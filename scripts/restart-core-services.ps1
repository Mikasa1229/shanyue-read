param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $Root 'backend'
$services = @(
    @{ Name = 'reader-user'; Port = 8081 },
    @{ Name = 'reader-novel'; Port = 8082 },
    @{ Name = 'reader-comment'; Port = 8083 },
    @{ Name = 'reader-agent'; Port = 8086 },
    @{ Name = 'reader-gateway'; Port = 8080 }
)

# Keep development secrets out of both command lines and logs.
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

foreach ($service in $services) {
    $jar = Join-Path $backend ("{0}\target\{0}-1.0.0-SNAPSHOT.jar" -f $service.Name)
    if (-not (Test-Path -LiteralPath $jar)) { throw "JAR not found: $jar" }
    Get-CimInstance Win32_Process -Filter "name='java.exe'" |
        Where-Object { $_.CommandLine -match ("{0}-1\.0\.0-SNAPSHOT\.jar" -f $service.Name) } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
}

Start-Sleep -Seconds 1
foreach ($service in $services) {
    $jar = Join-Path $backend ("{0}\target\{0}-1.0.0-SNAPSHOT.jar" -f $service.Name)
    $out = Join-Path $env:TEMP ("{0}-runtime.out.log" -f $service.Name)
    $err = Join-Path $env:TEMP ("{0}-runtime.err.log" -f $service.Name)
    Remove-Item -LiteralPath $out, $err -Force -ErrorAction SilentlyContinue
    Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar, '--spring.profiles.active=dev', ("--server.port={0}" -f $service.Port)) `
        -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err | Out-Null
}

for ($attempt = 0; $attempt -lt 40; $attempt++) {
    Start-Sleep -Seconds 2
    $ready = $true
    foreach ($service in $services) {
        $listener = Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue
        if (-not $listener) { $ready = $false }
    }
    if ($ready) { break }
}

$services | ForEach-Object {
    $port = $_.Port
    if ($_.Name -eq 'reader-agent') {
        try {
        $health = Invoke-RestMethod -Method Get -Uri ("http://localhost:{0}/actuator/health" -f $port) -TimeoutSec 2
        [pscustomobject]@{ service = $_.Name; port = $port; health = $health.status }
        } catch {
            [pscustomobject]@{ service = $_.Name; port = $port; health = 'DOWN' }
        }
    } elseif (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        [pscustomobject]@{ service = $_.Name; port = $port; health = 'LISTENING' }
    } else {
        [pscustomobject]@{ service = $_.Name; port = $port; health = 'DOWN' }
    }
} | ConvertTo-Json -Compress
