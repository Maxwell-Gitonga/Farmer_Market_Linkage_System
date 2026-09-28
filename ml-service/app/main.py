"""
Machine Learning Module (Python) – REST API.

Called by the Spring Boot backend (System Architecture, Figure 3.1: "Send Data for
Prediction" / "Prediction Results & Insights"). Endpoints:

  GET  /health                   service + model status
  GET  /meta                     supported crops and county markets
  GET  /metrics                  evaluation metrics of the current models
  POST /train                    retrain on data supplied by the backend (or the bundled dataset)
  POST /predict/price            regression price prediction            (PricePredictionModule)
  POST /forecast/demand          time-series demand forecast
  POST /recommend/price          full decision support for a listing (prediction + demand + advice)
  POST /recommend/markets        best county market net of transport cost
  POST /recommend/matches        buyer <-> product matching             (RecommendationModule)

Run:  uvicorn app.main:app --port 8000
"""
from __future__ import annotations

import logging
import threading
from contextlib import asynccontextmanager
from datetime import date, timedelta
from typing import Literal

import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from . import demand_model, recommender
from .reference import COUNTIES, CROPS, markets_for, normalise_county, normalise_crop
from .store import STORE

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("ml.api")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    STORE.startup()
    yield


app = FastAPI(title="Farmer Market Linkage – ML & Data Analysis Module", version="1.0.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


# ------------------------------------------------------------------------------ schemas
class PriceRecord(BaseModel):
    recorded_date: date
    price: float
    market_location: str
    crop_type: str | None = None


class TransactionRecord(BaseModel):
    transaction_date: date
    quantity: float
    crop_type: str | None = None


class PredictPriceRequest(BaseModel):
    crop_type: str
    county: str
    target_date: date | None = None
    reference_date: date | None = None
    history: list[PriceRecord] | None = Field(None, description="Market-price history for this crop (all markets)")


class DemandRequest(BaseModel):
    crop_type: str
    horizon_weeks: int = Field(4, ge=1, le=12)
    reference_date: date | None = None
    transactions: list[TransactionRecord] | None = None


class RecommendPriceRequest(BaseModel):
    crop_type: str
    county: str
    harvest_date: date | None = None
    quantity: float | None = None
    reference_date: date | None = None
    price_history: list[PriceRecord] | None = None
    transactions: list[TransactionRecord] | None = None


class BestMarketsRequest(BaseModel):
    crop_type: str
    farmer_county: str
    target_date: date | None = None
    quantity: float | None = None
    reference_date: date | None = None
    history: list[PriceRecord] | None = None


class ProductIn(BaseModel):
    id: int
    crop_type: str
    location: str | None = None
    price_per_unit: float | None = None
    quantity: float | None = None
    harvest_date: date | None = None
    farmer_id: int | None = None


class BuyerIn(BaseModel):
    id: int
    name: str | None = None
    location: str | None = None
    preferences: str | None = None


class MatchRequest(BaseModel):
    mode: Literal["buyers_for_product", "products_for_buyer"]
    products: list[ProductIn]
    buyers: list[BuyerIn]
    market_prices: dict[str, float] = Field(default_factory=dict, description='"Crop|County" -> latest price')
    purchase_history: dict[str, int] = Field(default_factory=dict, description='"buyerId|Crop" -> count')
    top_k: int = 10
    min_score: float = 0.0


class TrainRequest(BaseModel):
    market_prices: list[PriceRecord] | None = None
    transactions: list[TransactionRecord] | None = None
    background: bool = False


# ------------------------------------------------------------------------------ helpers
def _crop(name: str) -> str:
    return normalise_crop(name) or name.strip().title()


def _county(name: str) -> str:
    return normalise_county(name) or name.strip().title()


def _history_df(crop: str, records: list[PriceRecord] | None) -> pd.DataFrame:
    if records is not None:
        rows = [{"crop_type": crop, "market_location": _county(r.market_location), "recorded_date": r.recorded_date,
                 "price": r.price} for r in records if (r.crop_type is None or _crop(r.crop_type) == crop)]
        return pd.DataFrame(rows, columns=["crop_type", "market_location", "recorded_date", "price"])
    df = STORE.prices
    return df[df["crop_type"] == crop] if len(df) else df


def _tx_df(crop: str, records: list[TransactionRecord] | None) -> pd.DataFrame:
    if records is not None:
        rows = [{"crop_type": crop, "transaction_date": r.transaction_date, "quantity": r.quantity}
                for r in records if (r.crop_type is None or _crop(r.crop_type) == crop)]
        return pd.DataFrame(rows, columns=["crop_type", "transaction_date", "quantity"])
    df = STORE.transactions
    return df[df["crop_type"] == crop] if len(df) else df


def _require_model():
    if STORE.price is None:
        raise HTTPException(503, "Models are still training – try again shortly")


def _price_trend(hist: pd.DataFrame, county: str, reference: date, days: int = 30) -> dict:
    if hist is None or not len(hist):
        return {"basis": "none", "points": []}
    h = hist.copy()
    h["recorded_date"] = pd.to_datetime(h["recorded_date"])
    h = h[(h["recorded_date"] <= pd.Timestamp(reference)) &
          (h["recorded_date"] > pd.Timestamp(reference) - pd.Timedelta(days=days))]
    c = h[h["market_location"] == county]
    basis = "county"
    if len(c) < 4:
        c, basis = h, "national"
    pts = c.groupby("recorded_date")["price"].mean().sort_index()
    return {"basis": basis, "points": [{"date": d.date().isoformat(), "price": round(float(p), 2)} for d, p in pts.items()]}


# ------------------------------------------------------------------------------ endpoints
@app.get("/health")
def health():
    return {"status": "UP" if STORE.price else "TRAINING", "training": STORE.training,
            "price_model": STORE.price.version if STORE.price else None,
            "data_source": STORE.data_source, "last_training": STORE.last_training}


@app.get("/meta")
def meta():
    return {"crops": [{"name": c, "emoji": m["emoji"], "shelf_life_days": m["shelf"], "unit": "kg",
                       "markets": markets_for(c)} for c, m in CROPS.items()],
            "counties": [{"name": c, "lat": lat, "lon": lon} for c, (lat, lon) in COUNTIES.items()]}


@app.get("/metrics")
def metrics():
    return STORE.metrics()


@app.post("/train")
def train(req: TrainRequest | None = None):
    req = req or TrainRequest()
    if STORE.training:
        raise HTTPException(409, "Training already in progress")
    if req.market_prices:
        prices = pd.DataFrame([{"crop_type": _crop(r.crop_type or ""), "market_location": _county(r.market_location),
                                "recorded_date": str(r.recorded_date), "price": r.price} for r in req.market_prices])
        txns = pd.DataFrame([{"crop_type": _crop(r.crop_type or ""), "transaction_date": str(r.transaction_date),
                              "quantity": r.quantity} for r in (req.transactions or [])],
                            columns=["crop_type", "transaction_date", "quantity"])
        source = "backend_database"
    else:
        prices, txns = STORE.load_bundled_data()
        source = "bundled_simulated_dataset"
    if req.background:
        threading.Thread(target=STORE.train, args=(prices, txns, source), daemon=True).start()
        return {"status": "STARTED", "price_records": len(prices), "transactions": len(txns)}
    return {"status": "COMPLETED", **STORE.train(prices, txns, source)}


@app.post("/predict/price")
def predict_price(req: PredictPriceRequest):
    _require_model()
    crop, county = _crop(req.crop_type), _county(req.county)
    ref = req.reference_date or date.today()
    target = req.target_date or ref
    return STORE.price.predict(_history_df(crop, req.history), crop, county, target, ref)


@app.post("/forecast/demand")
def forecast_demand(req: DemandRequest):
    crop = _crop(req.crop_type)
    return demand_model.forecast(_tx_df(crop, req.transactions), crop, req.horizon_weeks, req.reference_date)


@app.post("/recommend/price")
def recommend_price(req: RecommendPriceRequest):
    """Activity diagram: predict price -> forecast demand -> adjust -> advise (accept or set own price)."""
    _require_model()
    crop, county = _crop(req.crop_type), _county(req.county)
    ref = req.reference_date or date.today()
    harvest = req.harvest_date or ref
    hist = _history_df(crop, req.price_history)
    txns = _tx_df(crop, req.transactions)

    prediction = STORE.price.predict(hist, crop, county, harvest, ref)
    demand = demand_model.forecast(txns, crop, 4, ref)
    window = []
    if prediction.get("sufficient_data"):
        for d in recommender.sell_window_dates(crop, harvest, ref):
            p = STORE.price.predict(hist, crop, county, d, ref)
            if p.get("sufficient_data"):
                window.append({"date": d.isoformat(), "price": p["predicted_price"]})
    typical = float(txns["quantity"].median()) if len(txns) else None
    rec = recommender.recommend_price(prediction, demand, req.quantity, typical, window)
    demand_brief = {k: v for k, v in demand.items() if k != "history"}
    return {"crop_type": crop, "county": county, "price_prediction": prediction, "demand_forecast": demand_brief,
            "recommendation": rec, "sell_window": window, "price_trend": _price_trend(hist, county, ref),
            "model_version": STORE.price.version}


@app.post("/recommend/markets")
def recommend_markets(req: BestMarketsRequest):
    _require_model()
    crop, county = _crop(req.crop_type), _county(req.farmer_county)
    ref = req.reference_date or date.today()
    target = req.target_date or ref
    hist = _history_df(crop, req.history)
    rows = recommender.best_markets(crop, county, lambda c: STORE.price.predict(hist, crop, c, target, ref),
                                    target, req.quantity)
    return {"crop_type": crop, "farmer_county": county, "target_date": target.isoformat(), "markets": rows}


@app.post("/recommend/matches")
def recommend_matches(req: MatchRequest):
    """RecommendationModule: matchBuyerToProducts / matched buyers for a listing, ranked (rankMatches)."""
    results = []
    for p in req.products:
        crop = _crop(p.crop_type)
        pd_ = {"crop_type": crop, "location": _county(p.location or ""), "price_per_unit": p.price_per_unit,
               "harvest_date": p.harvest_date}
        for b in req.buyers:
            loc = _county(b.location or "")
            mp = req.market_prices.get(f"{crop}|{loc}") or req.market_prices.get(f"{crop}|*")
            hc = req.purchase_history.get(f"{b.id}|{crop}", 0)
            s = recommender.match_score(pd_, {"location": loc, "preferences": b.preferences}, mp, hc)
            if s["score"] >= req.min_score:
                results.append({"product_id": p.id, "buyer_id": b.id, "score": s["score"], "reasons": s["reasons"],
                                "components": s["components"], "distance_km": s["distance_km"]})
    key = "product_id" if req.mode == "buyers_for_product" else "buyer_id"
    ranked: dict[int, list] = {}
    for r in sorted(results, key=lambda r: r["score"], reverse=True):
        lst = ranked.setdefault(r[key], [])
        if len(lst) < req.top_k:
            lst.append(r)
    out = [r for lst in ranked.values() for r in lst]
    out.sort(key=lambda r: r["score"], reverse=True)
    return {"mode": req.mode, "matches": out}
