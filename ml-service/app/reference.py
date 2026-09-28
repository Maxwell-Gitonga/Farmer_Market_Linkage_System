"""
Reference data for the simulated Kenyan agricultural market.

The proposal (Section 1.6 Scope) limits the study to *selected agricultural produce*
within a *simulated Kenyan agricultural context*. This module is the single source of
truth for the crops and county markets used by the dataset generator, the ML models
and (via the /meta endpoint) the Spring Boot backend and Angular frontend.
"""
from __future__ import annotations

import math

# County markets with approximate coordinates (used for proximity scoring and
# transport-cost estimates in the recommendation engine).
COUNTIES: dict[str, tuple[float, float]] = {
    "Nairobi": (-1.2864, 36.8172),
    "Kiambu": (-1.1714, 36.8356),
    "Nakuru": (-0.3031, 36.0800),
    "Uasin Gishu": (0.5143, 35.2698),
    "Trans Nzoia": (1.0190, 35.0020),
    "Meru": (0.0470, 37.6490),
    "Nyeri": (-0.4201, 36.9476),
    "Machakos": (-1.5177, 37.2634),
    "Kisumu": (-0.0917, 34.7680),
    "Mombasa": (-4.0435, 39.6682),
}

# Crop catalogue. Prices are KES per kg at 2023 levels.
#  base      – reference wholesale price (KES/kg)
#  amp       – seasonal amplitude (fraction of base)
#  peak      – month (1-12) in which prices usually peak (scarcity before harvest)
#  vol       – week-to-week volatility
#  shelf     – typical shelf life in days (limits how long a farmer can wait to sell)
#  producers – surplus-producing counties (cheaper at farm gate)
#  demand    – national weekly platform demand (kg) at 2023 levels
#  markets   – counties where the crop is traded (None = all)
CROPS: dict[str, dict] = {
    "Maize":          dict(base=45,  amp=0.18, peak=6,  vol=0.030, shelf=180, producers=["Uasin Gishu", "Trans Nzoia", "Nakuru"], demand=9000, markets=None, emoji="🌽"),
    "Beans":          dict(base=130, amp=0.12, peak=5,  vol=0.030, shelf=180, producers=["Machakos", "Meru", "Trans Nzoia"],     demand=3500, markets=None, emoji="🫘"),
    "Irish Potatoes": dict(base=55,  amp=0.20, peak=4,  vol=0.050, shelf=45,  producers=["Nyeri", "Nakuru", "Meru"],              demand=7000, markets=None, emoji="🥔"),
    "Tomatoes":       dict(base=85,  amp=0.30, peak=4,  vol=0.080, shelf=7,   producers=["Kiambu", "Machakos", "Kisumu"],         demand=5000, markets=None, emoji="🍅"),
    "Onions":         dict(base=95,  amp=0.18, peak=3,  vol=0.050, shelf=60,  producers=["Nakuru", "Meru", "Machakos"],           demand=3000, markets=None, emoji="🧅"),
    "Cabbages":       dict(base=35,  amp=0.25, peak=2,  vol=0.070, shelf=14,  producers=["Nyeri", "Kiambu", "Nakuru"],            demand=6000, markets=None, emoji="🥬"),
    "Sukuma Wiki":    dict(base=45,  amp=0.22, peak=2,  vol=0.060, shelf=3,   producers=["Kiambu", "Nairobi", "Kisumu"],          demand=4500, markets=None, emoji="🥗"),
    "Bananas":        dict(base=55,  amp=0.10, peak=8,  vol=0.040, shelf=10,  producers=["Meru", "Kisumu", "Kiambu"],             demand=4000, markets=None, emoji="🍌"),
    "Avocados":       dict(base=70,  amp=0.30, peak=9,  vol=0.060, shelf=14,  producers=["Meru", "Nyeri", "Kiambu"],              demand=1800,
                           markets=["Nairobi", "Kiambu", "Meru", "Nyeri", "Nakuru", "Machakos"], emoji="🥑"),
    "Green Grams":    dict(base=140, amp=0.12, peak=10, vol=0.030, shelf=180, producers=["Machakos", "Meru", "Kisumu"],           demand=1500, markets=None, emoji="🫛"),
}

# Kenyan agricultural seasons ("external factors" in the conceptual framework).
def season_of(month: int) -> str:
    if month in (3, 4, 5):
        return "long_rains"
    if month in (10, 11, 12):
        return "short_rains"
    if month in (6, 7, 8, 9):
        return "cool_dry"
    return "hot_dry"  # Jan, Feb


SEASONS = ["long_rains", "short_rains", "cool_dry", "hot_dry"]


def markets_for(crop: str) -> list[str]:
    m = CROPS[crop]["markets"]
    return list(m) if m else list(COUNTIES.keys())


def haversine_km(a: str, b: str) -> float:
    """Great-circle distance between two county markets (km)."""
    if a not in COUNTIES or b not in COUNTIES:
        return 300.0  # unknown location – assume a moderate distance
    (la1, lo1), (la2, lo2) = COUNTIES[a], COUNTIES[b]
    p1, p2 = math.radians(la1), math.radians(la2)
    dp, dl = p2 - p1, math.radians(lo2 - lo1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


# Rough road-transport cost used by the best-market recommendation (KES per kg per km).
TRANSPORT_KES_PER_KG_KM = 0.035


def normalise_crop(name: str | None) -> str | None:
    """Case-insensitive lookup of a crop name ('sukuma wiki' -> 'Sukuma Wiki')."""
    if not name:
        return None
    key = name.strip().lower()
    for c in CROPS:
        if c.lower() == key:
            return c
    aliases = {"kale": "Sukuma Wiki", "potatoes": "Irish Potatoes", "potato": "Irish Potatoes",
               "ndengu": "Green Grams", "tomato": "Tomatoes", "onion": "Onions", "cabbage": "Cabbages",
               "banana": "Bananas", "avocado": "Avocados", "bean": "Beans", "corn": "Maize"}
    return aliases.get(key)


def normalise_county(name: str | None) -> str | None:
    if not name:
        return None
    key = name.strip().lower()
    for c in COUNTIES:
        if c.lower() == key:
            return c
    aliases = {"eldoret": "Uasin Gishu", "kitale": "Trans Nzoia", "thika": "Kiambu"}
    return aliases.get(key)
