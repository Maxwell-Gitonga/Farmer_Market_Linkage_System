"""
Simulated Kenyan agricultural market dataset generator.

Produces the historical data the decision-support module learns from (Proposal §1.6 and
§1.7.1: the system is evaluated on *sample/simulated data* in a *simulated Kenyan
agricultural context*):

  market_prices.csv            crop_type, market_location, price, recorded_date, source
  historical_transactions.csv  farmer_ref, buyer_ref, crop_type, county, quantity,
                               agreed_price, transaction_date
  seed_farmers.csv / seed_buyers.csv   demo user profiles referenced above

Prices follow: 2023 base level x county premium (distance from producing counties)
x seasonality (month of scarcity) x inflation trend x crop-wide shock (AR(1))
x county-level noise (AR(1)). Market days are Tuesdays and Fridays.

Weekly demand follows: base x seasonality (festive December, January lull, harvest
months) x growth trend x price elasticity x noise, split into 2-5 transactions.

Run:  python data/generate_dataset.py            (deterministic, seed=42)
"""
from __future__ import annotations

import csv
import math
import os
import random
import sys
from datetime import date, timedelta

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from app.reference import COUNTIES, CROPS, haversine_km, markets_for  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
START = date(2023, 7, 4)          # a Tuesday
END = date(2026, 9, 22)           # a Tuesday – dates are shifted to "today" when seeded
SEED = 42

FIRST = ["Wanjiru", "Kamau", "Achieng", "Otieno", "Chebet", "Kiprotich", "Mwangi", "Njeri", "Wafula", "Nafula",
         "Mutua", "Mwikali", "Kimani", "Atieno", "Kipchumba", "Jepkosgei", "Omondi", "Akinyi", "Gitau", "Wambui",
         "Barasa", "Nekesa", "Muthoni", "Karanja", "Rotich", "Jeruto", "Ochieng", "Adhiambo", "Kiplagat", "Moraa"]
LAST = ["Kariuki", "Odhiambo", "Kosgei", "Wekesa", "Mutiso", "Njoroge", "Onyango", "Cheruiyot", "Maina", "Owino",
        "Kiptoo", "Ndungu", "Makori", "Langat", "Muriuki", "Oduor", "Kibet", "Wairimu", "Ruto", "Nyaga"]
BUYER_ORGS = ["Mama Mboga Traders", "Greenleaf Grocers", "Uzima Hotel", "Soko Fresh Ltd", "Tuinuane Supermarket",
              "Baraka Schools Catering", "Coast Fresh Produce", "Lakeside Restaurants", "Highlands Processors",
              "Jiji Foods Kiosk", "Mavuno Millers", "City Market Wholesalers", "Kilimo Fresh Exporters",
              "Neema Hospital Kitchen", "Rift Valley Grocers"]


def county_factor(crop: str, county: str) -> float:
    meta = CROPS[crop]
    if county in meta["producers"]:
        return 0.90
    d = min(haversine_km(county, p) for p in meta["producers"])
    f = 1.0 + 0.00045 * d
    if county == "Nairobi":
        f += 0.05          # urban retail premium
    if county == "Mombasa":
        f += 0.04
    return f


def seasonal(crop: str, d: date) -> float:
    meta = CROPS[crop]
    m = d.month - 1 + (d.day - 1) / 30.0
    s = 1 + meta["amp"] * math.cos(2 * math.pi * (m - (meta["peak"] - 1)) / 12)
    if crop == "Tomatoes":  # bimodal – short-rains scarcity in November
        s += 0.10 * math.cos(2 * math.pi * (m - 10) / 12 * 2)
    return s


def market_days(start: date, end: date):
    d = start
    while d <= end:
        if d.weekday() in (1, 4):  # Tue, Fri
            yield d
        d += timedelta(days=1)


def generate_prices(rng: random.Random):
    days = list(market_days(START, END))
    rows = []
    national_index: dict[tuple[str, date], float] = {}
    for crop, meta in CROPS.items():
        shock = 0.0
        county_noise = {c: 0.0 for c in COUNTIES}
        for d in days:
            shock = 0.97 * shock + rng.gauss(0, meta["vol"] * 0.55)
            years = (d - START).days / 365.25
            trend = (1.06) ** years
            base = meta["base"] * seasonal(crop, d) * trend * math.exp(shock)
            vals = []
            for county in markets_for(crop):
                county_noise[county] = 0.80 * county_noise[county] + rng.gauss(0, meta["vol"] * 0.6)
                price = base * county_factor(crop, county) * math.exp(county_noise[county])
                # Occasional missing report from a market (realistic gaps)
                if rng.random() < 0.04:
                    continue
                price = round(max(5.0, price), 2)
                rows.append(dict(crop_type=crop, market_location=county, price=price,
                                 recorded_date=d.isoformat(), source="KAMIS_SIMULATED"))
                vals.append(price)
            if vals:
                national_index[(crop, d)] = sum(vals) / len(vals)
    return rows, national_index


def demand_season(d: date) -> float:
    m = d.month
    f = 1.0
    if m == 12:
        f += 0.22            # festive season
    elif m == 1:
        f -= 0.12            # January lull
    if m in (2, 5, 9):       # school re-opening (boarding school procurement)
        f += 0.10
    if m in (7, 8):          # main harvest – more produce moving
        f += 0.06
    return f


def generate_people(rng: random.Random):
    counties = list(COUNTIES)
    farmers, buyers = [], []
    used = set()
    for i in range(1, 31):
        while True:
            name = f"{rng.choice(FIRST)} {rng.choice(LAST)}"
            if name not in used:
                used.add(name)
                break
        county = counties[(i - 1) % len(counties)]
        farmers.append(dict(ref=i, name=name, phone=f"07{11000000 + i:08d}"[:10],
                            email=f"farmer{i}@fmls.demo", location=county))
    for i in range(1, 16):
        org = BUYER_ORGS[i - 1]
        county = counties[(i * 3) % len(counties)]
        prefs = rng.sample(list(CROPS), k=rng.randint(2, 4))
        buyers.append(dict(ref=i, name=org, phone=f"07{22000000 + i:08d}"[:10],
                           email=f"buyer{i}@fmls.demo", location=county, preferences=",".join(prefs)))
    # Friendly, memorable demo accounts (documented in README)
    farmers[0].update(name="Maxwell Demo Farmer", phone="0711000001", location="Nakuru")
    buyers[0].update(name="Demo Buyer (Soko Fresh)", phone="0722000001", location="Nairobi",
                     preferences="Maize,Irish Potatoes,Tomatoes")
    return farmers, buyers


def generate_transactions(rng: random.Random, national_index, farmers, buyers, price_lookup):
    rows = []
    week = START
    while week <= END - timedelta(days=6):
        years = (week - START).days / 365.25
        for crop, meta in CROPS.items():
            # Price index relative to base -> elasticity effect
            idx_price = national_index.get((crop, week)) or national_index.get((crop, week + timedelta(days=3)))
            rel = (idx_price / meta["base"]) if idx_price else 1.0
            qty = (meta["demand"] * demand_season(week) * (1.08 ** years) * (rel ** -0.4)
                   * math.exp(rng.gauss(0, 0.08)))
            n = rng.randint(2, 5)
            weights = [rng.random() + 0.3 for _ in range(n)]
            tot = sum(weights)
            markets = markets_for(crop)
            for w in weights:
                producers = [f for f in farmers if f["location"] in meta["producers"] and f["location"] in markets]
                pool = producers if producers and rng.random() < 0.7 else [f for f in farmers if f["location"] in markets]
                farmer = rng.choice(pool)
                interested = [b for b in buyers if crop in b["preferences"].split(",")]
                buyer = rng.choice(interested) if interested and rng.random() < 0.75 else rng.choice(buyers)
                monday = week - timedelta(days=week.weekday())
                tdate = monday + timedelta(days=rng.randint(0, 6))
                mp = price_lookup(crop, farmer["location"], tdate) or idx_price or meta["base"]
                rows.append(dict(farmer_ref=farmer["ref"], buyer_ref=buyer["ref"], crop_type=crop,
                                 county=farmer["location"], quantity=round(qty * w / tot, 1),
                                 agreed_price=round(mp * rng.uniform(0.95, 1.05), 2),
                                 transaction_date=tdate.isoformat()))
        week += timedelta(days=7)
    return rows


def write_csv(path, rows, fields):
    with open(path, "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


def main():
    rng = random.Random(SEED)
    prices, national_index = generate_prices(rng)
    by_key: dict[tuple[str, str], list[tuple[date, float]]] = {}
    for r in prices:
        by_key.setdefault((r["crop_type"], r["market_location"]), []).append(
            (date.fromisoformat(r["recorded_date"]), r["price"]))

    def price_lookup(crop, county, d):
        series = by_key.get((crop, county))
        if not series:
            return None
        best = None
        for sd, p in series:
            if sd <= d:
                best = p
            else:
                break
        return best

    farmers, buyers = generate_people(rng)
    txns = generate_transactions(rng, national_index, farmers, buyers, price_lookup)

    write_csv(os.path.join(OUT, "market_prices.csv"), prices,
              ["crop_type", "market_location", "price", "recorded_date", "source"])
    write_csv(os.path.join(OUT, "historical_transactions.csv"), txns,
              ["farmer_ref", "buyer_ref", "crop_type", "county", "quantity", "agreed_price", "transaction_date"])
    write_csv(os.path.join(OUT, "seed_farmers.csv"), farmers, ["ref", "name", "phone", "email", "location"])
    write_csv(os.path.join(OUT, "seed_buyers.csv"), buyers, ["ref", "name", "phone", "email", "location", "preferences"])
    print(f"market_prices: {len(prices)} rows | transactions: {len(txns)} rows | "
          f"farmers: {len(farmers)} | buyers: {len(buyers)} | period {START} -> {END}")


if __name__ == "__main__":
    main()
