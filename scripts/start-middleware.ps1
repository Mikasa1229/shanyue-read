[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Root = Resolve-Path "$PSScriptRoot\.."

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   善阅坊 - 启动中间件（Docker）" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

docker info 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] Docker 未运行，请先启动 Docker Desktop。" -ForegroundColor Red
    exit 1
}

Set-Location "$Root\docker"

Write-Host "[1/2] 启动中间件容器..." -ForegroundColor Yellow
$composeArgs = @("-f", "docker-compose.yml", "up", "-d")
if (Test-Path -LiteralPath "$Root\.env") {
    $composeArgs = @("--env-file", "$Root\.env") + $composeArgs
    Write-Host "       已加载项目根目录 .env（敏感值不会显示）。" -ForegroundColor DarkGray
}
docker compose @composeArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] Docker Compose 启动失败，请检查 docker/docker-compose.yml。" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[2/2] 等待 Nacos 就绪（init-nacos 容器同步完成配置写入）..." -ForegroundColor Yellow
$elapsed = 0
while ($true) {
    Start-Sleep -Seconds 3
    $elapsed += 3
    try {
        $res = Invoke-WebRequest -Uri "http://localhost:8848/nacos/v1/console/health/liveness" `
            -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        if ($res.StatusCode -eq 200) {
            Write-Host "[就绪] Nacos 已启动（已等待 $elapsed 秒）" -ForegroundColor Green
            break
        }
    } catch {}
    if ($elapsed -ge 120) {
        Write-Host "[警告] Nacos 等待超时，请执行 docker ps 手动检查容器状态。" -ForegroundColor Yellow
        break
    }
    Write-Host "       等待中... $elapsed 秒"
}

Write-Host "[完成] 中间件已启动：" -ForegroundColor Green
Write-Host "  PostgreSQL    : localhost:5432"
Write-Host "  Redis         : localhost:6379"
Write-Host "  RabbitMQ      : localhost:5672  （管理界面：http://localhost:15672）"
Write-Host "  Nacos         : http://localhost:8848/nacos  （账号：nacos/nacos）"
Write-Host "  Elasticsearch : localhost:9200"
Write-Host "  MinIO         : http://localhost:9001"
Write-Host ""
