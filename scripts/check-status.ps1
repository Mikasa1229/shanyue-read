[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   善阅坊 - 服务状态检查" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

$allOk = $true

function Check-Docker($containerName, $label) {
    $running = docker ps --filter "name=$containerName" --filter "status=running" --format "{{.Names}}" 2>$null
    if ($running -match $containerName) {
        Write-Host "  [ 正常 ]  $label" -ForegroundColor Green
    } else {
        Write-Host "  [ 停止 ]  $label" -ForegroundColor Red
        $script:allOk = $false
    }
}

function Check-Port($port, $label) {
    $listening = netstat -an 2>$null | Select-String ":$port " | Select-String "LISTENING"
    if ($listening) {
        Write-Host "  [ 正常 ]  $label" -ForegroundColor Green
    } else {
        Write-Host "  [ 停止 ]  $label" -ForegroundColor Red
        $script:allOk = $false
    }
}

function Check-Http($url, $label) {
    try {
        $res = Invoke-WebRequest -Uri $url -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        Write-Host "  [  OK  ]  $label  （HTTP $($res.StatusCode)）" -ForegroundColor Green
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 401 -or $code -eq 405) {
            Write-Host "  [  OK  ]  $label  （HTTP $code - 服务在线）" -ForegroundColor Green
        } else {
            Write-Host "  [ 失败 ]  $label  （HTTP $code）" -ForegroundColor Red
            $script:allOk = $false
        }
    }
}

Write-Host "[中间件 - Docker 容器]"
Check-Docker "reader-postgres"  "PostgreSQL     :5432"
Check-Docker "reader-redis"     "Redis          :6379"
Check-Docker "reader-rabbitmq"  "RabbitMQ       :5672"
Check-Docker "reader-nacos"     "Nacos          :8848"
Check-Docker "reader-es"        "Elasticsearch  :9200"
Check-Docker "reader-minio"     "MinIO          :9000"
Check-Docker "reader-milvus"    "Milvus         :19530"
Check-Docker "reader-neo4j"     "Neo4j          :7687"
Check-Docker "reader-prometheus" "Prometheus     :9090"
Check-Docker "reader-jaeger"     "Jaeger         :16686"
Check-Docker "reader-otel-collector" "OTLP Collector :4318"

Write-Host ""
Write-Host "[后端服务 - 端口监听]"
Check-Port 8080 "网关 reader-gateway     :8080"
Check-Port 8081 "用户 reader-user        :8081"
Check-Port 8082 "小说 reader-novel       :8082"
Check-Port 8083 "评论 reader-comment     :8083"
Check-Port 8084 "互动 reader-interaction :8084"
Check-Port 8085 "打卡 reader-checkin     :8085"
Check-Port 8086 "Agent reader-agent       :8086"
Check-Port 9090 "Prometheus                 :9090"
Check-Port 16686 "Jaeger UI                  :16686"
Check-Port 4318 "OTLP HTTP collector        :4318"

Write-Host ""
Write-Host "[前端]"
Check-Port 3000 "Vite 开发服务器         :3000"

Write-Host ""
Write-Host "[HTTP 接口探测]"
Check-Http "http://localhost:8848/nacos/v1/console/health/liveness" "Nacos 健康检查"
Check-Http "http://localhost:9090/-/ready"                            "Prometheus 健康检查"
Check-Http "http://localhost:16686/"                                  "Jaeger UI"
Check-Http "http://localhost:8080/api/novels?page=1&size=1"         "GET /api/novels"
Check-Http "http://localhost:8080/api/auth"                        "GET /api/auth（401=在线）"

Write-Host ""
if ($allOk) {
    Write-Host "[结果] 所有服务运行正常。" -ForegroundColor Green
} else {
    Write-Host "[结果] 部分服务未启动，请检查对应的窗口日志。" -ForegroundColor Yellow
}
Write-Host ""
