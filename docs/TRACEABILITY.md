# Traceability: Proposal & Diagrams → Implementation

This maps every requirement in *Chapter 1–3 of the proposal* and every design artefact in the *Diagrams* file to the
code that implements it. Paths are relative to the project root
(`B` = `backend/src/main/java/ke/ac/strathmore/fmls`, `F` = `frontend/src/app`, `M` = `ml-service/app`).

## 1. Objectives & research questions

| Proposal | Where it is addressed |
|---|---|
| General objective – web-based linkage + price prediction, demand forecasting, recommendation support | Whole system (sections below) |
| (iii) Design the system | `docs/` + this document; ER schema `database/schema.sql`; entities `B/domain` |
| (iv) Develop & implement | `frontend/`, `backend/`, `ml-service/` |
| (v) Evaluate effectiveness (market access, pricing decisions, usability) | `docs/TEST_CASES.md`, `ml-service/reports/model_evaluation.md`, admin *Reports* & *ML models* screens |
| §1.6 Scope: list produce with quantity, price, availability | Listing form `F/pages/farmer/listing-form.ts`, `B/service/ProductService.addListing/publish` |
| §1.6 Buyer search, browse, communicate with farmer directly | `F/pages/buyer/browse.ts`, `product-detail.ts`, `F/pages/common/messages.ts`, `B/service/MessageService` |
| §1.6 Regression for price prediction | `M/price_model.py` (Linear Regression vs Random Forest vs Gradient Boosting; best selected) |
| §1.6 Time-series analysis for demand | `M/demand_model.py` (Holt-Winters seasonal / Holt trend / moving average, selected by back-test) |
| §1.6 Recommendations on pricing and selling strategy | `M/recommender.py` (recommended price, best time to sell, best market, buyer matching) |
| §1.6 Selected produce, simulated Kenyan context | `M/reference.py` (10 crops, 10 county markets), `ml-service/data/generate_dataset.py` |
| §1.6 Out of scope: logistics, transport, payments, supply-chain tracking | Not implemented (purchase records a transaction; payment/delivery "arranged directly with the farmer") |
| §1.7.1 Evaluation on simulated data | Simulated dataset + documented evaluation |

## 2. Proposed modules (§3.8)

| Module | Implementation |
|---|---|
| 3.8.1 User Management – registration, login, authentication, authorization; admin approves farmers, suspends/bans, tracks activity | `B/service/AuthService`, `B/security/*` (JWT, `AuthInterceptor`, `@RequireRole`), `B/service/AdminService.setStatus`, `B/service/ActivityLogService`; UI `F/pages/auth/login.ts`, `F/pages/admin/users.ts`, `activity.ts` |
| 3.8.2 Product Listing & Management – upload, modify, delete | `B/controller/ProductController` (POST/PUT/DELETE), `F/pages/farmer/listing-form.ts`, `my-listings.ts` |
| 3.8.3 Market Access & Interaction – browse and search | `B/service/ProductService.getAvailableListings`, `F/pages/buyer/browse.ts` |
| 3.8.4 Machine Learning & Data Analysis – price prediction, demand analysis, recommendations | `ml-service/` (FastAPI) + backend modules `B/module/PricePredictionModule`, `DemandForecastModule`, `RecommendationModule`, `MarketDataModule` |
| 3.8.5 Backend processing & API (Spring Boot) | `backend/` – REST controllers in `B/controller`, business logic in `B/service`, `B/module` |
| 3.8.6 User Interface (Angular) | `frontend/` |
| 3.8.7 Database Management (MySQL) | `B/domain` (JPA entities), `B/repository`, `database/schema.sql`, `B/config/DataSeeder` |
| 3.8.8 Architecture: role-based access after authentication, REST between front and back end, admin-only operations | `B/security/AuthInterceptor` + `@RequireRole` on controllers; `F/core/guards.ts`; `F/core/api.service.ts` |

## 3. Use case diagram

| Actor → use case | Screen | API |
|---|---|---|
| Farmer · Log In | Login | `POST /api/auth/login` |
| Farmer · List product | List produce | `POST /api/products` |
| Farmer · Get price recommendation (→ Analyse historical market data) | Price recommendation | `GET /api/products/{id}/recommendation` → ML `/recommend/price` |
| Farmer · Get demand forecast (→ Analyse historical demand data) | Dashboard, recommendation, Market insights | `GET /api/market/demand` → ML `/forecast/demand` |
| Farmer · View matched buyers | Matched buyers, Dashboard | `GET /api/products/{id}/matches` → ML `/recommend/matches` |
| Buyer · Log In | Login | `POST /api/auth/login` |
| Buyer · Search listings | Browse listings | `GET /api/products?crop=` |
| Buyer · View recommended products | Browse → "Recommended for you" | `GET /api/buyer/recommendations` |
| Buyer · Purchase product | Listing detail → Purchase | `POST /api/transactions` |
| Admin · Log In | Login (email) | `POST /api/auth/login` |
| Admin · Manage users (approve farmers, suspend/ban) | Admin → Users | `PATCH /api/admin/users/{role}/{id}/status` |
| Admin · Monitor system activity | Admin → Activity / Overview | `GET /api/admin/logs` |
| (§3.4.1) Admin · remove unwanted listings, view reports | Admin → Listings / Reports | `POST /api/admin/listings/{id}/remove`, `GET /api/admin/reports` |

## 4. Activity diagram (Farmer swim-lane ↔ System swim-lane)

| Step / decision | Implementation |
|---|---|
| Farmer logs in → enters listing | `LoginPage` → `ListingFormPage` |
| System fetches history, **decision: sufficient historical data?** | `M/price_model.py PriceModel.predict` – needs ≥ 6 records for the county (or nationally) in the last 60 days; *no* → `sufficient_data=false`, confidence *low*, "Limited data" alert, farmer must set own price (`recommendation.ts` opens "Set own price") |
| System forecasts demand, **decision: is the demand trend rising?** | `M/demand_model.py forecast` → RISING / STABLE / FALLING; `M/recommender.py recommend_price`: rising → +4 %, falling → −3 % (`decision_path` = `demand_rising` / `demand_not_rising`) |
| System shows recommended price, **decision: farmer accepts recommended price or sets custom?** | "List at this price" / "Set own price" → `POST /api/products/{id}/publish {price, acceptedRecommendation}` |
| Paths converge → farmer views matched buyers → end session | `RecommendationModule.matchedBuyersForProduct` → `MatchedBuyersPage` |

## 5. Sequence diagrams

**Diagram 1 – Farmer / UserInterface / Product / MarketDataModule / PricePredictionModule**

| Message | Code |
|---|---|
| `login(phone, password)` / login successful | `AuthService.login` (phone or email, BCrypt) |
| `enters product listing (cropType, quantity, harvestDate)` | `ListingFormPage.submit` |
| `addListing()` → `return productID` | `ProductService.addListing` (DRAFT product saved, id returned in `ListingCreatedResponse`) |
| `fetchHistoricalPrices(cropType)` + self-call `updateMarketData()` → `return historicalPrices` | `MarketDataModule.fetchHistoricalPrices` calls `updateMarketData()` (simulated real-time market feed) |
| `generatePricePrediction(product)` | `PricePredictionModule.generatePricePrediction` → ML `POST /recommend/price` |
| self-call `extractFeatures(data)` | `M/price_model.py _rolling_block / _feature_rows` |
| alt [sufficient historical data] `return predictedPrice` → `displayPriceRecommendation(price)` | `RecommendationView.sufficientData=true` → recommendation screen |
| alt [insufficient historical data] `return confidence: low` → `displayAlert('limited data')` | `sufficientData=false`, `confidenceLabel=low` → "Limited data" alert |

**Diagram 2 – Buyer / UserInterface / Product / RecommendationModule / Transaction**

| Message | Code |
|---|---|
| `login(phone, password)` | `AuthService.login` |
| `searchListings(cropType)` → `getAvailableListings(cropType)` → `return listings` | `BrowsePage.load` → `ProductService.getAvailableListings` |
| `matchBuyerToProducts(buyer)` + self-call `rankMatches(matches)` → `return rankedMatches` | `RecommendationModule.matchBuyerToProducts` → ML `/recommend/matches` (scores + ranking) |
| `displayListings(matches)` | "Recommended for you" + "All listings" |
| `purchaseProduct(productID)` → `createTransaction()` → `return transactionID` | `TransactionService.purchaseProduct` → `createTransaction` |
| `updateStatus('sold')` | `TransactionService.updateStatus` (SOLD when the full quantity is bought) |
| `displayAlert('Purchase confirmed')` | `PurchaseResponse.message = "Purchase confirmed"` → toast |

## 6. Class diagram (§3.4.4)

| Class | Implementation |
|---|---|
| User (base) → Farmer, Buyer, Admin (inherit) | `B/domain/User` (`@MappedSuperclass`), `Farmer`, `Buyer`, `Admin` (one table each, as in the ER design) |
| Single sign-in, user type identified after login | `AuthService.login` searches farmers/buyers/admins; token carries the role; `AuthService.home()` routes the UI |
| Product | `B/domain/Product` |
| Transaction | `B/domain/Transaction` |
| Machine Learning Model | Python `PriceModel` (`M/price_model.py`), demand forecaster (`M/demand_model.py`), recommender (`M/recommender.py`); Java façade classes `PricePredictionModule`, `DemandForecastModule`, `RecommendationModule` |
| Admin extra tasks (user management, listing control) | `B/service/AdminService` |

## 7. Database design (ER diagram + dbdiagram.io)

All tables and columns of the design exist with the same names: `farmers`, `buyers`, `admins`, `products`,
`market_prices` (`crop_type, market_location, price, recorded_date, source`), `price_predictions`
(`predicted_price, confidence DECIMAL(4,3), model_version, created_at`), `demand_forecasts`
(`crop_type, forecast_period, trend, generated_at`), `matches` (`match_score`), `transactions`
(`product_id, farmer_id, buyer_id, agreed_price, quantity, status, transaction_date`).
Relationships: farmer lists products (1–N), product receives price predictions (1–N), market history informs the
prediction, product sold in transactions, buyer purchases through transactions, product–buyer matches.

**Additions needed to make the design work** (documented deviations):

| Addition | Why |
|---|---|
| `products.price_per_unit`, `recommended_price`, `location`, `description` | §1.6 says farmers specify price and availability; wireframe form has *Location* |
| `farmers/buyers/admins.status`, `last_login_at` | §3.8.1 approval, suspension, banning, activity tracking |
| `price_predictions.recommended_price, confidence_label, lower_bound, upper_bound, demand_trend, based_on_records, details_json` | Detail view shows confidence label, range, "Based on …" and advice |
| `demand_forecasts.predicted_quantity, change_pct, model` | Forecast magnitude shown in the UI |
| `matches.reasons` | Explains each match to the farmer |
| `messages` table | §1.6 "communicate with the farmer directly", architecture "Contact Seller" |
| `system_logs` table | Architecture diagram "System Logs", admin "Monitor system activity" |

## 8. Wireframes

| Wireframe | Screen | Notes |
|---|---|---|
| Login screen (logo, title, Login/Register, Farmer/Buyer, phone, password, Log in) | `F/pages/auth/login.ts` | Same layout; Register adds name, county, email, buyer preferences |
| Farmer — Product Listing Form (crop type, quantity kg, harvest date, location, *System-generated (preview)*, Submit listing) | `F/pages/farmer/listing-form.ts` | Preview updates live via `POST /api/market/quick-estimate` |
| Farmer — Dashboard (Recommended price, Demand trend, Active listings; Your listings; Matched buyers) | `F/pages/farmer/dashboard.ts` | Plus recent sales and pending orders |
| Buyer Dashboard (search + 2 filters, Recommended for you cards, All listings rows) | `F/pages/buyer/browse.ts` | County filter + sort |
| Price Recommendation Detail View (price per kg, Confidence: high, 30-day price trend bars, Demand forecast / Based on, List at this price / Set own price) | `F/pages/farmer/recommendation.ts` | Plus advice, best time to sell, best markets |

## 9. Conceptual framework

Inputs (farmer, market, buyer data, external factors → season feature) → Process (price regression, demand time
series, recommendation engine) → Outputs (optimal price recommendations, demand insights, buyer–farmer matches,
market listings) → Outcomes (market access, better decisions, fewer middlemen, profitability: *Reports* screen).

## 10. Development tools (§3.5)

MySQL (XAMPP) ✔ · Python ✔ · Angular ✔ · Spring Boot ✔ · REST API ✔ · XAMPP as local environment ✔ ·
Draw.io – design diagrams (already produced).

## 11. Testing (§3.6)

Functional, integration and usability test cases with expected/actual results: `docs/TEST_CASES.md`.
Automated: `ml-service/tests/test_api.py` (14 tests) and `backend/src/test/.../SystemFlowIntegrationTest.java`.
