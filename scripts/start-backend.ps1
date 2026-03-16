[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Root = Resolve-Path "$PSScriptRoot\.."
$Ver  = "1.0.0-SNAPSHOT"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   善阅坊 - 启动后端微服务" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[错误] 未找到 java 命令，请安装 JDK 17+ 并添加到 PATH。" -ForegroundColor Red
    exit 1
}

$doBuild = Read-Host "是否先编译？（Y=编译后启动，直接回车=跳过）"
if ($doBuild -eq "Y" -or $doBuild -eq "y") {
    & powershell.exe -ExecutionPolicy Bypass -NoProfile -File "$PSScriptRoot\build-backend.ps1"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

# 启动一个微服务的辅助函数
function Start-Service($name, $port, $jar) {
    $jarPath = "$Root\backend\$jar\target\$jar-$Ver.jar"
    if (-not (Test-Path $jarPath)) {
        Write-Host "[错误] 找不到 JAR：$jarPath" -ForegroundColor Red
        Write-Host "       请先运行 build-backend.bat" -ForegroundColor Yellow
        exit 1
    }
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/k title $name`:$port && java -jar `"$jarPath`" --spring.profiles.active=dev"
    Write-Host "  [已启动] $name -> http://localhost:$port" -ForegroundColor Green
}

Write-Host ""
Write-Host "[启动] 正在打开独立窗口启动各微服务..." -ForegroundColor Yellow
Write-Host ""

Start-Service "reader-user"        8081 "reader-user"
Start-Service "reader-novel"       8082 "reader-novel"
Start-Service "reader-comment"     8083 "reader-comment"
Start-Service "reader-interaction" 8084 "reader-interaction"
Start-Service "reader-checkin"     8085 "reader-checkin"

Write-Host ""
Write-Host "[等待] 等待 15 秒，让各服务完成 Nacos 注册..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

Start-Service "reader-gateway" 8080 "reader-gateway"

Write-Host ""
Write-Host "[完成] 所有后端服务已在独立窗口中启动。" -ForegroundColor Green
Write-Host "       服务完全就绪约需 30~60 秒。"
Write-Host "       可运行 check-status.bat 查看状态。"
Write-Host ""
