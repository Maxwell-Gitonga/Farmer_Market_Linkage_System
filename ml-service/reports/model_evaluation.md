# Model Evaluation Report

Generated: 2026-09-23T16:35:07.681330+00:00  
Training data: 31100 market-price records, 5925 transactions (bundled_simulated_dataset)

## 1. Price prediction (regression)

Hold-out: last 180 days (time-based split, no look-ahead). Train samples: 76823, test samples: 13604.

| Model | MAE (KES/kg) | RMSE | MAPE | R² |
|---|---|---|---|---|
| linear_regression | 5.858 | 8.287 | 7.03% | 0.9728 |
| random_forest | 5.668 | 7.964 | 6.86% | 0.9749 |
| gradient_boosting **(selected)** | 5.572 | 7.885 | 6.77% | 0.9754 |
| naive_last_price_baseline | 7.515 | 11.705 | 9.28% | 0.9457 |

Improvement of the selected model over the naive 'last price' baseline: **27.0%** lower MAPE.

| Crop | Model MAPE | Naive MAPE | Test samples |
|---|---|---|---|
| Avocados | 8.91% | 12.79% | 889 |
| Bananas | 5.26% | 5.48% | 1411 |
| Beans | 3.25% | 3.92% | 1398 |
| Cabbages | 11.30% | 11.85% | 1439 |
| Green Grams | 3.75% | 4.16% | 1415 |
| Irish Potatoes | 7.10% | 9.51% | 1383 |
| Maize | 4.92% | 6.47% | 1430 |
| Onions | 6.71% | 10.21% | 1425 |
| Sukuma Wiki | 7.28% | 9.36% | 1405 |
| Tomatoes | 9.89% | 20.37% | 1409 |

## 2. Demand forecasting (time series)

Method: rolling-origin back-test, 13 origins, 4-week horizon

Mean MAPE: **9.24%** vs naive 11.02% and seasonal-naive 10.10% (improvement over naive: 16.2%).

| Crop | Model MAPE | Naive | Seasonal naive | Models selected across folds |
|---|---|---|---|---|
| Maize | 8.69% | 11.04% | 11.06% | moving_average×1, holt_winters_seasonal×9, holt_linear_trend×3 |
| Beans | 9.43% | 9.93% | 9.59% | moving_average×2, holt_winters_seasonal×9, holt_linear_trend×2 |
| Irish Potatoes | 9.40% | 11.84% | 11.40% | moving_average×1, holt_winters_seasonal×9, holt_linear_trend×3 |
| Tomatoes | 12.22% | 12.94% | 10.53% | holt_winters_seasonal×10, holt_linear_trend×3 |
| Onions | 7.93% | 9.45% | 7.74% | holt_winters_seasonal×13 |
| Cabbages | 9.42% | 12.66% | 11.02% | holt_winters_seasonal×10, holt_linear_trend×3 |
| Sukuma Wiki | 8.86% | 9.37% | 10.41% | moving_average×2, holt_winters_seasonal×8, holt_linear_trend×3 |
| Bananas | 9.09% | 10.95% | 10.06% | moving_average×2, holt_winters_seasonal×8, holt_linear_trend×3 |
| Avocados | 9.58% | 12.96% | 9.98% | moving_average×1, holt_winters_seasonal×9, holt_linear_trend×3 |
| Green Grams | 7.76% | 9.03% | 9.22% | moving_average×3, holt_winters_seasonal×5, holt_linear_trend×5 |

## 3. Recommendation engine

Rule-based decision support on top of the two models (see app/recommender.py): demand-adjusted price recommendation, optimal selling date within shelf life, best-market ranking net of transport cost, and weighted buyer–farmer matching (preference 0.35, proximity 0.25, price 0.20, purchase history 0.15, freshness 0.05).
