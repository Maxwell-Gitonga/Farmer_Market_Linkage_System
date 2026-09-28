@echo off
REM Starts the three parts of the system in separate windows. Start MySQL in XAMPP first.
cd /d "%~dp0.."
start "FMLS - ML module (8000)" cmd /k "cd /d %cd%\ml-service && python -m uvicorn app.main:app --port 8000"
start "FMLS - Backend (8080)" cmd /k "cd /d %cd%\backend && mvnw.cmd spring-boot:run"
start "FMLS - Frontend (4200)" cmd /k "cd /d %cd%\frontend && npm start"
echo.
echo Three windows are starting. When the backend window shows "Started FmlsApplication",
echo open http://localhost:4200   (first start takes about a minute while the dataset loads)
timeout /t 60 >nul
start "" http://localhost:4200
