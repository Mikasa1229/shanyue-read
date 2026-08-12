param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path,
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $Root 'backend'
$jar = Join-Path $backend 'reader-gateway\target\reader-gateway-1.0.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw "Gateway JAR not found: $jar" }

# The Agent service validates this shared value on every browser-facing call.
# Load it in the parent process so the spawned JVM receives the same .env value.
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
if ([string]::IsNullOrWhiteSpace($env:AGENT_GATEWAY_TOKEN)) {
    throw "AGENT_GATEWAY_TOKEN is not configured; gateway startup was cancelled."
}

Get-CimInstance Win32_Process -Filter "name='java.exe'" |
    Where-Object { $_.CommandLine -match 'reader-gateway-1\.0\.0-SNAPSHOT\.jar' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Start-Sleep -Seconds 1

$logOut = Join-Path $env:TEMP 'reader-gateway-runtime.out.log'
$logErr = Join-Path $env:TEMP 'reader-gateway-runtime.err.log'
Remove-Item -LiteralPath $logOut,$logErr -Force -ErrorAction SilentlyContinue
$started = Start-Process -FilePath 'java' -ArgumentList @(
    '-jar', $jar, '--spring.profiles.active=dev', "--server.port=$Port"
) -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput $logOut -RedirectStandardError $logErr -PassThru

for ($attempt = 0; $attempt -lt 45; $attempt++) {
    Start-Sleep -Seconds 2
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
        [pscustomobject]@{
            pid = $started.Id
            port = $Port
            status = 'LISTENING'
            gatewayTokenConfigured = $true
        } | ConvertTo-Json -Compress
        exit 0
    }
}
Get-Content -LiteralPath $logErr -Tail 80 -ErrorAction SilentlyContinue
Get-Content -LiteralPath $logOut -Tail 80 -ErrorAction SilentlyContinue
throw 'Gateway did not begin listening.'
