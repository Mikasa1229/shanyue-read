@echo off
setlocal

set "ROOT=%~dp0.."

echo.
echo ============================================
echo   ShanYueFang - Build Backend (Maven)
echo ============================================
echo.

where mvn > nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] mvn not found. Please install Maven and add it to PATH.
    pause
    exit /b 1
)

cd /d "%ROOT%\backend"

echo [BUILD] Running: mvn clean package -DskipTests
call mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Maven build failed. See output above.
    pause
    exit /b 1
)

echo.
echo [DONE] Build successful. JAR locations:
echo   reader-gateway\target\reader-gateway-1.0.0-SNAPSHOT.jar
echo   reader-user\target\reader-user-1.0.0-SNAPSHOT.jar
echo   reader-novel\target\reader-novel-1.0.0-SNAPSHOT.jar
echo   reader-comment\target\reader-comment-1.0.0-SNAPSHOT.jar
echo   reader-interaction\target\reader-interaction-1.0.0-SNAPSHOT.jar
echo   reader-checkin\target\reader-checkin-1.0.0-SNAPSHOT.jar
echo.
