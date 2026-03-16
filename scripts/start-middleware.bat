@echo off
setlocal

set "ROOT=%~dp0.."

echo.
echo =============================================
echo   善阅坊 - 启动中间件 (Docker Compose)
echo =============================================
echo.

:: 检查 Docker 是否运行
docker info > nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] Docker 未运行，请先启动 Docker Desktop
    pause
    exit /b 1
)

cd /d "%ROOT%\docker"

echo [1/2] 启动中间件容器...
docker-compose up -d
if %errorlevel% neq 0 (
    echo [错误] docker-compose 启动失败，请检查 docker/docker-compose.yml
    pause
    exit /b 1
)

echo.
echo [2/2] 等待 Nacos 就绪 (约 30 秒)...
set /a "count=0"
:wait_nacos
timeout /t 3 /nobreak > nul
set /a "count+=3"
curl -s -o nul -w "%%{http_code}" http://localhost:8848/nacos/v1/console/health/liveness 2>nul | findstr "200" > nul
if %errorlevel% == 0 (
    echo [OK] Nacos 已就绪 (耗时 %count% 秒^)
    goto nacos_ready
)
if %count% geq 60 (
    echo [警告] Nacos 超时未响应，请检查容器状态: docker ps
    goto nacos_ready
)
echo     等待中... %count%s
goto wait_nacos

:nacos_ready
echo.
echo [完成] 中间件已全部启动
echo   PostgreSQL : localhost:5432
echo   Redis      : localhost:6379
echo   RabbitMQ   : localhost:5672  (管理界面 http://localhost:15672)
echo   Nacos      : http://localhost:8848/nacos  (账号 nacos/nacos)
echo   Elasticsearch : localhost:9200
echo   MinIO      : http://localhost:9001
echo.
