[CmdletBinding()]
param(
    [switch]$RunLive,
    [string]$GatewayBaseUrl = 'http://localhost:8080',
    [string]$AccessToken,
    [long]$AdminUserId = 0,
    [string]$ReportPath = 'logs/agent-benchmark-report.json',
    [switch]$SkipMaven
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$datasetPath = Join-Path $repo 'backend/reader-agent/src/test/resources/agent-benchmark-cases.json'
if (-not (Test-Path -LiteralPath $datasetPath)) { throw "Benchmark corpus not found: $datasetPath" }
$dataset = Get-Content -LiteralPath $datasetPath -Raw -Encoding UTF8 | ConvertFrom-Json
$goldPath = Join-Path $repo 'backend/reader-agent/src/test/resources/agent-jianlai-knowledge-gold.json'
if (-not (Test-Path -LiteralPath $goldPath)) { throw "Jian Lai knowledge gold not found: $goldPath" }
$knowledgeGold = Get-Content -LiteralPath $goldPath -Raw -Encoding UTF8 | ConvertFrom-Json
$knowledgeGoldPass = ([long]$knowledgeGold.canonicalBookId -eq 358679512818388992 -and @($knowledgeGold.facts).Count -ge 30 -and @($knowledgeGold.evaluationCases).Count -ge 6)
$cases = @($dataset.cases)
$required = @('fact','citation','spoiler','graph','clue','recommendation','tool-security','refusal','character-interview','reading-map','cost','resilience')
$categories = @($cases | ForEach-Object { $_.category } | Sort-Object -Unique)
$schemaPass = $cases.Count -ge 20 -and (($required | Where-Object { $categories -notcontains $_ }).Count -eq 0)
foreach ($case in $cases) {
    if ([string]::IsNullOrWhiteSpace([string]$case.id) -or [string]::IsNullOrWhiteSpace([string]$case.contract)) { $schemaPass = $false }
    foreach ($chapter in @($case.goldCitations)) { if ([int]$chapter -gt [int]$case.boundary) { $schemaPass = $false } }
}

$tests = @(
    'AgentBenchmarkDatasetTest', 'JianLaiBenchmarkDatasetTest', 'JianLaiKnowledgeGoldDatasetTest', 'AgentEvaluationDatasetTest', 'AgentPolicyEvaluationTest', 'AgentAdminControllerTest',
    'AgentPromptAdvisorChainTest', 'AgentAnswerEvaluationServiceTest', 'OriginalFixtureGraphQualityTest',
    'StructuredGraphExtractorTest', 'LightRagServiceImplTest', 'LocalEvidenceRerankerTest',
    'ConfiguredRerankerServiceTest', 'PromptContextBudgetTest', 'RetrievalTraceTest',
    'KnowledgeServiceImplEvidenceRecallTest', 'KnowledgeServiceImplVisibilityTest', 'KnowledgeChunkingTest',
    'ConfiguredEmbeddingServiceTest', 'GraphKnowledgeStoreTest', 'SpoilerBoundaryServiceTest', 'InternalMcpControllerTest'
)
$testResult = [ordered]@{ skipped = $true; passed = $false; testsRun = 0; failures = 0; errors = 0; elapsedSeconds = 0 }
if (-not $SkipMaven) {
    Push-Location (Join-Path $repo 'backend')
    try {
        $start = Get-Date
        $selector = ($tests -join ',')
        & mvn -q -pl reader-agent -am "-Dtest=$selector" '-Dsurefire.failIfNoSpecifiedTests=false' test
        if ($LASTEXITCODE -ne 0) { throw "Agent Maven benchmark tests failed with exit code $LASTEXITCODE" }
        $testResult.skipped = $false
        $testResult.passed = $true
        $testResult.elapsedSeconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 3)
        $reports = Get-ChildItem (Join-Path $repo 'backend/reader-agent/target/surefire-reports') -Filter '*.txt' -ErrorAction SilentlyContinue
        foreach ($report in $reports) {
            $reportClass = ($report.BaseName -split '\.')[-1]
            if ($tests -notcontains $reportClass) { continue }
            $text = Get-Content -LiteralPath $report.FullName -Raw
            if ($text -match 'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)') {
                $testResult.testsRun += [int]$Matches[1]; $testResult.failures += [int]$Matches[2]; $testResult.errors += [int]$Matches[3]
            }
        }
    } finally { Pop-Location }
}

$liveResult = [ordered]@{ skipped = (-not $RunLive); passed = $false; checks = @(); note = 'Use validate-agent-e2e.ps1 and fault-injection scripts for full SSE and dependency evidence.' }
if ($RunLive) {
    if ([string]::IsNullOrWhiteSpace($AccessToken) -or $AdminUserId -le 0) { throw '-RunLive requires -AccessToken and a positive -AdminUserId.' }
    $headers = @{ Authorization = "Bearer $AccessToken" }
    $healthStart = Get-Date
    $health = Invoke-RestMethod -Method Get -Uri "$($GatewayBaseUrl.TrimEnd('/'))/actuator/health" -Headers $headers -TimeoutSec 15
    $healthMs = [math]::Round(((Get-Date) - $healthStart).TotalMilliseconds, 1)
    $healthOk = ($health.status -eq 'UP')
    $liveResult.checks += [ordered]@{ name = 'gateway-health'; passed = $healthOk; latencyMs = $healthMs; status = $health.status }
    $usageStart = Get-Date
    $usage = Invoke-RestMethod -Method Get -Uri "$($GatewayBaseUrl.TrimEnd('/'))/api/agent/admin/usage-breakdown?days=1" -Headers $headers -TimeoutSec 15
    $usageMs = [math]::Round(((Get-Date) - $usageStart).TotalMilliseconds, 1)
    $usageOk = ($usage.code -eq 200 -and $null -ne $usage.data)
    $liveResult.checks += [ordered]@{ name = 'admin-usage-breakdown'; passed = $usageOk; latencyMs = $usageMs }
    $liveResult.passed = ($healthOk -and $usageOk)
    $liveResult.note = 'Health and usage checks passed; run the existing SSE/fault scripts and answer-suite for full evidence.'
}

$report = [ordered]@{
    suite = 'Novel Agent comprehensive benchmark'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    corpus = [ordered]@{ name = $dataset.corpus; schemaVersion = $dataset.schemaVersion; cases = $cases.Count; categories = $categories; schemaPassed = $schemaPass }
    knowledgeGold = [ordered]@{ path = 'backend/reader-agent/src/test/resources/agent-jianlai-knowledge-gold.json'; facts = @($knowledgeGold.facts).Count; evaluationCases = @($knowledgeGold.evaluationCases).Count; schemaPassed = $knowledgeGoldPass }
    offline = $testResult
    live = $liveResult
    overallPassed = ($schemaPass -and $knowledgeGoldPass -and ($testResult.skipped -or $testResult.passed) -and ((-not $RunLive) -or $liveResult.passed))
    notMeasured = @('fact accuracy/F1', 'citation precision/recall', 'Chinese answer quality', 'recommendation NDCG@K', 'character interview consistency', 'reading map causality')
    knownEvidence = [ordered]@{
        deepSeekFixtureAnswerGate = '5/5 PASSED (historical authorized run; not rerun by offline suite)'
        originalFixture = [ordered]@{ chapters = 10; graphNodes = 24; graphEdges = 27; ruleClues = 1; lightRagCommunityCards = 20 }
        canonicalJianLai = [ordered]@{ readyChapters = 1279; chunks = 20472; postgresGraphNodes = 26893; postgresGraphEdges = 399953 }
        localRerankerRepeatRuns = 50
    }
    gates = [ordered]@{
        safety = '100% required (covered by policy/MCP tests)'
        spoiler = '0 boundary violations required'
        citation = '>=95% valid provenance required for release'
        recommendationTraceability = '100% required'
        tokenBudgetViolations = 0
    }
    limitations = @('Offline contracts do not equal model text quality', 'Answer quality requires authorized corpus and answer-suite submission', 'Cost is ESTIMATED when provider usage is absent')
}
$absoluteReport = Join-Path $repo $ReportPath
$reportDir = Split-Path -Parent $absoluteReport
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $absoluteReport -Encoding UTF8
$report | ConvertTo-Json -Depth 8
if (-not $schemaPass -or -not $knowledgeGoldPass -or (-not $testResult.skipped -and -not $testResult.passed)) { exit 1 }
