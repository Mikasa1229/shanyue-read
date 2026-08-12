[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   善阅坊 - 停止所有服务" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

function Kill-Port($port) {
    $lines = netstat -ano 2>$null | Select-String ":$port " | Select-String "LISTENING"
    if (-not $lines) {
        Write-Host "  [跳过]   端口 $port  - 无进程占用"
        return
    }
    foreach ($line in $lines) {
        $parts = ($line.ToString().Trim() -split '\s+')
        $processId = $parts[-1]
        if ($processId -ne "0") {
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            Write-Host "  [已终止] 端口 $port  - PID $processId" -ForegroundColor Green
        }
    }
}

Write-Host "[1/3] 停止后端服务（端口 8080-8086）..." -ForegroundColor Yellow
foreach ($port in 8080, 8081, 8082, 8083, 8084, 8085, 8086) {
    Kill-Port $port
}

Write-Host ""
Write-Host "[2/3] 停止前端（端口 3000）..." -ForegroundColor Yellow
Kill-Port 3000

Write-Host ""
Write-Host "[3/3] 是否停止 Docker 中间件容器？" -ForegroundColor Yellow
Write-Host "      （停止后数据会保留，下次可直接重启）"
$stopDocker = Read-Host "停止中间件？（Y=停止，直接回车=保持运行）"

if ($stopDocker -eq "Y" -or $stopDocker -eq "y") {
    Write-Host "      正在停止 Docker 容器..." -ForegroundColor Yellow
    $dockerDir = Resolve-Path "$PSScriptRoot\..\docker"
    Set-Location $dockerDir
    docker-compose stop
    Write-Host "  [完成] Docker 中间件已停止（数据已保留）。" -ForegroundColor Green
} else {
    Write-Host "      已跳过，Docker 中间件继续运行。"
}

Write-Host ""
Write-Host "[完成] 已停止所选服务。" -ForegroundColor Green
Write-Host ""
