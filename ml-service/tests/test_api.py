"""Functional + validation tests for the ML module (Proposal §3.6, §3.2.6 – validation of ML results)."""
from datetime import date, timedelta

import pandas as pd
import pytest
from fastapi.testclient import TestClient

from app import demand_model, recommender
from app.main import app

TODAY = date.today()


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:   # runs the lifespan -> loads (or trains) the models
        yield c


def test_health_and_meta(client):
    h = client.get("/health").json()
    assert h["status"] == "UP" and h["price_model"]
    meta = client.get("/meta").json()
    assert len(meta["crops"]) == 10 and any(c["name"] == "Maize" for c in meta["crops"])
    assert "Nairobi" in [c["name"] for c in meta["counties"]]


def test_metrics_show_model_beats_naive_baseline(client):
    m = client.get("/metrics").json()
    pm = m["price_model"]
    sel = pm["comparison"][pm["selected_model"]]
    assert sel["mape"] < pm["comparison"]["naive_last_price_baseline"]["mape"]
    assert sel["r2"] > 0.9
    assert m["demand_model"]["mean_mape"] < m["demand_model"]["mean_naive_mape"]


def test_price_prediction_sufficient_data(client):
    r = client.post("/predict/price", json={"crop_type": "maize", "county": "Nakuru",
                                             "target_date": str(TODAY + timedelta(days=14))}).json()
    assert r["sufficient_data"] is True and r["basis"] == "county"
    assert 15 < r["predicted_price"] < 150
    assert r["lower_bound"] <= r["predicted_price"] <= r["upper_bound"]
    assert r["confidence_label"] in ("high", "medium", "low") and 0 < r["confidence"] <= 1


def test_price_prediction_uses_supplied_history(client):
    hist = [{"recorded_date": str(TODAY - timedelta(days=3 * i)), "price": 100 + i % 3, "market_location": "Nakuru"}
            for i in range(40)]
    r = client.post("/predict/price", json={"crop_type": "Beans", "county": "Nakuru", "history": hist}).json()
    assert r["sufficient_data"] and 80 < r["predicted_price"] < 125   # anchored on the supplied level (~100)


def test_price_prediction_national_fallback(client):
    # Avocados are not traded in Mombasa in the dataset -> national estimate, lower confidence
    r = client.post("/predict/price", json={"crop_type": "Avocados", "county": "Mombasa"}).json()
    assert r["sufficient_data"] and r["basis"] == "national" and r["message"]


def test_price_prediction_insufficient_data_alt_path(client):
    # Sequence diagram alt [insufficient historical data] -> confidence low + "limited data"
    r = client.post("/predict/price", json={"crop_type": "Macadamia", "county": "Nakuru", "history": []}).json()
    assert r["sufficient_data"] is False and r["confidence_label"] == "low"
    assert r["predicted_price"] is None and "Limited data" in r["message"]


def _weekly_tx(values, end=None):
    end = end or TODAY
    monday = end - timedelta(days=end.weekday()) - timedelta(days=7)
    rows = []
    for i, v in enumerate(reversed(values)):
        rows.append({"transaction_date": str(monday - timedelta(days=7 * i) + timedelta(days=2)), "quantity": v})
    return rows


def test_demand_trend_rising_and_falling(client):
    rising = [100 + 12 * i for i in range(30)]
    r = client.post("/forecast/demand", json={"crop_type": "Onions", "transactions": _weekly_tx(rising)}).json()
    assert r["sufficient_data"] and r["trend"] == "RISING" and len(r["forecast"]) == 4
    falling = [500 - 12 * i for i in range(30)]
    r = client.post("/forecast/demand", json={"crop_type": "Onions", "transactions": _weekly_tx(falling)}).json()
    assert r["trend"] == "FALLING"
    flat = [300] * 30
    r = client.post("/forecast/demand", json={"crop_type": "Onions", "transactions": _weekly_tx(flat)}).json()
    assert r["trend"] == "STABLE"


def test_demand_insufficient(client):
    r = client.post("/forecast/demand", json={"crop_type": "Onions", "transactions": _weekly_tx([10, 20])}).json()
    assert r["sufficient_data"] is False


def test_recommend_price_full_flow(client):
    r = client.post("/recommend/price", json={"crop_type": "Irish Potatoes", "county": "Nyeri",
                                               "harvest_date": str(TODAY + timedelta(days=7)), "quantity": 800}).json()
    pp, rec = r["price_prediction"], r["recommendation"]
    assert pp["sufficient_data"] and rec["recommended_price"] > 0
    assert rec["decision_path"] in ("demand_rising", "demand_not_rising")
    assert r["demand_forecast"]["trend"] in ("RISING", "STABLE", "FALLING")
    assert len(r["price_trend"]["points"]) >= 6          # 30-day trend (twice-weekly markets)
    assert r["sell_window"] and rec["advice"]


def test_recommend_price_rising_demand_raises_price():
    pred = {"sufficient_data": True, "predicted_price": 100.0, "lower_bound": 80.0, "upper_bound": 120.0,
            "confidence_label": "high"}
    up = recommender.recommend_price(pred, {"sufficient_data": True, "trend": "RISING", "change_pct": 9}, 100, 500, [])
    down = recommender.recommend_price(pred, {"sufficient_data": True, "trend": "FALLING", "change_pct": -9}, 100, 500, [])
    assert up["recommended_price"] > 100 > down["recommended_price"]
    assert up["decision_path"] == "demand_rising"


def test_best_markets_ranked_by_net_price(client):
    r = client.post("/recommend/markets", json={"crop_type": "Maize", "farmer_county": "Uasin Gishu",
                                                 "quantity": 1000}).json()
    nets = [m["net_price_per_kg"] for m in r["markets"]]
    assert len(nets) == 10 and nets == sorted(nets, reverse=True) and r["markets"][0]["rank"] == 1


def test_matching_prefers_interested_nearby_buyer(client):
    body = {"mode": "buyers_for_product",
            "products": [{"id": 1, "crop_type": "Tomatoes", "location": "Kiambu", "price_per_unit": 80,
                          "harvest_date": str(TODAY)}],
            "buyers": [{"id": 10, "location": "Nairobi", "preferences": "Tomatoes,Onions"},
                       {"id": 11, "location": "Mombasa", "preferences": "Maize"},
                       {"id": 12, "location": "Kisumu", "preferences": "Tomatoes"}],
            "market_prices": {"Tomatoes|Nairobi": 100},
            "purchase_history": {"10|Tomatoes": 4}}
    m = client.post("/recommend/matches", json=body).json()["matches"]
    assert [x["buyer_id"] for x in m][0] == 10
    assert m[-1]["buyer_id"] == 11
    assert any("below local market" in r for r in m[0]["reasons"])


def test_weekly_series_aggregation():
    tx = pd.DataFrame(_weekly_tx([5, 7, 9]))
    tx["crop_type"] = "Maize"
    s = demand_model.weekly_series(tx, "Maize")
    assert list(s.to_numpy()) == [5, 7, 9]


def test_train_endpoint_with_backend_payload(client):
    # Backend-shaped payload: a subset of crops, two years – training must succeed and be reported
    p = pd.read_csv("data/market_prices.csv")
    p = p[p["crop_type"].isin(["Maize", "Beans"])]
    t = pd.read_csv("data/historical_transactions.csv")
    t = t[t["crop_type"].isin(["Maize", "Beans"])]
    body = {"market_prices": p[["crop_type", "market_location", "price", "recorded_date"]].to_dict("records"),
            "transactions": t[["crop_type", "quantity", "transaction_date"]].to_dict("records")}
    r = client.post("/train", json=body)
    assert r.status_code == 200, r.text
    res = r.json()
    assert res["status"] == "COMPLETED" and res["data_source"] == "backend_database"
    assert set(res["price_model"]["per_crop"]) == {"Maize", "Beans"}
    # restore full models for any later use
    assert client.post("/train", json={}).json()["status"] == "COMPLETED"
