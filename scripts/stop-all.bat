@echo off
setlocal enabledelayedexpansion

echo.
echo ============================================
echo   ShanYueFang - Stop All Services
echo ============================================
echo.

:: -- Kill backend Java processes by port -----------------------
echo [1/3] Stopping backend services (ports 8080-8085)...
for %%P in (8080 8081 8082 8083 8084 8085) do (
    call :kill_port %%P
)

:: -- Kill frontend (Node/Vite) ---------------------------------
echo.
echo [2/3] Stopping frontend (port 3000)...
call :kill_port 3000

:: Also close named console windows opened by our start scripts
for %%W in (
    "reader-gateway:8080"
    "reader-user   :8081"
    "reader-novel  :8082"
    "reader-comment:8083"
    "reader-interaction:8084"
    "reader-checkin:8085"
    "reader-frontend:3000"
) do (
    taskkill /FI "WINDOWTITLE eq %%~W" /F > nul 2>&1
)

:: -- Optional: stop Docker middleware --------------------------
echo.
echo [3/3] Stop Docker middleware containers?
echo       (Data is preserved when containers are stopped)
set "STOP_DOCKER=N"
set /p "STOP_DOCKER=Stop middleware? [Y/N, default N]: "

if /i "!STOP_DOCKER!"=="Y" (
    echo        Stopping Docker containers...
    cd /d "%~dp0\..\docker"
    docker-compose stop
    echo   [ OK ] Docker middleware stopped (data preserved).
) else (
    echo        Skipped. Docker middleware keeps running.
)

echo.
echo [DONE] Selected services have been stopped.
echo.
pause
goto :eof

:: -- Subroutine: kill process listening on a given port --------
:kill_port
set "PORT=%~1"
set "FOUND=0"
for /f "tokens=5" %%I in ('netstat -ano 2^>nul ^| findstr ":%PORT% " ^| findstr "LISTENING"') do (
    if "%%I" neq "0" (
        taskkill /F /PID %%I > nul 2>&1
        if !errorlevel! == 0 (
            echo   [ OK ] Port %PORT%  -  PID %%I terminated.
            set "FOUND=1"
        )
    )
)
if "!FOUND!"=="0" echo   [SKIP] Port %PORT%  -  no process found.
goto :eof
