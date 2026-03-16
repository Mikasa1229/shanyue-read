@echo off
setlocal

set "ROOT=%~dp0.."

echo.
echo =============================================
echo   善阅坊 - 启动前端开发服务器
echo =============================================
echo.

:: 检查 Node.js
where node > nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 node 命令，请安装 Node.js 18+
    pause
    exit /b 1
)

cd /d "%ROOT%\frontend"

:: 自动安装依赖
if not exist "node_modules" (
    echo [安装] 未检测到 node_modules，自动安装依赖...
    call npm install
    if %errorlevel% neq 0 (
        echo [错误] npm install 失败
        pause
        exit /b 1
    )
)

echo [启动] 启动 Vite 开发服务器...
start "reader-frontend:3000" cmd /k "cd /d "%ROOT%\frontend" && npm run dev"

echo.
echo [完成] 前端服务已启动
echo   访问地址: http://localhost:3000
echo.
