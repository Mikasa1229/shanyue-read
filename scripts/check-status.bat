@echo off
setlocal enabledelayedexpansion

echo.
echo ============================================
echo   ShanYueFang - Service Status Check
echo ============================================
echo.

set "ALL_OK=1"

:: -- Docker containers ----------------------------------------
echo [Middleware - Docker containers]
call :check_docker "reader-postgres"   "PostgreSQL     :5432"
call :check_docker "reader-redis"      "Redis          :6379"
call :check_docker "reader-rabbitmq"   "RabbitMQ       :5672"
call :check_docker "reader-nacos"      "Nacos          :8848"
call :check_docker "reader-es"         "Elasticsearch  :9200"
call :check_docker "reader-minio"      "MinIO          :9000"

echo.
echo [Backend Services - port listening]
call :check_port 8080 "reader-gateway     :8080"
call :check_port 8081 "reader-user        :8081"
call :check_port 8082 "reader-novel       :8082"
call :check_port 8083 "reader-comment     :8083"
call :check_port 8084 "reader-interaction :8084"
call :check_port 8085 "reader-checkin     :8085"

echo.
echo [Frontend]
call :check_port 3000 "Vite dev server    :3000"

echo.
echo [HTTP Health Probes]
call :check_http "http://localhost:8848/nacos/v1/console/health/liveness" "Nacos  health"
call :check_http "http://localhost:8080/api/novels?page=1&size=1"         "GET /api/novels"
call :check_http "http://localhost:8080/api/auth/login"                   "POST /api/auth  (405=online)"

echo.
if "!ALL_OK!"=="1" (
    echo [RESULT] All services are running.
) else (
    echo [RESULT] Some services are DOWN. Check the corresponding console windows.
)
echo.
pause
goto :eof

:check_docker
docker ps --filter "name=%~1" --filter "status=running" --format "{{.Names}}" 2>nul | findstr /i "%~1" > nul 2>&1
if %errorlevel% == 0 (
    echo   [ UP ]  %~2
) else (
    echo   [DOWN]  %~2
    set "ALL_OK=0"
)
goto :eof

:check_port
netstat -an 2>nul | findstr ":%~1 " | findstr "LISTENING" > nul 2>&1
if %errorlevel% == 0 (
    echo   [ UP ]  %~2
) else (
    echo   [DOWN]  %~2
    set "ALL_OK=0"
)
goto :eof

:check_http
set "HTTP_CODE="
for /f %%C in ('curl -s -o nul -w "%%{http_code}" --connect-timeout 3 "%~1" 2^>nul') do set "HTTP_CODE=%%C"
if "!HTTP_CODE!"=="200" (
    echo   [ OK ]  %~2  (HTTP 200)
) else if "!HTTP_CODE!"=="405" (
    echo   [ OK ]  %~2  (HTTP 405 - service online)
) else if "!HTTP_CODE!"=="401" (
    echo   [ OK ]  %~2  (HTTP 401 - auth required, service online)
) else (
    echo   [FAIL]  %~2  (HTTP !HTTP_CODE!)
    set "ALL_OK=0"
)
goto :eof
