# Web-Based Farmer Market Linkage System with Integrated Decision Support for Price Optimization and Demand Analysis

**Gitonga, Maxwell Leon · 164430 · BBIT 4D · Strathmore University** · Supervisor: Mr. Nelson Ochieng

A web platform that links small-scale farmers directly to buyers and supports their decisions with machine learning:
**price prediction (regression)**, **demand forecasting (time series)** and a **recommendation engine**
(recommended price, best time to sell, best market, buyer–farmer matching). Administrators manage users,
approve farmers, remove listings, monitor activity and view reports.

```
┌──────────────────────────┐   REST /api   ┌───────────────────────────┐   JPA   ┌──────────────────┐
│ Angular 20 web app       │ ────────────▶ │ Spring Boot 3 backend     │ ──────▶ │ MySQL / MariaDB  │
│ Farmer · Buyer · Admin   │ ◀──────────── │ business logic, access    │         │ (XAMPP) fmls_db  │
│ http://localhost:4200    │  JSON results │ control, REST endpoints   │         └──────────────────┘
└──────────────────────────┘               │ http://localhost:8080     │
                                           └────────────┬──────────────┘
                                          send data for │ ▲ prediction results
                                             prediction ▼ │ & insights
                                           ┌───────────────────────────┐
                                           │ Python ML module (FastAPI)│
                                           │ price regression, demand  │
                                           │ time series, recommender  │
                                           │ http://localhost:8000     │
                                           └───────────────────────────┘
```

| Folder | What it is | Tech (Proposal §3.5) |
|---|---|---|
| `frontend/` | User Interface Module – all screens from the wireframes | Angular 20 (TypeScript) |
| `backend/` | Backend Processing & API Module, User Management, Listings, Market Access, Transactions, Admin | Spring Boot 3.3 (Java 17), REST |
| `ml-service/` | Machine Learning & Data Analysis Module | Python 3, scikit-learn, statsmodels, FastAPI |
| `database/` | Database Management Module – schema script | MySQL (XAMPP) |
| `docs/` | Traceability to the proposal & diagrams, API reference, test cases, ML evaluation | |
| `scripts/` | One-click start scripts | |

---

## 1. Install the tools (once)

| Tool | Version | Notes |
|---|---|---|
| **XAMPP** | any recent | Only **MySQL** needs to be started (Apache is not needed) |
| **Java JDK** | **17 or newer** | e.g. Eclipse Temurin 17/21. `java -version` must show 17+ |
| **Maven** | 3.9+ | *Optional* – `backend\mvnw.cmd` downloads Maven automatically. IntelliJ IDEA also works |
| **Python** | 3.10 – 3.13 | Tick "Add Python to PATH" during installation |
| **Node.js** | 20.19+ or 22 LTS | includes `npm` |

## 2. Start the database (XAMPP)

1. Open the **XAMPP Control Panel** → click **Start** next to **MySQL** (port 3306).
2. That's all – the backend creates the `fmls_db` database and its tables automatically and loads the
   simulated Kenyan market dataset (≈31,000 market prices, ≈5,900 historical sales, demo accounts) on first start.
   *(Optional: import `database/schema.sql` in phpMyAdmin to create the tables yourself.)*
3. The backend connects as `root` with an **empty password** (XAMPP default). If your MySQL `root` has a
   password, put it in `backend/src/main/resources/application.properties` → `spring.datasource.password=`.

## 3. Run everything

### Windows – quickest
1. Double-click **`scripts\setup-windows.bat`** once (installs Python and Node packages – needs internet).
2. Double-click **`scripts\start-windows.bat`** – three windows open (ML module, backend, frontend).
3. Wait until the backend window shows `Started FmlsApplication` (first start ≈ 1 min while data loads), then open
   **http://localhost:4200**.

### Manually (Windows / macOS / Linux) – three terminals

**Terminal 1 – ML module (port 8000)**
```bash
cd ml-service
pip install -r requirements.txt
python -m uvicorn app.main:app --port 8000
```
Trained models are included (`ml-service/models/`). If your scikit-learn version differs they are retrained automatically
on start-up (≈1 minute). To retrain manually and regenerate the evaluation report: `python train.py`.

**Terminal 2 – backend (port 8080)**
```bash
cd backend
mvnw.cmd spring-boot:run        # Windows   (or: mvn spring-boot:run)
./mvnw spring-boot:run          # macOS / Linux
```
Or open the `backend` folder in IntelliJ IDEA and run `FmlsApplication`.

**Terminal 3 – frontend (port 4200)**
```bash
cd frontend
npm install
npm start
```
Open **http://localhost:4200**.

## 4. Demo accounts

| Role | Log in with | Password |
|---|---|---|
| Farmer (Nakuru) | `0711000001` | `Farmer@123` |
| Buyer (Nairobi) | `0722000001` | `Buyer@123` |
| Administrator | `admin@fmls.co.ke` | `Admin@123` |
| Farmer awaiting approval | `0711000099` | `Farmer@123` |

The login page has a "Demo accounts" panel that fills these in. Other seeded farmers use phones
`0711000002`–`0711000030` and buyers `0722000002`–`0722000015` (same passwords).

## 5. Suggested demonstration (follows the activity & sequence diagrams)

1. **Farmer** → *List produce*: choose crop, quantity, harvest date and location – the "System-generated (preview)" box
   shows the estimated price and demand trend while typing.
2. *Submit listing* → **Price recommendation** view: recommended price per kg with confidence, 30-day price trend,
   demand forecast, advice, best time to sell and best markets. Choose **List at this price** or **Set own price**.
3. **Matched buyers** appear, ranked by the recommendation engine. Try listing an unknown crop (e.g. *Other → Macadamia*)
   to see the *insufficient historical data* path ("Limited data" – farmer sets own price).
4. **Buyer** → *Browse listings*: "Recommended for you", search/filter, open a listing, **Purchase** → "Purchase confirmed".
   Use *Message seller* to contact the farmer.
5. **Farmer** → *Sales*: mark the order completed (the sale is recorded as new market data).
6. **Admin** → approve the pending farmer (*Users*), remove a listing, view *Activity*, *Reports* (CSV export),
   *Market data* (add/import prices) and *ML models* (accuracy metrics, **Retrain on database data**).
7. Anyone → *Market insights*: price history + 4-week forecast with prediction range, weekly demand forecast,
   best markets and latest county prices.

## 6. Tests

```bash
cd ml-service && python -m pytest -q          # 14 ML/API tests (models, alt paths, matching, training)
cd backend    && mvnw.cmd test                # Spring Boot integration tests (in-memory H2 database)
```
Test cases (functional, integration, usability) with expected and actual results: `docs/TEST_CASES.md`.
ML evaluation: `ml-service/reports/model_evaluation.md`.

## 7. Troubleshooting

| Problem | Fix |
|---|---|
| Backend: `Communications link failure` | MySQL is not running – start it in XAMPP |
| Backend: `Access denied for user 'root'` | Set your MySQL password in `application.properties` |
| Backend: `Unsupported class file major version` / `release 17 not supported` | Install JDK 17+ and set `JAVA_HOME` to it |
| Port 8080 / 8000 / 4200 already in use | Close the other program, or change `server.port` (backend) / `--port` |
| Frontend shows "Cannot reach the server" | Start the backend; the frontend proxies `/api` to `http://localhost:8080` |
| Recommendation says "decision-support service offline" | Start the ML module (terminal 1). The backend falls back to a moving-average estimate until then |
| Want a fresh database | In phpMyAdmin drop `fmls_db`, then restart the backend (data is re-seeded) |

Seeded dates are shifted automatically so the latest market data is always in the current week, and a simulated
market feed adds prices for every Tuesday/Friday market day, so predictions work whenever you run the system.
