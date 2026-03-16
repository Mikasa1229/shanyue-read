@echo off
setlocal

set "ROOT=%~dp0.."

echo.
echo ============================================
echo   ShanYueFang - Start Middleware (Docker)
echo ============================================
echo.

docker info > nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not running. Please start Docker Desktop first.
    pause
    exit /b 1
)

cd /d "%ROOT%\docker"

echo [1/2] Starting middleware containers...
docker-compose up -d
if %errorlevel% neq 0 (
    echo [ERROR] docker-compose failed. Check docker/docker-compose.yml
    pause
    exit /b 1
)

echo.
echo [2/2] Waiting for Nacos to be ready...
set /a "elapsed=0"

:wait_nacos
timeout /t 3 /nobreak > nul
set /a "elapsed+=3"
curl -s -o nul -w "%%{http_code}" http://localhost:8848/nacos/v1/console/health/liveness 2>nul | findstr "200" > nul
if %errorlevel% == 0 (
    echo [ OK ] Nacos is ready  (elapsed: %elapsed%s)
    goto nacos_ready
)
if %elapsed% geq 60 (
    echo [WARN] Nacos timed out. Check containers: docker ps
    goto nacos_ready
)
echo        Waiting... %elapsed%s
goto wait_nacos

:nacos_ready
echo.
echo [DONE] Middleware is up:
echo   PostgreSQL    : localhost:5432
echo   Redis         : localhost:6379
echo   RabbitMQ      : localhost:5672  (UI: http://localhost:15672)
echo   Nacos         : http://localhost:8848/nacos  (nacos/nacos)
echo   Elasticsearch : localhost:9200
echo   MinIO         : http://localhost:9001
echo.
