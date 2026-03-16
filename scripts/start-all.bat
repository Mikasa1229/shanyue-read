@echo off
setlocal

echo.
echo ########################################
echo #                                      #
echo #        善阅坊  一键启动              #
echo #                                      #
echo ########################################
echo.
echo 启动顺序：中间件 -> 后端服务 -> 前端
echo.

:: ─── 步骤 1：中间件 ───────────────────────────────────────
echo [步骤 1/3] 启动 Docker 中间件...
call "%~dp0start-middleware.bat"
if %errorlevel% neq 0 (
    echo [中止] 中间件启动失败，已停止
    pause
    exit /b 1
)

:: ─── 步骤 2：后端服务 ─────────────────────────────────────
echo [步骤 2/3] 启动后端服务...
call "%~dp0start-backend.bat"
if %errorlevel% neq 0 (
    echo [中止] 后端启动失败，已停止
    pause
    exit /b 1
)

:: ─── 步骤 3：前端 ─────────────────────────────────────────
echo [步骤 3/3] 启动前端...
call "%~dp0start-frontend.bat"

echo.
echo ========================================
echo   全部服务已启动！
echo.
echo   前端界面  : http://localhost:3000
echo   API 网关  : http://localhost:8080
echo   Nacos     : http://localhost:8848/nacos
echo   RabbitMQ  : http://localhost:15672
echo   MinIO     : http://localhost:9001
echo.
echo   运行 check-status.bat 检查各服务状态
echo   运行 stop-all.bat     停止所有服务
echo ========================================
echo.
pause
