param(
    [string]$BaseUrl = 'http://localhost:8080',
    [int]$DurationSeconds = 5,
    [int[]]$Concurrency = @(1, 10, 25, 50, 100),
    [string]$OutputPath = 'logs/qps-load-test.json'
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$endpoints = @(
    @{ name = 'novels_list'; path = '/api/novels?page=1&size=1'; success = @(200) },
    @{ name = 'auth_probe'; path = '/api/auth'; success = @(401) },
    @{ name = 'ranking'; path = '/api/reading/ranking?page=1&size=20'; success = @(200) }
)

function Invoke-Probe([string]$Url) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $status = 0
    $errorType = $null
    try {
        $request = [Net.HttpWebRequest]::Create($Url)
        $request.Timeout = 10000
        $request.ReadWriteTimeout = 10000
        $response = $request.GetResponse()
        $status = [int]$response.StatusCode
        $response.Close()
    } catch [Net.WebException] {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            $_.Exception.Response.Close()
        } else {
            $errorType = $_.Exception.GetType().Name
        }
    } catch {
        $errorType = $_.Exception.GetType().Name
    } finally {
        $watch.Stop()
    }
    [pscustomobject]@{
        latencyMs = [double]$watch.Elapsed.TotalMilliseconds
        status = $status
        errorType = $errorType
    }
}

function Invoke-Load([hashtable]$Endpoint, [int]$Workers) {
    $deadline = [DateTime]::UtcNow.AddSeconds($DurationSeconds)
    $worker = {
        param($url, $until)
        $rows = [Collections.Generic.List[object]]::new()
        while ([DateTime]::UtcNow -lt $until) {
            $rows.Add((Invoke-Probe $url))
        }
        return $rows
    }
    $jobs = @()
    for ($i = 0; $i -lt $Workers; $i++) {
        $jobs += Start-Job -ScriptBlock $worker -ArgumentList @($base + $Endpoint.path, $deadline)
    }
    $rows = @()
    try {
        Wait-Job -Job $jobs -Timeout ($DurationSeconds + 15) | Out-Null
        foreach ($job in $jobs) { $rows += Receive-Job -Job $job }
    } finally {
        $jobs | Remove-Job -Force -ErrorAction SilentlyContinue
    }
    $latencies = @($rows | ForEach-Object { [double]$_.latencyMs } | Sort-Object)
    $success = @($rows | Where-Object { $Endpoint.success -contains [int]$_.status }).Count
    $count = $rows.Count
    $percentile = {
        param([double]$p)
        if ($latencies.Count -eq 0) { return $null }
        $index = [Math]::Min($latencies.Count - 1, [Math]::Max(0, [int]($latencies.Count * $p)))
        return [Math]::Round($latencies[$index], 2)
    }
    [pscustomobject]@{
        endpoint = $Endpoint.name
        path = $Endpoint.path
        concurrency = $Workers
        requests = $count
        ok = $success
        errors = $count - $success
        qps = [Math]::Round($count / [double]$DurationSeconds, 2)
        successQps = [Math]::Round($success / [double]$DurationSeconds, 2)
        p50Ms = & $percentile 0.50
        p95Ms = & $percentile 0.95
        p99Ms = & $percentile 0.99
        maxMs = if ($latencies.Count) { [Math]::Round($latencies[-1], 2) } else { $null }
    }
}

$results = @()
foreach ($endpoint in $endpoints) {
    foreach ($workers in $Concurrency) {
        $result = Invoke-Load $endpoint $workers
        $results += $result
        $result | ConvertTo-Json -Compress | Write-Output
    }
}

$report = [ordered]@{
    startedAt = (Get-Date).ToUniversalTime().ToString('o')
    baseUrl = $base
    durationSeconds = $DurationSeconds
    results = $results
}
$parent = Split-Path -Parent $OutputPath
if ($parent -and !(Test-Path $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
$report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $OutputPath
Write-Output "报告已写入 $OutputPath"
