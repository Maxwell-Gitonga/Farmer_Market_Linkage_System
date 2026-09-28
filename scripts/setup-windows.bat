@echo off
REM One-time setup: installs the Python and Node packages (needs internet).
cd /d "%~dp0.."
echo === Python packages (ML module) ===
python -m pip install -r ml-service\requirements.txt || goto :fail
echo.
echo === Node packages (Angular frontend) ===
cd frontend
call npm install || goto :fail
cd ..
echo.
echo Setup complete. Start MySQL in XAMPP, then run scripts\start-windows.bat
pause
exit /b 0
:fail
echo Setup failed - check the messages above (is Python/Node installed and on PATH?)
pause
exit /b 1
