param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path,
    [int]$Port = 8086
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $Root 'backend'
$jar = Join-Path $backend 'reader-agent\target\reader-agent-1.0.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw "Agent JAR not found: $jar" }

# Load local development secrets without echoing their values.
$envFile = Join-Path $Root '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*([^#=][^=]*)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim().Trim('"').Trim("'")
            if ($name) { Set-Item -Path ("Env:" + $name) -Value $value }
        }
    }
}

$processes = Get-CimInstance Win32_Process -Filter "name='java.exe'" |
    Where-Object { $_.CommandLine -match 'reader-agent-1\.0\.0-SNAPSHOT\.jar' }
foreach ($process in $processes) {
    Stop-Process -Id $process.ProcessId -Force
}
if ($processes) { Start-Sleep -Seconds 2 }

$logOut = Join-Path $env:TEMP 'reader-agent-runtime.out.log'
$logErr = Join-Path $env:TEMP 'reader-agent-runtime.err.log'
Remove-Item -LiteralPath $logOut,$logErr -Force -ErrorAction SilentlyContinue
$started = Start-Process -FilePath 'java' -ArgumentList @(
    '-jar', $jar, '--spring.profiles.active=dev', "--server.port=$Port"
) -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput $logOut -RedirectStandardError $logErr -PassThru

$healthy = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    Start-Sleep -Seconds 2
    try {
        $health = Invoke-RestMethod -Method Get -Uri "http://localhost:$Port/actuator/health" -TimeoutSec 3
        if ($health.status -eq 'UP') { $healthy = $true; break }
    } catch { }
}
if (-not $healthy) {
    Get-Content -LiteralPath $logErr -Tail 80 -ErrorAction SilentlyContinue
    Get-Content -LiteralPath $logOut -Tail 80 -ErrorAction SilentlyContinue
    throw 'Agent did not become healthy.'
}

[pscustomobject]@{
    pid = $started.Id
    health = 'UP'
    milvusEnabled = ([string]$env:AGENT_MILVUS_ENABLED -eq 'true')
    gatewayTokenConfigured = (-not [string]::IsNullOrWhiteSpace($env:AGENT_GATEWAY_TOKEN))
} | ConvertTo-Json -Compress
