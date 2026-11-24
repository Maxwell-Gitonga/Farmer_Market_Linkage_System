"""
Demand Forecasting (Time-Series Analysis) – Machine Learning and Data Analysis Module.

Weekly demand per crop is the total quantity (kg) bought through the platform
(transactions). The forecaster picks the richest model the data supports:

  >= 104 weeks  Holt-Winters exponential smoothing (additive damped trend + 52-week seasonality)
  >=  12 weeks  Holt's linear trend (damped)
  >=   4 weeks  moving average
  <    4 weeks  insufficient data

Among the eligible methods, the one with the lowest error on the most recent 12 weeks
(validation window) is used for the forecast (simple, data-driven model selection).

Trend classification (drives the activity-diagram decision "is the demand trend rising?"):
compare the mean of the next 4 forecast weeks with the mean of the last 4 observed weeks:
> +5 % RISING, < -5 % FALLING, otherwise STABLE.

Evaluation: rolling-origin back-test over the last year (13 forecast origins, 4-week
horizon) compared against naive (last week) and seasonal-naive (same week last year)
baselines. The error measure is WAPE (weighted absolute percentage error): total absolute
error over the 4 forecast weeks divided by total actual demand in those weeks. Plain MAPE
divides by each week separately, add so one week with almost no recorded sales (e.g. 40 kg
against a normal 9,000 kg) turns into an error of thousands of percent; WAPE does not.
Weeks whose recorded demand is far below the crop's normal level (less than a quarter of
the typical week) are treated as missing data, not as real demand, and left out of the score.
(The result keys keep the historical names "mape", "naive_mape" … for API compatibility;
"metric" says which measure they hold.)
"""
from __future__ import annotations

import warnings
from datetime import date, timedelta

import numpy as np
import pandas as pd
from statsmodels.tsa.holtwinters import ExponentialSmoothing

RISING, FALLING, STABLE = "RISING", "FALLING", "STABLE"
TREND_THRESHOLD = 0.05
METRIC = "WAPE"
MIN_WINDOW_SHARE = 0.25   # a week below 25% of the typical weekly demand is treated as missing data


def wape(forecast: np.ndarray, actual: np.ndarray) -> float:
    """Weighted absolute percentage error: sum|forecast - actual| / sum(actual)."""
    total = float(np.sum(actual))
    return float(np.sum(np.abs(np.asarray(forecast) - np.asarray(actual))) / total) if total > 0 else float("nan")


def typical_week(s: pd.Series) -> float:
    """Median of the weeks that had sales: the crop's normal weekly demand."""
    nz = s[s > 0]
    return float(nz.median()) if len(nz) else 0.0


def weekly_series(transactions: pd.DataFrame, crop: str, reference: date | None = None) -> pd.Series:
    """Aggregate transactions to weekly demand (weeks start Monday), up to the last complete week."""
    df = transactions.rename(columns={"crop_type": "crop", "transaction_date": "date"})
    df = df[df["crop"].str.lower() == crop.lower()] if "crop" in df.columns else df
    if df.empty:
        return pd.Series(dtype=float)
    d = pd.to_datetime(df["date"]).dt.normalize()
    q = pd.to_numeric(df["quantity"], errors="coerce").fillna(0)
    week = d - pd.to_timedelta(d.dt.weekday, unit="D")
    s = q.groupby(week).sum().sort_index()
    ref = pd.Timestamp(reference or date.today())
    last_complete = ref - pd.Timedelta(days=ref.weekday()) - pd.Timedelta(days=7)
    idx = pd.date_range(s.index.min(), max(last_complete, s.index.min()), freq="7D")
    s = s.reindex(idx, fill_value=0.0)
    s = s[s.index <= last_complete]
    # Drop leading zeros
    nz = np.flatnonzero(s.to_numpy() > 0)
    return s.iloc[nz[0]:] if len(nz) else pd.Series(dtype=float)


METHODS = ["holt_winters_seasonal", "holt_linear_trend", "moving_average"]


def _fit_forecast(s: pd.Series, horizon: int, method: str | None = None) -> tuple[np.ndarray, str]:
    n = len(s)
    y = s.to_numpy(dtype=float)
    if method is None:
        method = "holt_winters_seasonal" if n >= 104 else "holt_linear_trend" if n >= 12 else "moving_average"
    if method == "holt_winters_seasonal" and n < 104:
        method = "holt_linear_trend"
    if method == "holt_linear_trend" and n < 12:
        method = "moving_average"
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        if method == "holt_winters_seasonal":
            m = ExponentialSmoothing(y, trend="add", damped_trend=True, seasonal="add",
                                     seasonal_periods=52, initialization_method="estimated").fit()
            return np.maximum(m.forecast(horizon), 0), method
        if method == "holt_linear_trend":
            m = ExponentialSmoothing(y, trend="add", damped_trend=True, initialization_method="estimated").fit()
            return np.maximum(m.forecast(horizon), 0), method
    return np.repeat(y[-4:].mean(), horizon), "moving_average"


def select_method(s: pd.Series, val: int = 12) -> str:
    """Model selection: back-test each eligible method on the last `val` weeks of `s`."""
    if len(s) < val + 12:
        return "moving_average" if len(s) < 12 else "holt_linear_trend"
    train, test = s.iloc[:-val], s.iloc[-val:].to_numpy()
    best, best_err = None, float("inf")
    for m in METHODS:
        fc, used = _fit_forecast(train, val, m)
        if used != m:
            continue
        err = wape(fc, test)
        if np.isnan(err):
            err = 0.0
        if err < best_err:
            best, best_err = m, err
    return best or "moving_average"


def forecast(transactions: pd.DataFrame, crop: str, horizon: int = 4, reference: date | None = None) -> dict:
    s = weekly_series(transactions, crop, reference)
    base = {"crop_type": crop, "horizon_weeks": horizon, "based_on_weeks": int(len(s))}
    if len(s) < 4:
        return {**base, "sufficient_data": False, "trend": STABLE, "model": "none", "forecast": [],
                "history": [{"week_start": d.date().isoformat(), "quantity": round(float(v), 1)} for d, v in s.items()],
                "recent_weekly_avg": float(s.mean()) if len(s) else 0.0, "forecast_weekly_avg": None,
                "change_pct": 0.0,
                "message": f"Limited demand history for {crop}; demand trend cannot be forecast yet."}
    fc, model = _fit_forecast(s, horizon, select_method(s))
    recent = float(s.iloc[-4:].mean())
    future = float(np.mean(fc))
    change = (future - recent) / recent if recent > 0 else 0.0
    trend = RISING if change > TREND_THRESHOLD else FALLING if change < -TREND_THRESHOLD else STABLE
    start = s.index[-1] + pd.Timedelta(days=7)
    weeks = [start + pd.Timedelta(days=7 * i) for i in range(horizon)]
    hist = s.iloc[-26:]
    return {**base, "sufficient_data": True, "trend": trend, "model": model,
            "recent_weekly_avg": round(recent, 1), "forecast_weekly_avg": round(future, 1),
            "change_pct": round(100 * change, 1),
            "forecast_period": f"{weeks[0].date().isoformat()} to {(weeks[-1] + pd.Timedelta(days=6)).date().isoformat()}",
            "forecast": [{"week_start": w.date().isoformat(), "quantity": round(float(v), 1)} for w, v in zip(weeks, fc)],
            "history": [{"week_start": d.date().isoformat(), "quantity": round(float(v), 1)} for d, v in hist.items()],
            "message": None}


def evaluate(transactions: pd.DataFrame, crops: list[str], horizon: int = 4, folds: int = 13,
             reference: date | None = None) -> dict:
    """Rolling-origin back-test over the last year.

    For each of `folds` forecast origins (every 4 weeks), the model is selected and fitted on
    the data before the origin only, then forecasts the next `horizon` weeks. Errors are
    compared with naive (last observed week) and seasonal-naive (same weeks last year).
    """
    per_crop, all_m, all_naive, all_snaive = {}, [], [], []
    for crop in crops:
        s = weekly_series(transactions, crop, reference)
        if len(s) < 52 + folds * 4 + horizon:
            continue
        errs, e_naive, e_snaive, used_models = [], [], [], []
        skipped = 0
        for f in range(folds):
            origin = len(s) - horizon - 4 * (folds - 1 - f)
            train, test = s.iloc[:origin], s.iloc[origin:origin + horizon].to_numpy()
            # Data-quality check: a week with far less recorded demand than normal means the sales
            # records are missing (e.g. the demo data stops), not that buyers stopped buying.
            # Such weeks are left out of the score; a window with fewer than 2 good weeks is skipped.
            ok = test >= MIN_WINDOW_SHARE * typical_week(train)
            if ok.sum() < 2:
                skipped += 1
                continue
            fc, model = _fit_forecast(train, horizon, select_method(train))
            naive = np.repeat(train.iloc[-1], horizon)
            snaive = s.iloc[origin - 52:origin - 52 + horizon].to_numpy()
            errs.append(wape(fc[ok], test[ok]))
            e_naive.append(wape(naive[ok], test[ok]))
            e_snaive.append(wape(snaive[ok], test[ok]))
            used_models.append(model)
        if not errs:
            continue
        per_crop[crop] = {"mape": round(float(np.mean(errs)), 4), "naive_mape": round(float(np.mean(e_naive)), 4),
                          "seasonal_naive_mape": round(float(np.mean(e_snaive)), 4), "weeks": int(len(s)),
                          "folds_used": len(errs), "folds_skipped": skipped,
                          "models_selected": {m: used_models.count(m) for m in set(used_models)}}
        all_m += errs
        all_naive += e_naive
        all_snaive += e_snaive
    if not all_m:
        return {"per_crop": {}, "mean_mape": None, "metric": METRIC}
    return {"method": f"rolling-origin back-test, {folds} origins, {horizon}-week horizon, WAPE",
            "metric": METRIC,
            "per_crop": per_crop, "mean_mape": round(float(np.mean(all_m)), 4),
            "mean_naive_mape": round(float(np.mean(all_naive)), 4),
            "mean_seasonal_naive_mape": round(float(np.mean(all_snaive)), 4),
            "improvement_over_naive_pct": round(float(100 * (1 - np.mean(all_m) / np.mean(all_naive))), 1)}
