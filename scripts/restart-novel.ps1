param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path,
    [int]$Port = 8082
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $Root 'backend'
$jar = Join-Path $backend 'reader-novel\target\reader-novel-1.0.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw "Novel JAR not found: $jar" }

# Load development-only secrets without placing them on the process command line.
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

Get-CimInstance Win32_Process -Filter "name='java.exe'" |
    Where-Object { $_.CommandLine -match 'reader-novel-1\.0\.0-SNAPSHOT\.jar' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

$logOut = Join-Path $env:TEMP 'reader-novel-runtime.out.log'
$logErr = Join-Path $env:TEMP 'reader-novel-runtime.err.log'
Remove-Item -LiteralPath $logOut,$logErr -Force -ErrorAction SilentlyContinue
$started = Start-Process -FilePath 'java' -ArgumentList @(
    '-jar', $jar, '--spring.profiles.active=dev', "--server.port=$Port"
) -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput $logOut -RedirectStandardError $logErr -PassThru

for ($attempt = 0; $attempt -lt 45; $attempt++) {
    Start-Sleep -Seconds 2
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listener -and -not $started.HasExited) {
        [pscustomobject]@{ pid = $started.Id; health = 'LISTENING' } | ConvertTo-Json -Compress
        exit 0
    }
}

Get-Content -LiteralPath $logErr -Tail 80 -ErrorAction SilentlyContinue
Get-Content -LiteralPath $logOut -Tail 80 -ErrorAction SilentlyContinue
throw 'Novel service did not become healthy.'
