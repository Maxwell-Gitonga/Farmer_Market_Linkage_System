"""
Recommendation Engine – Machine Learning and Data Analysis Module.

Implements the "simple recommendation mechanisms" of the proposal (§1.1, §1.2, §2.5):

1. Price recommendation (activity diagram): combines the regression price prediction with
   the demand forecast.  Rising demand -> price slightly above the prediction; falling
   demand -> slightly below to move the produce; large lots -> small volume discount.
   Also finds the *optimal time to sell* within the crop's shelf life (price optimisation).
2. Best market recommendation: predicted price in every county market minus an estimated
   transport cost from the farmer's county -> ranked net farm-gate value.
3. Buyer-farmer matching: a transparent weighted score used both for "matched buyers"
   (farmer dashboard) and "recommended for you" (buyer dashboard):
       0.35 crop preference + 0.25 proximity + 0.20 price attractiveness
     + 0.15 purchase history + 0.05 freshness
"""
from __future__ import annotations

from datetime import date, timedelta

from .reference import CROPS, TRANSPORT_KES_PER_KG_KM, haversine_km, markets_for

WEIGHTS = {"preference": 0.35, "proximity": 0.25, "price": 0.20, "history": 0.15, "freshness": 0.05}
DEMAND_ADJ = {"RISING": 0.04, "STABLE": 0.0, "FALLING": -0.03}


def recommend_price(prediction: dict, demand: dict, quantity: float | None,
                    typical_lot: float | None, sell_window: list[dict]) -> dict:
    """Decision logic from the activity diagram."""
    if not prediction.get("sufficient_data"):
        return {"recommended_price": None, "adjustment_pct": 0.0, "decision_path": "insufficient_data",
                "advice": [prediction.get("message") or "Limited data – set your own price."],
                "best_sell_date": None, "best_sell_price": None}

    pred = prediction["predicted_price"]
    trend = demand.get("trend", "STABLE") if demand.get("sufficient_data") else "STABLE"
    adj = DEMAND_ADJ.get(trend, 0.0)
    advice = []
    if trend == "RISING":
        advice.append(f"Demand is rising ({demand.get('change_pct', 0):+.1f}% expected over the next 4 weeks) – "
                      "you can price slightly above the predicted market price.")
        path = "demand_rising"
    elif trend == "FALLING":
        advice.append(f"Demand is falling ({demand.get('change_pct', 0):+.1f}% expected) – pricing slightly below "
                      "the market will help you sell quickly and avoid post-harvest losses.")
        path = "demand_not_rising"
    else:
        advice.append("Demand is stable – price close to the predicted market price.")
        path = "demand_not_rising"

    if quantity and typical_lot and quantity > 2.5 * typical_lot:
        adj -= 0.02
        advice.append("Your lot is large compared with typical orders – a small volume discount (2%) is applied "
                      "to attract bulk buyers.")

    rec = pred * (1 + adj)
    lo, hi = prediction.get("lower_bound"), prediction.get("upper_bound")
    if lo and hi:
        rec = min(max(rec, lo), hi)

    best = max(sell_window, key=lambda w: w["price"]) if sell_window else None
    first = sell_window[0] if sell_window else None
    best_date = best_price = None
    if best and first and best["price"] > first["price"] * 1.03 and best["date"] != first["date"]:
        best_date, best_price = best["date"], best["price"]
        gain = 100 * (best["price"] / first["price"] - 1)
        advice.append(f"Prices are expected to be higher around {best['date']} (about KES {best['price']:.0f}/kg, "
                      f"{gain:+.1f}%). If you can store the produce safely, consider selling then.")
    else:
        advice.append("Selling at or soon after harvest is recommended – no better price is expected within the "
                      "produce's shelf life.")

    if prediction.get("confidence_label") == "low":
        advice.append("Confidence is low – compare with prices at your local market before listing.")

    return {"recommended_price": round(rec, 2), "adjustment_pct": round(100 * adj, 1), "decision_path": path,
            "demand_trend": trend, "advice": advice, "best_sell_date": best_date,
            "best_sell_price": round(best_price, 2) if best_price else None}


def sell_window_dates(crop: str, harvest: date, reference: date) -> list[date]:
    shelf = CROPS.get(crop, {}).get("shelf", 7)
    start = max(harvest, reference)
    span = min(shelf, 28)
    steps = [0, 7, 14, 21, 28]
    return [start + timedelta(days=s) for s in steps if s <= span]


def best_markets(crop: str, farmer_county: str, predict_fn, target: date, quantity: float | None = None) -> list[dict]:
    """Rank county markets by predicted price net of transport cost."""
    rows = []
    for county in markets_for(crop) if crop in CROPS else []:
        p = predict_fn(county)
        if not p.get("sufficient_data"):
            continue
        dist = 0.0 if county == farmer_county else haversine_km(farmer_county, county)
        transport = round(dist * TRANSPORT_KES_PER_KG_KM, 2)
        net = round(p["predicted_price"] - transport, 2)
        rows.append({"market": county, "predicted_price": p["predicted_price"], "distance_km": round(dist, 1),
                     "transport_cost_per_kg": transport, "net_price_per_kg": net,
                     "confidence_label": p["confidence_label"],
                     "estimated_total": round(net * quantity, 0) if quantity else None})
    rows.sort(key=lambda r: r["net_price_per_kg"], reverse=True)
    for i, r in enumerate(rows, 1):
        r["rank"] = i
    return rows


def _prefs(buyer: dict) -> list[str]:
    raw = buyer.get("preferences") or ""
    return [p.strip().lower() for p in raw.split(",") if p.strip()]


def match_score(product: dict, buyer: dict, market_price: float | None, history_count: int,
                today: date | None = None) -> dict:
    """Score how well a product listing suits a buyer (0..1) with human-readable reasons."""
    today = today or date.today()
    crop = (product.get("crop_type") or "").strip()
    prefs = _prefs(buyer)
    reasons = []

    pref = 1.0 if crop.lower() in prefs else 0.0
    if pref:
        reasons.append(f"Interested in {crop}")

    dist = haversine_km(product.get("location") or "", buyer.get("location") or "")
    prox = max(0.0, 1 - min(dist, 800) / 800)
    if dist < 1:
        reasons.append("Same county")
    elif dist < 150:
        reasons.append(f"Nearby ({dist:.0f} km)")

    price = product.get("price_per_unit")
    if price and market_price:
        diff = (market_price - price) / market_price
        price_s = max(0.0, min(1.0, 0.5 + diff * 2.5))
        if diff > 0.03:
            reasons.append(f"{100 * diff:.0f}% below local market price")
    else:
        price_s = 0.5

    hist = min(1.0, history_count / 10)
    if history_count:
        reasons.append(f"Bought {crop} {history_count} time(s) before")

    fresh = 0.5
    hd = product.get("harvest_date")
    if hd:
        try:
            days = abs((date.fromisoformat(str(hd)[:10]) - today).days)
            fresh = max(0.0, 1 - days / 30)
        except ValueError:
            pass

    parts = {"preference": pref, "proximity": prox, "price": price_s, "history": hist, "freshness": fresh}
    score = sum(WEIGHTS[k] * v for k, v in parts.items())
    return {"score": round(score, 4), "components": {k: round(v, 3) for k, v in parts.items()},
            "distance_km": round(dist, 1), "reasons": reasons}
