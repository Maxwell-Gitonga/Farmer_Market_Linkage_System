"""Model store: training orchestration, persistence and in-memory reference data."""
from __future__ import annotations

import json
import logging
import os
import threading
import time
from datetime import date, timedelta

import joblib
import pandas as pd

from . import demand_model
from .price_model import PriceModel
from .reference import CROPS

log = logging.getLogger("ml.store")
BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE, "data")
MODEL_DIR = os.path.join(BASE, "models")
REPORT_DIR = os.path.join(BASE, "reports")
PRICE_MODEL_FILE = os.path.join(MODEL_DIR, "price_model.joblib")
META_FILE = os.path.join(MODEL_DIR, "metadata.json")


def shift_to_today(df: pd.DataFrame, col: str, today: date | None = None) -> pd.DataFrame:
    """Shift a simulated dataset by whole weeks so its latest record falls in the current week.

    Whole weeks keep market days (Tue/Fri) and Monday-based demand weeks aligned.
    """
    today = today or date.today()
    d = pd.to_datetime(df[col])
    gap = (pd.Timestamp(today) - d.max()).days
    weeks = gap // 7
    if weeks <= 0:
        return df
    out = df.copy()
    out[col] = (d + pd.Timedelta(days=7 * weeks)).dt.date.astype(str)
    return out


class ModelStore:
    def __init__(self):
        self.price: PriceModel | None = None
        self.demand_metrics: dict = {}
        self.prices = pd.DataFrame()
        self.transactions = pd.DataFrame()
        self.data_source = "none"
        self.lock = threading.Lock()
        self.training = False
        self.last_training: dict = {}

    # ------------------------------------------------------------------ data
    def load_bundled_data(self):
        p = pd.read_csv(os.path.join(DATA_DIR, "market_prices.csv"))
        t = pd.read_csv(os.path.join(DATA_DIR, "historical_transactions.csv"))
        return shift_to_today(p, "recorded_date"), shift_to_today(t, "transaction_date")

    # ------------------------------------------------------------------ lifecycle
    def startup(self):
        self.prices, self.transactions = self.load_bundled_data()
        self.data_source = "bundled_simulated_dataset"
        if os.path.exists(PRICE_MODEL_FILE):
            try:
                self.price = joblib.load(PRICE_MODEL_FILE)
                with open(META_FILE, encoding="utf-8") as fh:
                    meta = json.load(fh)
                self.demand_metrics = meta.get("demand_metrics", {})
                self.last_training = meta.get("last_training", {})
                log.info("Loaded price model %s", self.price.version)
                return
            except Exception as exc:  # e.g. scikit-learn version mismatch
                log.warning("Could not load saved model (%s) – retraining", exc)
        self.train(self.prices, self.transactions, source="bundled_simulated_dataset")

    def train(self, prices: pd.DataFrame, transactions: pd.DataFrame, source: str) -> dict:
        with self.lock:
            self.training = True
            try:
                t0 = time.time()
                log.info("Training on %d price records and %d transactions (%s)", len(prices), len(transactions), source)
                pm = PriceModel.train(prices, log=log.info)
                dm = demand_model.evaluate(transactions, list(CROPS)) if len(transactions) else {}
                self.price, self.demand_metrics = pm, dm
                self.prices, self.transactions, self.data_source = prices, transactions, source
                self.last_training = {"at": pd.Timestamp.now('UTC').isoformat(), "source": source,
                                      "price_records": int(len(prices)), "transactions": int(len(transactions)),
                                      "seconds": round(time.time() - t0, 1), "model_version": pm.version}
                self._persist()
                return self.metrics()
            finally:
                self.training = False

    def _persist(self):
        os.makedirs(MODEL_DIR, exist_ok=True)
        os.makedirs(REPORT_DIR, exist_ok=True)
        joblib.dump(self.price, PRICE_MODEL_FILE, compress=3)
        with open(META_FILE, "w", encoding="utf-8") as fh:
            json.dump({"demand_metrics": self.demand_metrics, "last_training": self.last_training}, fh, indent=2)
        m = self.metrics()
        with open(os.path.join(REPORT_DIR, "model_evaluation.json"), "w", encoding="utf-8") as fh:
            json.dump(m, fh, indent=2, default=str)
        with open(os.path.join(REPORT_DIR, "model_evaluation.md"), "w", encoding="utf-8") as fh:
            fh.write(self.report_markdown(m))

    # ------------------------------------------------------------------ reporting
    def metrics(self) -> dict:
        return {"price_model": {"version": self.price.version if self.price else None,
                                "algorithm": self.price.algorithm if self.price else None,
                                **(self.price.metrics if self.price else {})},
                "demand_model": self.demand_metrics, "last_training": self.last_training,
                "data_source": self.data_source}

    @staticmethod
    def report_markdown(m: dict) -> str:
        pm, dm = m["price_model"], m["demand_model"]
        lines = ["# Model Evaluation Report", "",
                 f"Generated: {m['last_training'].get('at', '')}  ",
                 f"Training data: {m['last_training'].get('price_records', 0)} market-price records, "
                 f"{m['last_training'].get('transactions', 0)} transactions ({m['data_source']})", "",
                 "## 1. Price prediction (regression)", "",
                 f"Hold-out: last {pm.get('holdout_days')} days (time-based split, no look-ahead). "
                 f"Train samples: {pm.get('train_samples')}, test samples: {pm.get('test_samples')}.", "",
                 "| Model | MAE (KES/kg) | RMSE | MAPE | R² |", "|---|---|---|---|---|"]
        for name, r in pm.get("comparison", {}).items():
            mark = " **(selected)**" if name == pm.get("selected_model") else ""
            lines.append(f"| {name}{mark} | {r['mae']} | {r['rmse']} | {100 * r['mape']:.2f}% | {r['r2']} |")
        lines += ["", f"Improvement of the selected model over the naive 'last price' baseline: "
                      f"**{pm.get('improvement_over_naive_pct')}%** lower MAPE.", "",
                  "| Crop | Model MAPE | Naive MAPE | Test samples |", "|---|---|---|---|"]
        for crop, r in pm.get("per_crop", {}).items():
            lines.append(f"| {crop} | {100 * r['mape']:.2f}% | {100 * r['naive_mape']:.2f}% | {r['n']} |")
        lines += ["", "## 2. Demand forecasting (time series)", "", f"Method: {dm.get('method', '')}", "",
                  f"Mean {dm.get('metric', 'MAPE')}: **{100 * (dm.get('mean_mape') or 0):.2f}%** vs naive "
                  f"{100 * (dm.get('mean_naive_mape') or 0):.2f}% and seasonal-naive "
                  f"{100 * (dm.get('mean_seasonal_naive_mape') or 0):.2f}% "
                  f"(improvement over naive: {dm.get('improvement_over_naive_pct')}%). "
                  "WAPE = total absolute error ÷ total actual demand over each 4-week window; weeks with "
                  "almost no recorded sales are treated as missing data.", "",
                  f"| Crop | Model {dm.get('metric', 'MAPE')} | Naive | Seasonal naive | Models selected across folds |", "|---|---|---|---|---|"]
        for crop, r in dm.get("per_crop", {}).items():
            sel = ", ".join(f"{k}×{v}" for k, v in r.get("models_selected", {}).items())
            lines.append(f"| {crop} | {100 * r['mape']:.2f}% | {100 * r['naive_mape']:.2f}% | "
                         f"{100 * r['seasonal_naive_mape']:.2f}% | {sel} |")
        lines += ["", "## 3. Recommendation engine", "",
                  "Rule-based decision support on top of the two models (see app/recommender.py): demand-adjusted "
                  "price recommendation, optimal selling date within shelf life, best-market ranking net of "
                  "transport cost, and weighted buyer–farmer matching (preference 0.35, proximity 0.25, "
                  "price 0.20, purchase history 0.15, freshness 0.05).", ""]
        return "\n".join(lines)


STORE = ModelStore()
