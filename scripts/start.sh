#!/usr/bin/env bash
# Starts ML module, backend and frontend (macOS / Linux). Make sure MySQL is running first.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
(cd "$ROOT/ml-service" && python3 -m uvicorn app.main:app --port 8000) &
(cd "$ROOT/backend" && ./mvnw spring-boot:run) &
(cd "$ROOT/frontend" && npm start) &
echo "Starting… open http://localhost:4200 once the backend prints 'Started FmlsApplication'. Ctrl+C stops all."
trap 'kill 0' INT TERM
wait
