# Frontend – User Interface Module (Angular 20)

Screens follow the wireframes: Login/Register, Farmer dashboard, Product listing form (with system-generated preview),
Price recommendation detail view, Matched buyers, Buyer "Browse listings", listing detail & purchase, Market insights,
Messages, Sales/Purchases, and the Admin dashboard (users, listings, activity, reports, market data, ML models).

```bash
npm install
npm start        # http://localhost:4200 – /api is proxied to the Spring Boot backend on :8080 (proxy.conf.json)
npm run build    # production build in dist/
```
Structure: `src/app/core` (API client, auth, guards, models), `src/app/shared` (SVG charts, pipes, badges),
`src/app/layout` (navigation shell), `src/app/pages/{auth,farmer,buyer,admin,common}`.
