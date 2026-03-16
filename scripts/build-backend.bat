@echo off
setlocal

set "ROOT=%~dp0.."

echo.
echo =============================================
echo   善阅坊 - 构建后端 (Maven)
echo =============================================
echo.

:: 检查 mvn
where mvn > nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 mvn 命令，请安装 Maven 并配置 PATH
    pause
    exit /b 1
)

cd /d "%ROOT%\backend"

echo [构建] 编译所有模块（跳过测试）...
call mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo.
    echo [错误] Maven 构建失败，请查看上方错误信息
    pause
    exit /b 1
)

echo.
echo [完成] 后端构建成功，JAR 文件位置：
echo   backend\reader-gateway\target\reader-gateway-1.0.0-SNAPSHOT.jar
echo   backend\reader-user\target\reader-user-1.0.0-SNAPSHOT.jar
echo   backend\reader-novel\target\reader-novel-1.0.0-SNAPSHOT.jar
echo   backend\reader-comment\target\reader-comment-1.0.0-SNAPSHOT.jar
echo   backend\reader-interaction\target\reader-interaction-1.0.0-SNAPSHOT.jar
echo   backend\reader-checkin\target\reader-checkin-1.0.0-SNAPSHOT.jar
echo.
