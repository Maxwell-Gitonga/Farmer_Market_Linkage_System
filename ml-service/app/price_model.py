"""
Price Prediction (Regression) – Machine Learning and Data Analysis Module (Proposal §3.8.4).

A direct multi-horizon regression model: given the market-price history of a crop up to
today, predict the price at a target date (e.g. the farmer's harvest date).

Target:   log(price_at_target / recent_4_obs_mean)   – scale-free, so one model serves
          every crop and county.
Features: forecast horizon, recent momentum and volatility, level vs long-run mean,
          county-vs-national ratio, learned seasonal profile per crop, target month
          (sin/cos), Kenyan season (external factor), crop and county (one-hot).

Three regressors are trained and compared on a time-based hold-out (last 6 months):
Linear Regression, Random Forest and Gradient Boosting. The best one (lowest MAPE) is
refitted on all data and persisted with joblib. A naive "last price" baseline is also
reported so the benefit of the model is measurable (Proposal §3.2.6 – validation of the
accuracy of the pricing model).
"""
from __future__ import annotations

import math
import time
from dataclasses import dataclass, field
from datetime import date, timedelta

import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor, RandomForestRegressor
from sklearn.linear_model import Ridge
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from .reference import COUNTIES, CROPS, SEASONS, season_of

NATIONAL = "National"
CAT_CROPS = list(CROPS)
CAT_COUNTIES = list(COUNTIES) + [NATIONAL]
OFFSETS = np.array([1, 2, 4, 6, 9, 13, 17])       # market days between cutoff and target
MIN_RECENT = 6                                     # records in the last 60 days to count as "sufficient"
RECENT_DAYS = 60
MODEL_VERSION_PREFIX = "price-reg"

NUM_FEATURES = ["horizon_days", "lvl_long", "mom4", "vol8", "ratio_nat", "lag_dev", "season_delta",
                "tm_sin", "tm_cos", "log_base", "recent_cov"]


def _feature_names() -> list[str]:
    return (NUM_FEATURES + [f"crop={c}" for c in CAT_CROPS] + [f"county={c}" for c in CAT_COUNTIES]
            + [f"season={s}" for s in SEASONS])


FEATURES = _feature_names()


# --------------------------------------------------------------------------------------
# Data preparation
# --------------------------------------------------------------------------------------
def prepare_prices(df: pd.DataFrame) -> pd.DataFrame:
    """Normalise a market-price frame to columns crop, county, date, price (known crops only)."""
    df = df.rename(columns={"crop_type": "crop", "market_location": "county", "recorded_date": "date"})
    df = df[["crop", "county", "date", "price"]].copy()
    df["date"] = pd.to_datetime(df["date"]).dt.normalize()
    df["price"] = pd.to_numeric(df["price"], errors="coerce")
    df = df.dropna()
    df = df[df["price"] > 0]
    # Several observations for the same crop/county/day (e.g. platform sales) -> average
    df = df.groupby(["crop", "county", "date"], as_index=False)["price"].mean()
    return df.sort_values(["crop", "county", "date"]).reset_index(drop=True)


def with_national(df: pd.DataFrame) -> pd.DataFrame:
    nat = df.groupby(["crop", "date"], as_index=False)["price"].mean()
    nat["county"] = NATIONAL
    return pd.concat([df, nat[["crop", "county", "date", "price"]]], ignore_index=True).sort_values(
        ["crop", "county", "date"]).reset_index(drop=True)


def seasonal_index(df: pd.DataFrame) -> dict[str, dict[int, float]]:
    """Average log deviation from the one-year centred mean, by crop and month."""
    out: dict[str, dict[int, float]] = {}
    nat = df[df["county"] == NATIONAL] if NATIONAL in set(df["county"]) else with_national(df).query("county == @NATIONAL")
    for crop, g in nat.groupby("crop"):
        g = g.sort_values("date")
        lp = np.log(g["price"].to_numpy())
        centred = pd.Series(lp).rolling(104, center=True, min_periods=40).mean().to_numpy()
        dev = lp - centred
        months = g["date"].dt.month.to_numpy()
        prof = {}
        for m in range(1, 13):
            v = dev[(months == m) & ~np.isnan(dev)]
            prof[m] = float(v.mean()) if len(v) else 0.0
        out[crop] = prof
    return out


def _rolling_block(g: pd.DataFrame, nat_mean4: pd.Series | None) -> pd.DataFrame:
    """Per-series rolling statistics evaluated at each observation (cutoff candidates)."""
    s = g["price"].reset_index(drop=True)
    d = g["date"].reset_index(drop=True)
    out = pd.DataFrame({"date": d, "price": s})
    out["mean4"] = s.rolling(4, min_periods=2).mean()
    out["mean8"] = s.rolling(8, min_periods=3).mean()
    out["mean26"] = s.rolling(26, min_periods=8).mean()
    out["std8"] = s.rolling(8, min_periods=3).std()
    out["lag4"] = s.shift(4)
    ts = pd.Series(1.0, index=pd.DatetimeIndex(d))
    out["recent_n"] = ts.rolling(f"{RECENT_DAYS}D").sum().to_numpy()
    if nat_mean4 is not None:
        out["nat_mean4"] = nat_mean4.reindex(pd.DatetimeIndex(d), method="ffill").to_numpy()
    else:
        out["nat_mean4"] = out["mean4"]
    return out


def _feature_rows(block: pd.DataFrame, k_idx: np.ndarray, target_dates: pd.Series, crop: str, county: str,
                  sidx: dict[str, dict[int, float]], crop_log_base: dict[str, float]) -> np.ndarray:
    b = block.iloc[k_idx]
    cutoff_dates = pd.to_datetime(b["date"].to_numpy())
    tdates = pd.to_datetime(np.asarray(target_dates))
    horizon = (tdates - cutoff_dates).days.to_numpy().astype(float)
    mean4 = b["mean4"].to_numpy()
    mean26 = b["mean26"].to_numpy()
    lvl_long = np.log(mean4 / mean26)
    mom4 = np.log(b["price"].to_numpy() / b["lag4"].to_numpy())
    vol8 = b["std8"].to_numpy() / b["mean8"].to_numpy()
    ratio_nat = np.log(mean4 / b["nat_mean4"].to_numpy())
    lag_dev = np.log(b["price"].to_numpy() / mean4)
    prof = sidx.get(crop, {})
    tm = tdates.month.to_numpy()
    cm = cutoff_dates.month.to_numpy()
    season_delta = np.array([prof.get(int(a), 0.0) - prof.get(int(c), 0.0) for a, c in zip(tm, cm)])
    tm_frac = (tm - 1 + (tdates.day.to_numpy() - 1) / 30.0) / 12.0
    recent_cov = np.minimum(b["recent_n"].to_numpy() / 17.0, 1.0)

    n = len(k_idx)
    X = np.zeros((n, len(FEATURES)))
    cols = {f: i for i, f in enumerate(FEATURES)}
    X[:, cols["horizon_days"]] = horizon
    X[:, cols["lvl_long"]] = np.nan_to_num(lvl_long)
    X[:, cols["mom4"]] = np.nan_to_num(mom4)
    X[:, cols["vol8"]] = np.nan_to_num(vol8)
    X[:, cols["ratio_nat"]] = np.nan_to_num(ratio_nat)
    X[:, cols["lag_dev"]] = np.nan_to_num(lag_dev)
    X[:, cols["season_delta"]] = season_delta
    X[:, cols["tm_sin"]] = np.sin(2 * math.pi * tm_frac)
    X[:, cols["tm_cos"]] = np.cos(2 * math.pi * tm_frac)
    X[:, cols["log_base"]] = crop_log_base.get(crop, 4.0)
    X[:, cols["recent_cov"]] = recent_cov
    if f"crop={crop}" in cols:
        X[:, cols[f"crop={crop}"]] = 1.0
    if f"county={county}" in cols:
        X[:, cols[f"county={county}"]] = 1.0
    for i, m in enumerate(tm):
        X[i, cols[f"season={season_of(int(m))}"]] = 1.0
    return X


def build_training_set(df: pd.DataFrame, sidx, crop_log_base, seed: int = 7):
    """Create (X, y, meta) samples: cutoff k -> target j = k + offset, 3 offsets per target."""
    rng = np.random.default_rng(seed)
    Xs, ys, metas = [], [], []
    for crop, gc in df.groupby("crop"):
        nat = gc[gc["county"] == NATIONAL].sort_values("date")
        nat_mean4 = pd.Series(nat["price"].rolling(4, min_periods=2).mean().to_numpy(),
                              index=pd.DatetimeIndex(nat["date"]))
        for county, g in gc.groupby("county"):
            g = g.sort_values("date").reset_index(drop=True)
            if len(g) < 30:
                continue
            block = _rolling_block(g, nat_mean4 if county != NATIONAL else None)
            n = len(block)
            js = np.repeat(np.arange(n), 3)
            offs = rng.choice(OFFSETS, size=len(js))
            ks = js - offs
            ok = ks >= 26
            js, ks = js[ok], ks[ok]
            X = _feature_rows(block, ks, block["date"].iloc[js].reset_index(drop=True), crop, county, sidx, crop_log_base)
            y = np.log(block["price"].to_numpy()[js] / block["mean4"].to_numpy()[ks])
            Xs.append(X)
            ys.append(y)
            metas.append(pd.DataFrame({"crop": crop, "county": county,
                                       "target_date": block["date"].to_numpy()[js],
                                       "cutoff_date": block["date"].to_numpy()[ks],
                                       "mean4": block["mean4"].to_numpy()[ks],
                                       "last_price": block["price"].to_numpy()[ks],
                                       "actual": block["price"].to_numpy()[js]}))
    X = np.vstack(Xs)
    y = np.concatenate(ys)
    meta = pd.concat(metas, ignore_index=True)
    good = np.isfinite(y) & np.all(np.isfinite(X), axis=1)
    return X[good], y[good], meta[good].reset_index(drop=True)


# --------------------------------------------------------------------------------------
# Model
# --------------------------------------------------------------------------------------
def _candidates():
    return {
        "linear_regression": make_pipeline(StandardScaler(), Ridge(alpha=1.0)),
        "random_forest": RandomForestRegressor(n_estimators=120, min_samples_leaf=4, max_features=0.5,
                                               n_jobs=-1, random_state=42),
        "gradient_boosting": HistGradientBoostingRegressor(max_iter=400, learning_rate=0.05, max_leaf_nodes=31,
                                                           l2_regularization=1.0, random_state=42),
    }


def _metrics(actual: np.ndarray, pred: np.ndarray) -> dict:
    err = pred - actual
    ss_res = float(np.sum(err ** 2))
    ss_tot = float(np.sum((actual - actual.mean()) ** 2))
    return {"mae": round(float(np.mean(np.abs(err))), 3),
            "rmse": round(float(np.sqrt(np.mean(err ** 2))), 3),
            "mape": round(float(np.mean(np.abs(err) / actual)), 4),
            "r2": round(1 - ss_res / ss_tot, 4) if ss_tot > 0 else None,
            "n": int(len(actual))}


@dataclass
class PriceModel:
    estimator: object = None
    algorithm: str = ""
    version: str = ""
    trained_at: str = ""
    seasonal: dict = field(default_factory=dict)
    crop_log_base: dict = field(default_factory=dict)
    crop_mape: dict = field(default_factory=dict)
    crop_resid_q: dict = field(default_factory=dict)       # crop -> (q10, q90) in log space
    county_premium: dict = field(default_factory=dict)      # (crop|*, county) -> ratio to national
    metrics: dict = field(default_factory=dict)
    data_end: str = ""

    # ------------------------------------------------------------------ training
    @classmethod
    def train(cls, prices: pd.DataFrame, holdout_days: int = 180, log=print) -> "PriceModel":
        t0 = time.time()
        df = with_national(prepare_prices(prices))
        end = df["date"].max()
        split = end - pd.Timedelta(days=holdout_days)
        crop_log_base = {c: float(np.log(g["price"].mean())) for c, g in df.groupby("crop")}

        # --- evaluation run: statistics from the training period only (no leakage)
        train_df = df[df["date"] <= split]
        sidx_train = seasonal_index(train_df)
        X, y, meta = build_training_set(df, sidx_train, crop_log_base)
        is_test = (meta["target_date"] > split) & (meta["cutoff_date"] > split - pd.Timedelta(days=1))
        is_train = meta["target_date"] <= split
        results = {}
        preds_by_model = {}
        for name, est in _candidates().items():
            t = time.time()
            est.fit(X[is_train.to_numpy()], y[is_train.to_numpy()])
            p_log = est.predict(X[is_test.to_numpy()])
            pred = meta.loc[is_test, "mean4"].to_numpy() * np.exp(p_log)
            m = _metrics(meta.loc[is_test, "actual"].to_numpy(), pred)
            m["fit_seconds"] = round(time.time() - t, 1)
            results[name] = m
            preds_by_model[name] = (p_log, pred)
            log(f"  [price] {name:<18} MAPE={m['mape']:.4f} MAE={m['mae']:.2f} R2={m['r2']}")
        naive = _metrics(meta.loc[is_test, "actual"].to_numpy(), meta.loc[is_test, "last_price"].to_numpy())
        results["naive_last_price_baseline"] = naive
        log(f"  [price] naive baseline      MAPE={naive['mape']:.4f}")
        best = min((k for k in results if k != "naive_last_price_baseline"), key=lambda k: results[k]["mape"])

        # Per-crop accuracy and residual spread of the chosen model (drives confidence + intervals)
        test_meta = meta[is_test].copy()
        test_meta["pred"] = preds_by_model[best][1]
        test_meta["log_resid"] = np.log(test_meta["actual"] / test_meta["pred"])
        crop_mape, crop_q, per_crop = {}, {}, {}
        for crop, g in test_meta.groupby("crop"):
            mape = float(np.mean(np.abs(g["pred"] - g["actual"]) / g["actual"]))
            crop_mape[crop] = round(mape, 4)
            crop_q[crop] = (float(np.quantile(g["log_resid"], 0.10)), float(np.quantile(g["log_resid"], 0.90)))
            per_crop[crop] = {"mape": round(mape, 4),
                              "naive_mape": round(float(np.mean(np.abs(g["last_price"] - g["actual"]) / g["actual"])), 4),
                              "n": int(len(g))}

        # --- final fit on all data
        sidx = seasonal_index(df)
        X_all, y_all, _ = build_training_set(df, sidx, crop_log_base)
        final = _candidates()[best]
        final.fit(X_all, y_all)

        # County premium relative to national (crop-specific + generic "*")
        prem = {}
        piv = df.pivot_table(index=["crop", "date"], columns="county", values="price")
        for crop in piv.index.get_level_values(0).unique():
            sub = piv.loc[crop]
            for county in COUNTIES:
                if county in sub.columns:
                    r = (sub[county] / sub[NATIONAL]).dropna()
                    if len(r) > 10:
                        prem[f"{crop}|{county}"] = float(r.median())
        for county in COUNTIES:
            vals = [v for k, v in prem.items() if k.endswith(f"|{county}")]
            prem[f"*|{county}"] = float(np.median(vals)) if vals else 1.0

        model = cls(estimator=final, algorithm=best,
                    version=f"{MODEL_VERSION_PREFIX}-{best}-{pd.Timestamp.now('UTC').strftime('%Y%m%d%H%M')}",
                    trained_at=pd.Timestamp.now('UTC').isoformat(), seasonal=sidx, crop_log_base=crop_log_base,
                    crop_mape=crop_mape, crop_resid_q=crop_q, county_premium=prem, data_end=str(end.date()))
        model.metrics = {"selected_model": best, "holdout_days": holdout_days, "train_samples": int(is_train.sum()),
                         "test_samples": int(is_test.sum()), "final_training_samples": int(len(y_all)),
                         "comparison": results, "per_crop": per_crop,
                         "improvement_over_naive_pct": round(100 * (1 - results[best]["mape"] / naive["mape"]), 1),
                         "training_seconds": round(time.time() - t0, 1), "records_used": int(len(prices))}
        log(f"  [price] selected {best} in {model.metrics['training_seconds']}s")
        return model

    # ------------------------------------------------------------------ inference
    def predict(self, history: pd.DataFrame, crop: str, county: str, target: date,
                reference: date | None = None) -> dict:
        """Predict the price of `crop` in `county` at `target` from the supplied history."""
        reference = reference or date.today()
        if target < reference:
            target = reference
        known_crop = crop in CROPS
        h = prepare_prices(history.assign(crop_type=crop)) if len(history) else pd.DataFrame(
            columns=["crop", "county", "date", "price"])
        h = h[h["date"] <= pd.Timestamp(reference)]
        recent_cut = pd.Timestamp(reference) - pd.Timedelta(days=RECENT_DAYS)

        county_series = h[h["county"] == county]
        n_county_recent = int((county_series["date"] > recent_cut).sum())
        nat = h.groupby("date", as_index=False)["price"].mean().sort_values("date")
        n_nat_recent = int((nat["date"] > recent_cut).sum())

        base = {"crop_type": crop, "county": county, "target_date": target.isoformat(),
                "reference_date": reference.isoformat(), "model_version": self.version,
                "algorithm": self.algorithm, "based_on_records": int(len(h))}

        if not known_crop or (n_county_recent < MIN_RECENT and n_nat_recent < MIN_RECENT):
            return {**base, "sufficient_data": False, "predicted_price": None, "lower_bound": None,
                    "upper_bound": None, "confidence": 0.15 if len(h) else 0.0, "confidence_label": "low",
                    "basis": "none", "recent_records": n_county_recent,
                    "message": ("Limited data: there is not enough recent market data for "
                                f"{crop} to make a reliable prediction. Please set your own price "
                                "using local market knowledge.")}

        if n_county_recent >= MIN_RECENT:
            basis, series, series_county = "county", county_series.sort_values("date"), county
        else:
            basis, series, series_county = "national", nat, NATIONAL

        nat_mean4 = pd.Series(nat["price"].rolling(4, min_periods=2).mean().to_numpy(), index=pd.DatetimeIndex(nat["date"]))
        block = _rolling_block(series.reset_index(drop=True), nat_mean4 if basis == "county" else None)
        # Long-run mean needs history; fall back gracefully for short series
        block["mean26"] = block["mean26"].fillna(block["price"].expanding().mean())
        block["lag4"] = block["lag4"].fillna(block["price"].iloc[0])
        block["std8"] = block["std8"].fillna(0.0)
        block["mean8"] = block["mean8"].fillna(block["price"].expanding().mean())
        block["mean4"] = block["mean4"].fillna(block["price"])
        k = len(block) - 1
        X = _feature_rows(block, np.array([k]), pd.Series([pd.Timestamp(target)]), crop, series_county,
                          self.seasonal, self.crop_log_base)
        p_log = float(self.estimator.predict(X)[0])
        mean4 = float(block["mean4"].iloc[k])
        pred = mean4 * math.exp(p_log)
        if basis == "national":
            pred *= self.county_premium.get(f"{crop}|{county}", self.county_premium.get(f"*|{county}", 1.0))

        q10, q90 = self.crop_resid_q.get(crop, (-0.12, 0.12))
        horizon = (pd.Timestamp(target) - block["date"].iloc[k]).days
        widen = (1.25 if basis == "national" else 1.0) * (1 + horizon / 120)
        lower, upper = pred * math.exp(q10 * widen), pred * math.exp(q90 * widen)

        mape = self.crop_mape.get(crop, 0.12)
        acc = max(0.0, 1 - 2.2 * mape)
        coverage = min(1.0, (n_county_recent if basis == "county" else n_nat_recent) / 12)
        horizon_pen = 1 - min(0.35, horizon / 180)
        conf = acc * (0.6 + 0.4 * coverage) * horizon_pen * (0.85 if basis == "national" else 1.0)
        conf = round(max(0.05, min(0.99, conf)), 3)
        label = "high" if conf >= 0.75 else ("medium" if conf >= 0.5 else "low")
        msg = None
        if basis == "national":
            msg = (f"Few recent {crop} records for {county}; estimate derived from national prices "
                   "adjusted for this county.")
        return {**base, "sufficient_data": True, "predicted_price": round(pred, 2),
                "lower_bound": round(lower, 2), "upper_bound": round(upper, 2), "confidence": conf,
                "confidence_label": label, "basis": basis, "recent_records": n_county_recent,
                "last_observed_price": round(float(block['price'].iloc[k]), 2),
                "last_observed_date": block["date"].iloc[k].date().isoformat(),
                "horizon_days": int(horizon), "message": msg}
