@echo off
setlocal

echo.
echo =============================================
echo   善阅坊 - 停止所有服务
echo =============================================
echo.

:: ─── 停止后端 Java 进程（按端口）────────────────────────────
echo [1/3] 停止后端 Java 服务...

for %%P in (8080 8081 8082 8083 8084 8085) do (
    call :kill_port %%P
)

:: ─── 停止前端（Node.js vite）────────────────────────────────
echo.
echo [2/3] 停止前端开发服务器...
call :kill_port 3000

:: 同时关闭以 reader- 开头的控制台窗口
taskkill /FI "WINDOWTITLE eq reader-gateway:8080" /F > nul 2>&1
taskkill /FI "WINDOWTITLE eq reader-user  :8081"  /F > nul 2>&1
taskkill /FI "WINDOWTITLE eq reader-novel :8082"  /F > nul 2>&1
taskkill /FI "WINDOWTITLE eq reader-comment:8083" /F > nul 2>&1
taskkill /FI "WINDOWTITLE eq reader-interaction:8084" /F > nul 2>&1
taskkill /FI "WINDOWTITLE eq reader-checkin:8085" /F > nul 2>&1
taskkill /FI "WINDOWTITLE eq reader-frontend:3000" /F > nul 2>&1

:: ─── 可选：停止 Docker 中间件 ────────────────────────────────
echo.
echo [3/3] 是否同时停止 Docker 中间件？(中间件停止后数据不会丢失)
set "STOP_DOCKER=N"
set /p "STOP_DOCKER=停止中间件容器？[Y/N，默认 N]: "

if /i "%STOP_DOCKER%"=="Y" (
    echo     正在停止 Docker 容器...
    cd /d "%~dp0\..\docker"
    docker-compose stop
    echo     [ OK ] Docker 中间件已停止（数据已保留）
) else (
    echo     跳过，Docker 中间件保持运行
)

echo.
echo [完成] 所有选定服务已停止
echo.
pause
goto :eof

:: ─── 子程序：按端口杀进程 ────────────────────────────────────
:kill_port
set "PORT=%~1"
set "KILLED=0"
for /f "tokens=5" %%I in ('netstat -ano 2^>nul ^| findstr ":%PORT% " ^| findstr "LISTENING"') do (
    if "%%I" neq "0" (
        taskkill /F /PID %%I > nul 2>&1
        if !errorlevel! == 0 (
            echo   [ OK ] 端口 %PORT% 的进程 (PID %%I^) 已终止
            set "KILLED=1"
        )
    )
)
if "%KILLED%"=="0" echo   [跳过] 端口 %PORT% 无运行中的进程
goto :eof
