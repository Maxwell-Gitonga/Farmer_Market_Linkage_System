# Test Cases (Proposal §3.6)

Testing follows the proposal: **functional**, **integration** and **usability** tests, each with a predefined test
case, expected outcome and actual result. Automated tests cover the same cases:

* `ml-service/tests/test_api.py` – 14 tests of the ML module through its REST API (`python -m pytest -q`)
* `backend/src/test/java/ke/ac/strathmore/fmls/SystemFlowIntegrationTest.java` – end-to-end API flow on an
  in-memory database (`mvnw test`)

**Status column.** ✔ = executed during development and behaved as expected. For the ML module this means
`pytest` (14 passed). For the user-interface flows it means a scripted browser run (Playwright) of the Angular app
against the ML module. Rows marked ☐ still need their result recorded when you run `mvnw test` and the manual checks
on your own machine. Fill in the *Actual result* column as you test.

## A. Functional tests

| ID | Feature | Steps | Expected result | Actual result | Status |
|---|---|---|---|---|---|
| F01 | Farmer registration | Register → Farmer, name, phone 07…, password, county | Account created with status PENDING, farmer lands on dashboard with "awaiting approval" banner | | ☐ |
| F02 | Buyer registration | Register → Buyer, choose preferred produce | Account ACTIVE immediately; preferences saved | | ☐ |
| F03 | Validation | Register with phone `12345` or password < 6 chars | 400 error message shown, no account created | | ☐ (`validationAndDuplicateRegistration`) |
| F04 | Duplicate registration | Register the same phone twice (same role) | 409 "already exists" | | ☐ (automated) |
| F05 | Single sign-in / role identification | Log in as farmer, buyer, admin (email) | Each is routed to their own dashboard; menus differ by role | Farmer → Dashboard, Buyer → Browse, Admin → Administration | ✔ |
| F06 | Wrong password | Log in with a wrong password | "Incorrect phone number or password" | | ☐ (automated) |
| F07 | Role-based access | Buyer calls `POST /api/products`; farmer opens `/api/admin/users`; no token | 403, 403, 401 | | ☐ (automated) |
| F08 | List produce + preview | Farmer: crop Maize, 1200 kg, harvest +10 days, Nakuru | Preview shows recommended price, confidence, demand trend, estimated value | KES 37.50/kg, confidence high, demand falling (−5.1 %) | ✔ |
| F09 | Submit listing | Click *Submit listing* | Listing saved as DRAFT, price recommendation screen opens | Recommendation view with 30-day trend, demand, advice, best markets | ✔ |
| F10 | Accept recommended price | *List at this price* | Listing AVAILABLE at the recommended price; matched buyers shown | Buyers ranked with match % and reasons | ✔ |
| F11 | Set own price | *Set own price*, enter a value | Listing AVAILABLE at the custom price; difference vs predicted shown | | ☐ |
| F12 | Insufficient historical data | List *Other → Macadamia* | "Limited data" alert, confidence low, only *Set own price* available | Alert shown, own-price form opened | ✔ |
| F13 | Pending farmer cannot publish | Pending farmer tries to publish | 403 "awaiting administrator approval"; listing stays DRAFT | | ☐ (automated) |
| F14 | Edit / delete listing | My listings → Edit quantity / Delete | Changes saved / status REMOVED | | ☐ |
| F15 | Buyer search | Browse → type "maize" | Only matching available listings shown | Filtered list shown | ✔ |
| F16 | Recommended products | Buyer browse | "Recommended for you" ranked by preference, proximity, price, history | Cards with match % and reasons | ✔ |
| F17 | Purchase | Open listing → quantity → Purchase | "Purchase confirmed", transaction CONFIRMED, quantity reduced; SOLD when all bought | Confirmation toast, purchases page | ✔ |
| F18 | Over-quantity purchase | Buy more than available | 400 "Only … available" | | ☐ (automated) |
| F19 | Complete / cancel sale | Farmer → Sales → Mark completed / Cancel | COMPLETED (recorded as market price) / CANCELLED (quantity returned) | Mark completed succeeded | ✔ |
| F20 | Messaging | Buyer → Message seller; farmer replies | Conversation visible to both; unread badge | | ☐ (automated) |
| F21 | Admin approves farmer | Admin → Users → Approve | Status ACTIVE; farmer can publish | Approve succeeded | ✔ |
| F22 | Suspend / ban | Admin suspends a buyer | Buyer's next request and login are refused (403) | | ☐ (automated) |
| F23 | Remove listing | Admin → Listings → Remove | Status REMOVED, hidden from buyers, logged | | ☐ |
| F24 | Monitor activity | Admin → Activity | Logins, listings, purchases, approvals listed | Events listed | ✔ |
| F25 | Reports & export | Admin → Reports → CSV | Charts and tables by crop/month; CSV downloads | Charts and tables shown | ✔ |
| F26 | Market data | Admin adds a price / imports CSV | Price saved; latest prices table updated | | ☐ |
| F27 | Retrain models | Admin → ML models → Retrain | Models retrained on database data; new version + metrics shown | | ☐ |

## B. Integration tests

| ID | Integration | Expected result | Actual result | Status |
|---|---|---|---|---|
| I01 | Angular ↔ Spring Boot (REST, JSON, token) | All screens load data through `/api`, token sent on each call, expired token returns to login | | ☐ |
| I02 | Spring Boot ↔ MySQL | Tables created automatically; seed data loaded (≈31,100 prices, ≈5,900 sales); data persists across restarts | Seed SQL verified on MariaDB 10.11 | ✔ (SQL) |
| I03 | Spring Boot ↔ Python ML (price) | `POST /recommend/price` receives the crop's price history + demand history and returns prediction, demand, advice | ML side verified (pytest) | ✔ |
| I04 | Spring Boot ↔ Python ML (matching) | `POST /recommend/matches` returns ranked buyers / products | ML side verified (pytest) | ✔ |
| I05 | ML module offline | Backend still answers with a moving-average estimate, clearly flagged "offline", no crash | | ☐ (automated – ML unreachable in test profile) |
| I06 | Retraining from database | `POST /train` with backend payload trains on DB data (`data_source = backend_database`) | pytest `test_train_endpoint_with_backend_payload` | ✔ |
| I07 | Market data feed | Latest market data is always current (dates shifted; Tue/Fri feed) | Latest date = current week | ✔ |

## C. Machine-learning validation (§3.2.6 – accuracy of pricing and demand models)

| ID | Check | Expected | Actual | Status |
|---|---|---|---|---|
| M01 | Price model beats naive "last price" baseline on a time-based hold-out (last 180 days) | Lower MAPE | Gradient Boosting MAPE **6.78 %** vs naive 9.28 % (26.9 % better), R² 0.975 | ✔ |
| M02 | Model comparison | Linear Regression, Random Forest, Gradient Boosting compared; best selected | LR 7.03 %, RF 6.86 %, GB 6.78 % → GB selected | ✔ |
| M03 | Demand forecast beats naive baselines (rolling-origin back-test, 13 origins, 4-week horizon) | Lower MAPE | **9.28 %** vs naive 11.02 %, seasonal-naive 10.10 % | ✔ |
| M04 | Trend classification | Rising/falling/flat synthetic series → RISING/FALLING/STABLE | All three correct | ✔ |
| M05 | Prediction interval | lower ≤ predicted ≤ upper | Holds | ✔ |
| M06 | National fallback | Crop not traded in a county (Avocados, Mombasa) → national estimate, lower confidence | basis = national, message shown | ✔ |
| M07 | Rising demand raises the recommended price, falling lowers it | rec(rising) > predicted > rec(falling) | Holds | ✔ |
| M08 | Matching ranks interested, nearby, cheaper-than-market buyers first | Correct order | Holds | ✔ |
| M09 | Best markets sorted by net price after transport | Descending net price | Holds | ✔ |

Full figures per crop: `ml-service/reports/model_evaluation.md` (regenerated by `python train.py` or admin retrain).

## D. Usability tests (with sample farmers, buyers and administrators)

Ask each participant to complete the task without help. Record the time taken, errors made and a 1–5 ease rating.

| ID | User | Task | Success criterion | Time | Errors | Ease (1–5) | Comments |
|---|---|---|---|---|---|---|---|
| U01 | Farmer | Register and list 500 kg of tomatoes | Listing created in < 3 min without help | | | | |
| U02 | Farmer | Find the recommended price and decide to accept or change it | Explains the recommended price and confidence in own words | | | | |
| U03 | Farmer | Find which buyers are interested | Opens matched buyers and sends a message | | | | |
| U04 | Buyer | Find potatoes near Nairobi and buy 100 kg | Purchase confirmed in < 2 min | | | | |
| U05 | Buyer | Tell whether a listing is cheap or expensive | Uses "vs market" / local market price | | | | |
| U06 | Admin | Approve the pending farmer and remove a suspicious listing | Both done in < 2 min | | | | |
| U07 | Admin | Report the most traded crop this year | Uses Reports | | | | |
| U08 | Any | Use the system on a phone | All screens usable at phone width | Login screen verified at 390 px | | | |
