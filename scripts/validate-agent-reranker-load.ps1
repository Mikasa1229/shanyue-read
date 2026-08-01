param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$AgentMetricsBaseUrl = "http://localhost:8086",
    [long]$UserId = 1,
    [string]$AccessToken = $env:AGENT_VALIDATION_ACCESS_TOKEN,
    [long]$CanonicalBookId = 1,
    [int]$CurrentChapter = 0,
    [int]$Requests = 50,
    [int]$Concurrency = 5
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($AccessToken)) { throw "AccessToken is required (pass it explicitly or set AGENT_VALIDATION_ACCESS_TOKEN)." }
if ($Requests -lt 1 -or $Concurrency -lt 1) { throw "Requests and Concurrency must be positive." }

$headers = @{ Authorization = "Bearer $AccessToken"; "X-User-Id" = "$UserId"; "Content-Type" = "application/json" }
$payload = @{ content = "Summarize visible character relationships without spoilers."; canonicalBookId = $CanonicalBookId; currentChapter = $CurrentChapter } | ConvertTo-Json -Compress

function Get-CounterSnapshot {
    param([string]$BaseUrl)
    try {
        $response = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/prometheus" -Headers @{ Accept = "text/plain" }
        $values = @{}
        foreach ($line in ($response -split "`n")) {
            if ($line -match '^reader_agent_reranker_requests_total\{outcome="([^"]+)"\}\s+([0-9.Ee+-]+)') { $values[$matches[1]] = [double]$matches[2] }
        }
        return $values
    } catch { return @{} }
}

$before = Get-CounterSnapshot $AgentMetricsBaseUrl
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
1..$Requests | ForEach-Object {
    Start-Job -ScriptBlock {
        param($url, $requestHeaders, $body)
        $timer = [System.Diagnostics.Stopwatch]::StartNew()
        $sessionId = $null
        try {
            $session = Invoke-RestMethod -Method Post -Uri "$url/api/agent/sessions" -Headers $requestHeaders -Body (@{ title = "validation"; context = "" } | ConvertTo-Json -Compress) -TimeoutSec 30
            $sessionId = $session.data.id
            Invoke-WebRequest -Method Post -Uri "$url/api/agent/sessions/$sessionId/messages:stream" -Headers $requestHeaders -Body $body -TimeoutSec 90 | Out-Null
            [pscustomobject]@{ Success = $true; Milliseconds = $timer.Elapsed.TotalMilliseconds }
        } catch {
            [pscustomobject]@{ Success = $false; Milliseconds = $timer.Elapsed.TotalMilliseconds }
        } finally {
            if ($null -ne $sessionId) { try { Invoke-RestMethod -Method Delete -Uri "$url/api/agent/sessions/$sessionId" -Headers $requestHeaders -TimeoutSec 15 | Out-Null } catch { } }
        }
    } -ArgumentList $GatewayBaseUrl, $headers, $payload
    while ((Get-Job -State Running).Count -ge $Concurrency) { Start-Sleep -Milliseconds 100 }
}
while ((Get-Job -State Running).Count -gt 0) { Start-Sleep -Milliseconds 100 }
$results = @(Get-Job | Receive-Job)
Get-Job | Remove-Job -Force
$stopwatch.Stop()

$latencies = @($results | ForEach-Object { [double]$_.Milliseconds } | Sort-Object)
$success = @($results | Where-Object Success).Count
$failure = $results.Count - $success
function Get-Percentile([double[]]$values, [double]$percentile) {
    if ($values.Count -eq 0) { return 0 }
    return $values[[Math]::Min($values.Count - 1, [Math]::Floor(($values.Count - 1) * $percentile))]
}
$after = Get-CounterSnapshot $AgentMetricsBaseUrl
$delta = @{}
foreach ($outcome in @("success", "fallback", "disabled")) { $delta[$outcome] = [Math]::Max(0, ($after[$outcome] - $before[$outcome])) }

[pscustomobject]@{
    requests = $results.Count
    success = $success
    failure = $failure
    p50Ms = [Math]::Round((Get-Percentile $latencies 0.50), 1)
    p95Ms = [Math]::Round((Get-Percentile $latencies 0.95), 1)
    elapsedMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 1)
    rerankerSuccess = $delta.success
    rerankerFallback = $delta.fallback
    rerankerDisabled = $delta.disabled
} | ConvertTo-Json
