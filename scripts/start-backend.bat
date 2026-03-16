@echo off
setlocal

set "ROOT=%~dp0.."
set "JAR_VER=1.0.0-SNAPSHOT"

echo.
echo =============================================
echo   善阅坊 - 启动后端服务
echo =============================================
echo.

:: 检查 Java
where java > nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 java 命令，请安装 JDK 17+ 并配置 PATH
    pause
    exit /b 1
)

:: 询问是否重新构建
set "DO_BUILD=N"
set /p "DO_BUILD=是否重新构建？(如首次运行请输入 Y) [Y/N，默认 N]: "
if /i "%DO_BUILD%"=="Y" (
    call "%~dp0build-backend.bat"
    if %errorlevel% neq 0 exit /b 1
)

echo.
echo [启动] 正在启动 6 个微服务（各自独立窗口）...
echo.

:: ── 业务服务（先启动，向 Nacos 注册）────────────────────────

set "JAR=%ROOT%\backend\reader-user\target\reader-user-%JAR_VER%.jar"
if not exist "%JAR%" ( echo [错误] JAR 不存在: %JAR%，请先构建 & pause & exit /b 1 )
start "reader-user  :8081" java -jar "%JAR%" --spring.profiles.active=dev
echo   [启动] reader-user   -> http://localhost:8081

set "JAR=%ROOT%\backend\reader-novel\target\reader-novel-%JAR_VER%.jar"
if not exist "%JAR%" ( echo [错误] JAR 不存在: %JAR%，请先构建 & pause & exit /b 1 )
start "reader-novel :8082" java -jar "%JAR%" --spring.profiles.active=dev
echo   [启动] reader-novel  -> http://localhost:8082

set "JAR=%ROOT%\backend\reader-comment\target\reader-comment-%JAR_VER%.jar"
if not exist "%JAR%" ( echo [错误] JAR 不存在: %JAR%，请先构建 & pause & exit /b 1 )
start "reader-comment:8083" java -jar "%JAR%" --spring.profiles.active=dev
echo   [启动] reader-comment -> http://localhost:8083

set "JAR=%ROOT%\backend\reader-interaction\target\reader-interaction-%JAR_VER%.jar"
if not exist "%JAR%" ( echo [错误] JAR 不存在: %JAR%，请先构建 & pause & exit /b 1 )
start "reader-interaction:8084" java -jar "%JAR%" --spring.profiles.active=dev
echo   [启动] reader-interaction -> http://localhost:8084

set "JAR=%ROOT%\backend\reader-checkin\target\reader-checkin-%JAR_VER%.jar"
if not exist "%JAR%" ( echo [错误] JAR 不存在: %JAR%，请先构建 & pause & exit /b 1 )
start "reader-checkin:8085" java -jar "%JAR%" --spring.profiles.active=dev
echo   [启动] reader-checkin -> http://localhost:8085

:: ── 等待业务服务注册后再启动 Gateway ────────────────────────
echo.
echo [等待] 业务服务注册到 Nacos（15 秒）...
timeout /t 15 /nobreak > nul

set "JAR=%ROOT%\backend\reader-gateway\target\reader-gateway-%JAR_VER%.jar"
if not exist "%JAR%" ( echo [错误] JAR 不存在: %JAR%，请先构建 & pause & exit /b 1 )
start "reader-gateway:8080" java -jar "%JAR%" --spring.profiles.active=dev
echo   [启动] reader-gateway -> http://localhost:8080

echo.
echo [完成] 所有后端服务已在独立窗口中启动
echo   服务完全就绪通常需要 30-60 秒，可运行 check-status.bat 查看
echo.
