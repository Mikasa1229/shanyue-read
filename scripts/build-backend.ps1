[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Root = Resolve-Path "$PSScriptRoot\.."

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   善阅坊 - 后端编译（Maven）" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[错误] 未找到 mvn 命令，请安装 Maven 并添加到 PATH。" -ForegroundColor Red
    exit 1
}

Set-Location "$Root\backend"

Write-Host "[编译] 执行：mvn clean package -DskipTests" -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[错误] Maven 编译失败，请查看上方输出。" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[完成] 编译成功！JAR 文件位置：" -ForegroundColor Green
Write-Host "  reader-gateway\target\reader-gateway-1.0.0-SNAPSHOT.jar"
Write-Host "  reader-user\target\reader-user-1.0.0-SNAPSHOT.jar"
Write-Host "  reader-novel\target\reader-novel-1.0.0-SNAPSHOT.jar"
Write-Host "  reader-comment\target\reader-comment-1.0.0-SNAPSHOT.jar"
Write-Host "  reader-interaction\target\reader-interaction-1.0.0-SNAPSHOT.jar"
Write-Host "  reader-checkin\target\reader-checkin-1.0.0-SNAPSHOT.jar"
Write-Host ""
