@echo off
setlocal

set "ROOT=%~dp0.."

echo.
echo ============================================
echo   ShanYueFang - Start Frontend (Vite)
echo ============================================
echo.

where node > nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] node not found. Please install Node.js 18+ and add it to PATH.
    pause
    exit /b 1
)

cd /d "%ROOT%\frontend"

if not exist "node_modules" (
    echo [SETUP] node_modules not found. Running npm install...
    call npm install
    if %errorlevel% neq 0 (
        echo [ERROR] npm install failed.
        pause
        exit /b 1
    )
    echo [ OK ] Dependencies installed.
    echo.
)

echo [START] Launching Vite dev server...
start "reader-frontend:3000" cmd /k "cd /d "%ROOT%\frontend" && npm run dev"

echo.
echo [DONE] Frontend started.
echo        URL: http://localhost:3000
echo.
