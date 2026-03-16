[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$NacosUrl = "http://localhost:8848/nacos/v1/cs/configs"
$Group    = "reader"

Write-Host ""
Write-Host "  [Nacos] 开始初始化 Nacos 配置..." -ForegroundColor Cyan

$configs = @{

    "reader-gateway.yaml" = @"
# ===== reader-gateway 集中配置 =====
# 可在此覆盖路由、限流、跨域等网关配置，无需重新打包
logging:
  level:
    com.shanyuefang: debug
    org.springframework.cloud.gateway: info
"@

    "reader-user.yaml" = @"
# ===== reader-user 集中配置 =====
# 可在此覆盖数据库连接、缓存TTL、Sa-Token等配置，无需重新打包
# 示例（按需取消注释）：
# spring:
#   datasource:
#     url: jdbc:postgresql://prod-host:5432/db_user
logging:
  level:
    com.shanyuefang: debug
"@

    "reader-novel.yaml" = @"
# ===== reader-novel 集中配置 =====
# 可在此覆盖小说服务的数据库、缓存、MQ 等配置
logging:
  level:
    com.shanyuefang: debug
"@

    "reader-comment.yaml" = @"
# ===== reader-comment 集中配置 =====
# 可在此覆盖点评服务的数据库、Feign 超时等配置
logging:
  level:
    com.shanyuefang: debug
"@

    "reader-interaction.yaml" = @"
# ===== reader-interaction 集中配置 =====
# 可在此覆盖互动服务的数据库、缓存TTL等配置
logging:
  level:
    com.shanyuefang: debug
"@

    "reader-checkin.yaml" = @"
# ===== reader-checkin 集中配置 =====
# 可在此覆盖打卡服务的数据库、Bitmap TTL等配置
logging:
  level:
    com.shanyuefang: debug
"@
}

$success = 0
$failed  = 0

foreach ($dataId in $configs.Keys) {
    $content = $configs[$dataId]
    try {
        $encoded = "dataId=$([uri]::EscapeDataString($dataId))" +
                   "&group=$([uri]::EscapeDataString($Group))" +
                   "&content=$([uri]::EscapeDataString($content))" +
                   "&type=yaml"
        $res = Invoke-WebRequest -Uri $NacosUrl -Method Post `
            -Body $encoded `
            -ContentType "application/x-www-form-urlencoded" `
            -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop

        if ($res.Content -eq "true") {
            Write-Host "    [OK] $dataId  (group: $Group)" -ForegroundColor Green
            $success++
        } else {
            Write-Host "    [警告] $dataId  返回: $($res.Content)" -ForegroundColor Yellow
            $failed++
        }
    } catch {
        Write-Host "    [失败] $dataId  错误: $($_.Exception.Message)" -ForegroundColor Red
        $failed++
    }
}

Write-Host ""
if ($failed -eq 0) {
    Write-Host "  [Nacos] 配置初始化完成：$success 个配置已写入 group=$Group" -ForegroundColor Green
} else {
    Write-Host "  [Nacos] 部分配置写入失败（成功 $success / 失败 $failed），请检查 Nacos 是否正常运行。" -ForegroundColor Yellow
}
Write-Host ""
