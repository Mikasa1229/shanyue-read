@echo off
setlocal enabledelayedexpansion

echo.
echo =============================================
echo   善阅坊 - 服务状态检查
echo =============================================
echo.

set "OK=[ OK ]"
set "DOWN=[DOWN]"
set "ALL_OK=1"

:: ─── 检查函数（通过端口判断） ─────────────────────────────

:: 中间件：Docker 容器
echo --- 中间件 (Docker) ---
call :check_docker "reader-postgres"  "PostgreSQL  :5432"
call :check_docker "reader-redis"     "Redis       :6379"
call :check_docker "reader-rabbitmq"  "RabbitMQ    :5672"
call :check_docker "reader-nacos"     "Nacos       :8848"
call :check_docker "reader-es"        "Elasticsearch:9200"
call :check_docker "reader-minio"     "MinIO       :9000"

echo.
echo --- 后端服务 ---
call :check_port 8080 "Gateway     :8080"
call :check_port 8081 "User        :8081"
call :check_port 8082 "Novel       :8082"
call :check_port 8083 "Comment     :8083"
call :check_port 8084 "Interaction :8084"
call :check_port 8085 "Checkin     :8085"

echo.
echo --- 前端 ---
call :check_port 3000 "Frontend    :3000"

echo.
echo --- API 接口探测 ---
call :check_http "http://localhost:8080/api/novels?page=1&size=1"  "GET /api/novels"
call :check_http "http://localhost:8848/nacos/v1/console/health/liveness" "Nacos Health"

echo.
if "%ALL_OK%"=="1" (
    echo [结果] 所有服务运行正常
) else (
    echo [结果] 部分服务未启动，请检查对应窗口的日志
)
echo.
pause
goto :eof

:: ─── 子程序：检查 Docker 容器 ──────────────────────────────
:check_docker
docker ps --filter "name=%~1" --filter "status=running" --format "{{.Names}}" 2>nul | findstr /i "%~1" > nul 2>&1
if %errorlevel% == 0 (
    echo   %OK%  %~2
) else (
    echo   %DOWN%  %~2
    set "ALL_OK=0"
)
goto :eof

:: ─── 子程序：检查端口是否监听 ─────────────────────────────
:check_port
netstat -an 2>nul | findstr ":%~1 " | findstr "LISTENING" > nul 2>&1
if %errorlevel% == 0 (
    echo   %OK%  %~2
) else (
    echo   %DOWN%  %~2
    set "ALL_OK=0"
)
goto :eof

:: ─── 子程序：HTTP 探测 ────────────────────────────────────
:check_http
for /f %%C in ('curl -s -o nul -w "%%{http_code}" --connect-timeout 3 "%~1" 2^>nul') do set "CODE=%%C"
if "%CODE%"=="200" (
    echo   %OK%  %~2  (HTTP 200)
) else if "%CODE%"=="401" (
    echo   %OK%  %~2  (HTTP 401 - 服务在线，需登录)
) else (
    echo   %DOWN%  %~2  (HTTP %CODE%)
    set "ALL_OK=0"
)
goto :eof
