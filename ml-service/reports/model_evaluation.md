# Model Evaluation Report

Generated: 2026-11-14T23:02:04.346437+00:00  
Training data: 31976 market-price records, 5933 transactions (backend_database)

## 1. Price prediction (regression)

Hold-out: last 180 days (time-based split, no look-ahead). Train samples: 79565, test samples: 13741.

| Model | MAE (KES/kg) | RMSE | MAPE | R² |
|---|---|---|---|---|
| linear_regression | 5.603 | 7.816 | 7.04% | 0.9769 |
| random_forest | 5.427 | 7.48 | 6.95% | 0.9789 |
| gradient_boosting **(selected)** | 5.364 | 7.415 | 6.91% | 0.9792 |
| naive_last_price_baseline | 6.922 | 11.155 | 8.62% | 0.953 |

Improvement of the selected model over the naive 'last price' baseline: **19.8%** lower MAPE.

| Crop | Model MAPE | Naive MAPE | Test samples |
|---|---|---|---|
| Avocados | 7.90% | 10.50% | 914 |
| Bananas | 5.58% | 4.73% | 1417 |
| Beans | 3.52% | 4.29% | 1422 |
| Cabbages | 13.11% | 10.04% | 1451 |
| Green Grams | 3.53% | 3.99% | 1404 |
| Irish Potatoes | 7.05% | 9.44% | 1436 |
| Maize | 5.04% | 6.49% | 1423 |
| Onions | 6.32% | 9.58% | 1432 |
| Sukuma Wiki | 6.61% | 8.62% | 1420 |
| Tomatoes | 10.57% | 19.12% | 1422 |

## 2. Demand forecasting (time series)

Method: rolling-origin back-test, 13 origins, 4-week horizon, WAPE

Mean WAPE: **9.14%** vs naive 10.75% and seasonal-naive 10.06% (improvement over naive: 15.0%). WAPE = total absolute error ÷ total actual demand over each 4-week window; weeks with almost no recorded sales are treated as missing data.

| Crop | Model WAPE | Naive | Seasonal naive | Models selected across folds |
|---|---|---|---|---|
| Maize | 8.66% | 11.23% | 10.68% | holt_linear_trend×2, moving_average×1, holt_winters_seasonal×9 |
| Beans | 9.77% | 10.17% | 9.85% | moving_average×2, holt_winters_seasonal×10 |
| Irish Potatoes | 8.70% | 11.98% | 11.58% | holt_linear_trend×3, holt_winters_seasonal×9 |
| Tomatoes | 11.78% | 10.72% | 10.68% | holt_linear_trend×3, holt_winters_seasonal×9 |
| Onions | 7.92% | 9.58% | 7.70% | holt_winters_seasonal×12 |
| Cabbages | 9.18% | 12.42% | 10.71% | holt_linear_trend×2, holt_winters_seasonal×10 |
| Sukuma Wiki | 8.93% | 9.35% | 10.36% | holt_linear_trend×2, moving_average×2, holt_winters_seasonal×8 |
| Bananas | 9.03% | 10.54% | 10.00% | holt_linear_trend×3, moving_average×1, holt_winters_seasonal×8 |
| Avocados | 9.87% | 12.77% | 10.40% | holt_linear_trend×3, moving_average×1, holt_winters_seasonal×8 |
| Green Grams | 7.59% | 8.76% | 8.61% | holt_linear_trend×6, moving_average×1, holt_winters_seasonal×5 |

## 3. Recommendation engine

Rule-based decision support on top of the two models (see app/recommender.py): demand-adjusted price recommendation, optimal selling date within shelf life, best-market ranking net of transport cost, and weighted buyer–farmer matching (preference 0.35, proximity 0.25, price 0.20, purchase history 0.15, freshness 0.05).
