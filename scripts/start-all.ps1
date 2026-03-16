[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host ""
Write-Host "##########################################" -ForegroundColor Cyan
Write-Host "#                                        #" -ForegroundColor Cyan
Write-Host "#       善阅坊 - 一键启动所有服务        #" -ForegroundColor Cyan
Write-Host "#                                        #" -ForegroundColor Cyan
Write-Host "##########################################" -ForegroundColor Cyan
Write-Host ""
Write-Host "启动顺序：中间件 -> 后端 -> 前端" -ForegroundColor Yellow
Write-Host ""

Write-Host "[第1步/共3步] 启动 Docker 中间件..." -ForegroundColor Yellow
& powershell.exe -ExecutionPolicy Bypass -NoProfile -File "$PSScriptRoot\start-middleware.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[中止] 中间件启动失败，已停止。" -ForegroundColor Red
    exit 1
}

Write-Host "[第2步/共3步] 启动后端服务..." -ForegroundColor Yellow
& powershell.exe -ExecutionPolicy Bypass -NoProfile -File "$PSScriptRoot\start-backend.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[中止] 后端启动失败，已停止。" -ForegroundColor Red
    exit 1
}

Write-Host "[第3步/共3步] 启动前端..." -ForegroundColor Yellow
& powershell.exe -ExecutionPolicy Bypass -NoProfile -File "$PSScriptRoot\start-frontend.ps1"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "   所有服务已启动！" -ForegroundColor Green
Write-Host ""
Write-Host "   前端地址    : http://localhost:3000"
Write-Host "   API 网关    : http://localhost:8080"
Write-Host "   Nacos 控制台: http://localhost:8848/nacos"
Write-Host "   RabbitMQ    : http://localhost:15672"
Write-Host "   MinIO       : http://localhost:9001"
Write-Host ""
Write-Host "   运行 check-status.bat 查看服务状态"
Write-Host "   运行 stop-all.bat     停止所有服务"
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
