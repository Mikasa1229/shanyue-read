[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Root = Resolve-Path "$PSScriptRoot\.."

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   善阅坊 - 启动前端（Vite）" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "[错误] 未找到 node 命令，请安装 Node.js 18+ 并添加到 PATH。" -ForegroundColor Red
    exit 1
}

$frontendDir = "$Root\frontend"
Set-Location $frontendDir

if (-not (Test-Path "node_modules")) {
    Write-Host "[初始化] 首次运行，正在安装依赖（npm install）..." -ForegroundColor Yellow
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[错误] npm install 失败，请检查网络或 package.json。" -ForegroundColor Red
        exit 1
    }
    Write-Host "[完成] 依赖安装成功。" -ForegroundColor Green
    Write-Host ""
}

Write-Host "[启动] 正在启动 Vite 开发服务器..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/k title reader-frontend:3000 && cd /d `"$frontendDir`" && npm run dev"

Write-Host ""
Write-Host "[完成] 前端已启动。" -ForegroundColor Green
Write-Host "       访问地址：http://localhost:3000"
Write-Host ""
