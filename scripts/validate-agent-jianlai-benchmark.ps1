[CmdletBinding()]
param(
    [string]$GatewayBaseUrl = 'http://localhost:8080',
    [long]$CanonicalBookId = 358679512818388992,
    [int]$MaxCases = 3,
    [switch]$RunLive,
    [string]$ReportPath = 'logs/agent-jianlai-benchmark-report.json'
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$datasetPath = Join-Path $repo 'backend/reader-agent/src/test/resources/agent-jianlai-benchmark-cases.json'
$dataset = Get-Content -LiteralPath $datasetPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($CanonicalBookId -ne [long]$dataset.canonicalBookId) { throw 'CanonicalBookId does not match the Jian Lai benchmark corpus.' }
$cases = @($dataset.cases)
if ($MaxCases -lt 1 -or $MaxCases -gt $cases.Count) { throw "MaxCases must be between 1 and $($cases.Count)." }

$report = [ordered]@{
    suite = 'Jian Lai real-corpus benchmark'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    canonicalBookId = [long]$CanonicalBookId
    corpus = [ordered]@{ title = [string]$dataset.title; author = [string]$dataset.author; cases = $cases.Count; indexedTextRequired = $true }
    offline = [ordered]@{ passed = $true; cases = $cases.Count; note = 'Schema and boundary validation only; no model call.' }
    live = [ordered]@{ skipped = (-not $RunLive); passed = $false; attempted = 0; results = @(); note = 'Skipped by default to avoid consuming platform credits.' }
}

if ($RunLive) {
    if (-not (Test-Path (Join-Path $repo 'scripts/validate-agent-e2e.ps1'))) { throw 'Base E2E script is missing.' }
    $credentials = @{ username = ('jianlai_bench_' + (Get-Date).ToString('yyyyMMddHHmmssfff')); password = 'TestPass_123'; nickname = 'Jian Lai benchmark' } | ConvertTo-Json
    $register = Invoke-RestMethod -Method Post -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/auth/register" -ContentType 'application/json' -Body $credentials -TimeoutSec 15
    $login = Invoke-RestMethod -Method Post -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/auth/login" -ContentType 'application/json' -Body $credentials -TimeoutSec 15
    if ($login.code -ne 200 -or [string]::IsNullOrWhiteSpace([string]$login.data.token)) { throw 'Jian Lai benchmark login failed.' }
    $token = [string]$login.data.token
    $headers = @{ Authorization = "Bearer $token"; Accept = 'text/event-stream' }
    $credits = Invoke-RestMethod -Method Get -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/users/me/credits" -Headers $headers -TimeoutSec 15
    if ($credits.code -ne 200 -or [int]$credits.data.availableCredits -lt $MaxCases) { throw "The benchmark user needs at least $MaxCases available credits." }
    $bookLink = Invoke-RestMethod -Method Get -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/agent/books/$CanonicalBookId/reader-link" -Headers $headers -TimeoutSec 15
    $book = $bookLink.data
    if ($bookLink.code -ne 200 -or $null -eq $book -or [string]::IsNullOrWhiteSpace([string]$book.sourceBookUrl) -or $null -eq $book.sourceId) { throw 'The Jian Lai canonical work has no usable source mapping for the test shelf.' }
    $shelfBody = @{ sourceId = [long]$book.sourceId; canonicalBookId = [long]$CanonicalBookId; sourceName = 'benchmark'; bookName = [string]$book.title; author = [string]$book.author; coverUrl = [string]$book.coverUrl; bookUrl = [string]$book.sourceBookUrl } | ConvertTo-Json
    $shelf = Invoke-RestMethod -Method Post -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/bookshelf" -Headers $headers -ContentType 'application/json' -Body $shelfBody -TimeoutSec 15
    if ($shelf.code -ne 200) { throw 'Could not add Jian Lai to the temporary benchmark shelf.' }
    $shelfAdded = $true
    $session = Invoke-RestMethod -Method Post -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/agent/sessions" -Headers $headers -ContentType 'application/json' -Body (@{ title = 'Jian Lai real-corpus benchmark'; context = 'canonical Jian Lai' } | ConvertTo-Json) -TimeoutSec 15
    if ($session.code -ne 200) { throw 'Jian Lai benchmark session creation failed.' }
    $sessionId = [long]$session.data.id
    $selected = $cases | Select-Object -First $MaxCases
    try {
    foreach ($case in $selected) {
        $progressBody = @{ bookUrl = [string]$book.sourceBookUrl; chapterName = "Benchmark chapter $([int]$case.boundary + 1)"; chapterUrl = "benchmark://jianlai/$([int]$case.boundary)"; chapterIndex = [int]$case.boundary; totalChapters = 1279 } | ConvertTo-Json
        $progress = Invoke-RestMethod -Method Put -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/bookshelf/progress" -Headers $headers -ContentType 'application/json' -Body $progressBody -TimeoutSec 15
        if ($progress.code -ne 200) { throw "Could not set benchmark reading progress for $($case.id)." }
        $payload = @{ content = [string]$case.prompt; mode = 'PLATFORM'; canonicalBookId = [long]$CanonicalBookId; currentChapter = [int]$case.boundary; currentBookTitle = 'Jian Lai' } | ConvertTo-Json
        $temporaryFile = Join-Path $env:TEMP ('jianlai-agent-sse-' + [guid]::NewGuid().ToString('N') + '.txt')
        $start = Get-Date
        try {
            Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/agent/sessions/$sessionId/messages:stream" -Headers $headers -ContentType 'application/json' -Body $payload -OutFile $temporaryFile -TimeoutSec 180
            $raw = Get-Content -Raw -LiteralPath $temporaryFile
            $events = @([regex]::Matches($raw, '(?m)^event:\s*([^\r\n]+)') | ForEach-Object { $_.Groups[1].Value })
            $missing = @($case.mustHaveEvents | Where-Object { $events -notcontains $_ })
            $report.live.results += [ordered]@{ id = $case.id; category = $case.category; boundary = [int]$case.boundary; passed = ($missing.Count -eq 0); missingEvents = $missing; latencyMs = [math]::Round(((Get-Date) - $start).TotalMilliseconds, 1); streamBytes = (Get-Item -LiteralPath $temporaryFile).Length }
        } finally { Remove-Item -LiteralPath $temporaryFile -Force -ErrorAction SilentlyContinue }
    }
    } finally {
        if ($shelfAdded) {
            try {
                $encodedBookUrl = [uri]::EscapeDataString([string]$book.sourceBookUrl)
                Invoke-RestMethod -Method Delete -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/bookshelf?bookUrl=$encodedBookUrl" -Headers $headers -TimeoutSec 15 -ErrorAction Stop | Out-Null
            } catch { }
        }
    }
    $report.live.attempted = $MaxCases
    $report.live.skipped = $false
    $report.live.passed = (@($report.live.results | Where-Object { -not $_.passed }).Count -eq 0)
    $report.live.note = 'Validated SSE events against the real Jian Lai context; factual and citation quality still need human or answer-suite scoring.'
}

$absoluteReport = Join-Path $repo $ReportPath
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $absoluteReport) | Out-Null
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $absoluteReport -Encoding UTF8
$report | ConvertTo-Json -Depth 8
if (-not $report.offline.passed -or ($RunLive -and -not $report.live.passed)) { exit 1 }
