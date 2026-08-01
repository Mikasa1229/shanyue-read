param(
    [string]$AgentBaseUrl = 'http://localhost:8086',
    [string]$RerankerBaseUrl = $env:AGENT_RERANKER_BASE_URL,
    [string]$RerankerApiKey = $env:AGENT_RERANKER_API_KEY,
    [string]$RerankerPath = $env:AGENT_RERANKER_PATH,
    [string]$RerankerModel = $env:AGENT_RERANKER_MODEL,
    [string]$FixturePath = (Join-Path (Resolve-Path "$PSScriptRoot\..") 'backend\reader-agent\src\test\resources\agent-original-fixture.json'),
    [switch]$SkipProviderProbe
)

$ErrorActionPreference = 'Stop'
$baseUrl = $AgentBaseUrl.TrimEnd('/')

# Local development secrets are loaded for this process only and are never printed.
$envFile = Join-Path (Resolve-Path "$PSScriptRoot\..") '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*([^#=][^=]*)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim().Trim('"').Trim("'")
            if ($name) { Set-Item -Path ("Env:" + $name) -Value $value }
        }
    }
    if ([string]::IsNullOrWhiteSpace($RerankerBaseUrl)) { $RerankerBaseUrl = $env:AGENT_RERANKER_BASE_URL }
    if ([string]::IsNullOrWhiteSpace($RerankerApiKey)) { $RerankerApiKey = $env:AGENT_RERANKER_API_KEY }
    if ([string]::IsNullOrWhiteSpace($RerankerPath)) { $RerankerPath = $env:AGENT_RERANKER_PATH }
    if ([string]::IsNullOrWhiteSpace($RerankerModel)) { $RerankerModel = $env:AGENT_RERANKER_MODEL }
}
if ([string]::IsNullOrWhiteSpace($RerankerPath)) { $RerankerPath = '/v1/rerank' }
if ([string]::IsNullOrWhiteSpace($RerankerModel)) { $RerankerModel = 'rerank-v3.5' }

$checks = [ordered]@{}
$health = Invoke-RestMethod -Method Get -Uri "$baseUrl/actuator/health" -TimeoutSec 10
$checks.agentHealth = ($health.status -eq 'UP')

try {
    Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$baseUrl/api/agent/infrastructure" -TimeoutSec 10 -ErrorAction Stop | Out-Null
    $checks.gatewayBoundary = $false
} catch {
    $checks.gatewayBoundary = ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404)
}

if (-not (Test-Path -LiteralPath $FixturePath)) { throw "Fixture not found: $FixturePath" }
$fixture = Get-Content -Raw -Encoding UTF8 -LiteralPath $FixturePath | ConvertFrom-Json
$checks.fixtureLicense = ([string]$fixture.license -eq 'original-synthetic')
$checks.fixtureChapters = (@($fixture.chapters).Count -ge 10)

$rerankerConfigured = (-not [string]::IsNullOrWhiteSpace($RerankerBaseUrl)) -and
    (-not [string]::IsNullOrWhiteSpace($RerankerApiKey))
$checks.rerankerConfigured = $rerankerConfigured
$checks.localReranker = $true
$probe = 'not-run'
if ($rerankerConfigured -and -not $SkipProviderProbe) {
    $payload = @{ model = $RerankerModel; query = 'fixture query'; documents = @('fixture evidence one', 'fixture evidence two'); top_n = 2 } | ConvertTo-Json -Compress
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-RestMethod -Method Post -Uri ($RerankerBaseUrl.TrimEnd('/') + '/' + $RerankerPath.TrimStart('/')) `
            -Headers @{ Authorization = "Bearer $RerankerApiKey" } -ContentType 'application/json' -Body $payload -TimeoutSec 15
        $probe = if (@($response.results).Count -gt 0) { 'passed' } else { 'invalid-response' }
    } catch {
        $probe = 'failed'
    } finally { $timer.Stop() }
    $checks.rerankerProbe = ($probe -eq 'passed')
    $checks.rerankerProbeMs = [Math]::Round($timer.Elapsed.TotalMilliseconds, 1)
} else {
    $checks.rerankerProbe = $false
}

$passed = $checks.agentHealth -and $checks.gatewayBoundary -and $checks.fixtureLicense -and $checks.fixtureChapters -and
    (-not $checks.rerankerConfigured -or $SkipProviderProbe -or $checks.rerankerProbe)
$result = [ordered]@{
    passed = $passed
    status = if (-not $checks.rerankerConfigured -and $passed) { 'local_ready_external_optional' } elseif ($passed -and $checks.rerankerConfigured) { 'ready_external_verified' } elseif (-not $checks.rerankerConfigured) { 'local_not_ready' } else { 'external_reranker_probe_failed_local_fallback_ready' }
    checks = $checks
    providerProbe = $probe
    fixture = [IO.Path]::GetFileName($FixturePath)
    timestamp = (Get-Date).ToUniversalTime().ToString('o')
}
$result | ConvertTo-Json -Depth 5
if (-not $passed) { exit 2 }
