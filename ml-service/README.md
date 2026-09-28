# Machine Learning and Data Analysis Module (Python)

Implements the decision support of the proposal (§1.2, §1.6, §3.8.4) as a REST service used by the Spring Boot backend.

| Technique | File | Details |
|---|---|---|
| **Price prediction – regression** | `app/price_model.py` | Direct multi-horizon regression: predicts the price at the harvest date from the price history up to today. Features: horizon, momentum, volatility, level vs long-run mean, county-vs-national ratio, learned seasonal profile, target month, Kenyan season (external factor), crop, county. Linear Regression, Random Forest and Gradient Boosting are compared on a time-based hold-out (last 180 days); the best is refitted on all data. Outputs price, 80 % range, confidence (from per-crop accuracy, data coverage and horizon). |
| **Demand forecasting – time series** | `app/demand_model.py` | Weekly platform demand per crop; Holt-Winters seasonal / Holt linear trend / moving average chosen by back-testing on the latest 12 weeks; trend RISING / STABLE / FALLING (±5 %). Evaluated with a rolling-origin back-test against naive and seasonal-naive baselines. |
| **Recommendation engine** | `app/recommender.py` | Recommended price (prediction adjusted for demand: +4 % rising, −3 % falling, −2 % large lots), best time to sell within shelf life, best market net of transport cost, buyer–product matching score (preference 0.35, proximity 0.25, price vs market 0.20, purchase history 0.15, freshness 0.05). |
| Simulated Kenyan dataset | `data/generate_dataset.py` | 10 crops × 10 county markets, twice-weekly market days, 3+ years; seasonality, county premiums, inflation, shocks; weekly demand with festive/school/harvest effects. |

## Run
```bash
pip install -r requirements.txt
python -m uvicorn app.main:app --port 8000     # API docs: http://localhost:8000/docs
python train.py                                # retrain from the bundled dataset + write reports/model_evaluation.md
python -m pytest -q                            # tests
python data/generate_dataset.py                # regenerate the simulated dataset (deterministic)
```
The backend retrains the models on the MySQL data through `POST /train` (admin button and every Sunday 02:00).

## Results (hold-out / back-test)
Price model (Gradient Boosting): **MAPE 6.78 %**, R² 0.975, vs naive last-price 9.28 %.
Demand forecast: **MAPE 9.28 %** vs naive 11.02 % and seasonal-naive 10.10 %. Details per crop: `reports/model_evaluation.md`.
