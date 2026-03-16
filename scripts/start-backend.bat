@echo off
setlocal enabledelayedexpansion

set "ROOT=%~dp0.."
set "VER=1.0.0-SNAPSHOT"

echo.
echo ============================================
echo   ShanYueFang - Start Backend Services
echo ============================================
echo.

where java > nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] java not found. Please install JDK 17+ and add it to PATH.
    pause
    exit /b 1
)

set "DO_BUILD=N"
set /p "DO_BUILD=Build before starting? (Y=yes, N=skip, default N): "
if /i "!DO_BUILD!"=="Y" (
    call "%~dp0build-backend.bat"
    if !errorlevel! neq 0 exit /b 1
)

echo.
echo [START] Launching 6 microservices in separate windows...
echo.

:: -- Business services (register to Nacos first) -----------------

set "JAR=%ROOT%\backend\reader-user\target\reader-user-%VER%.jar"
if not exist "!JAR!" ( echo [ERROR] JAR not found: !JAR! & echo        Run build-backend.bat first. & pause & exit /b 1 )
start "reader-user   :8081" java -jar "!JAR!" --spring.profiles.active=dev
echo   [STARTED] reader-user        -> http://localhost:8081

set "JAR=%ROOT%\backend\reader-novel\target\reader-novel-%VER%.jar"
if not exist "!JAR!" ( echo [ERROR] JAR not found: !JAR! & pause & exit /b 1 )
start "reader-novel  :8082" java -jar "!JAR!" --spring.profiles.active=dev
echo   [STARTED] reader-novel       -> http://localhost:8082

set "JAR=%ROOT%\backend\reader-comment\target\reader-comment-%VER%.jar"
if not exist "!JAR!" ( echo [ERROR] JAR not found: !JAR! & pause & exit /b 1 )
start "reader-comment:8083" java -jar "!JAR!" --spring.profiles.active=dev
echo   [STARTED] reader-comment     -> http://localhost:8083

set "JAR=%ROOT%\backend\reader-interaction\target\reader-interaction-%VER%.jar"
if not exist "!JAR!" ( echo [ERROR] JAR not found: !JAR! & pause & exit /b 1 )
start "reader-interaction:8084" java -jar "!JAR!" --spring.profiles.active=dev
echo   [STARTED] reader-interaction -> http://localhost:8084

set "JAR=%ROOT%\backend\reader-checkin\target\reader-checkin-%VER%.jar"
if not exist "!JAR!" ( echo [ERROR] JAR not found: !JAR! & pause & exit /b 1 )
start "reader-checkin:8085" java -jar "!JAR!" --spring.profiles.active=dev
echo   [STARTED] reader-checkin     -> http://localhost:8085

:: -- Wait for services to register, then start Gateway -----------

echo.
echo [WAIT ] Waiting 15s for services to register with Nacos...
timeout /t 15 /nobreak > nul

set "JAR=%ROOT%\backend\reader-gateway\target\reader-gateway-%VER%.jar"
if not exist "!JAR!" ( echo [ERROR] JAR not found: !JAR! & pause & exit /b 1 )
start "reader-gateway:8080" java -jar "!JAR!" --spring.profiles.active=dev
echo   [STARTED] reader-gateway     -> http://localhost:8080

echo.
echo [DONE] All backend services launched in separate windows.
echo        Services are fully ready in ~30-60s.
echo        Run check-status.bat to verify.
echo.
