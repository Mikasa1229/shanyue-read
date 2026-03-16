@echo off
setlocal

echo.
echo ##########################################
echo #                                        #
echo #    ShanYueFang - Start All Services    #
echo #                                        #
echo ##########################################
echo.
echo Order: Middleware -> Backend -> Frontend
echo.

echo [Step 1/3] Starting Docker middleware...
call "%~dp0start-middleware.bat"
if %errorlevel% neq 0 (
    echo [ABORT] Middleware failed. Stopping.
    pause
    exit /b 1
)

echo [Step 2/3] Starting backend services...
call "%~dp0start-backend.bat"
if %errorlevel% neq 0 (
    echo [ABORT] Backend failed. Stopping.
    pause
    exit /b 1
)

echo [Step 3/3] Starting frontend...
call "%~dp0start-frontend.bat"

echo.
echo ==========================================
echo   All services started!
echo.
echo   Frontend  : http://localhost:3000
echo   API GW    : http://localhost:8080
echo   Nacos     : http://localhost:8848/nacos
echo   RabbitMQ  : http://localhost:15672
echo   MinIO     : http://localhost:9001
echo.
echo   Run check-status.bat to verify status.
echo   Run stop-all.bat    to stop everything.
echo ==========================================
echo.
pause
