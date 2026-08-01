param(
    [string]$GatewayBaseUrl = 'http://localhost:8080',
    [long]$CanonicalBookId = 358679512818388992,
    [int]$CurrentChapter = 0,
    [string]$Dependency = 'reader-neo4j'
)

$ErrorActionPreference = 'Stop'
if ($Dependency -notin @('reader-neo4j', 'reader-milvus')) { throw 'Dependency must be reader-neo4j or reader-milvus.' }

# Use an approved chapter-zero character as the LightRAG seed without printing its name.
$seed = (docker exec reader-postgres psql -U postgres -d db_agent -Atc "SELECT name FROM t_knowledge_graph_node WHERE canonical_book_id=$CanonicalBookId AND node_type='CHARACTER' AND review_status='APPROVED' AND first_chapter <= $CurrentChapter LIMIT 1;").Trim()
if ([string]::IsNullOrWhiteSpace($seed)) { throw 'No approved visible character seed found.' }

$stamp = (Get-Date).ToString('yyyyMMddHHmmssfff')
$credentials = @{ username = "graphfault_$stamp"; password = 'TestPass_123'; nickname = 'Graph fault' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/auth/register" -ContentType 'application/json' -Body $credentials | Out-Null
$login = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/auth/login" -ContentType 'application/json' -Body $credentials
$headers = @{ Authorization = "Bearer $($login.data.token)" }
$session = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/agent/sessions" -Headers $headers `
    -ContentType 'application/json' -Body (@{ title = 'Graph fault'; context = 'indexed work' } | ConvertTo-Json)
$sessionId = [long]$session.data.id
$payload = @{
    content = "Analyze the character $seed relationship using only visible chapter evidence; do not spoil future chapters."
    mode = 'PLATFORM'
    canonicalBookId = $CanonicalBookId
    currentChapter = $CurrentChapter
    currentBookTitle = 'indexed work'
} | ConvertTo-Json -Compress
$payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
$prometheus = (Invoke-WebRequest -UseBasicParsing http://localhost:8086/actuator/prometheus).Content
$beforeMatch = [regex]::Match($prometheus, 'reader_agent_graph_recall_total\{outcome="fallback",\}\s+([0-9.]+)')
$before = if ($beforeMatch.Success) { [double]$beforeMatch.Groups[1].Value } else { 0 }
$temporaryFile = Join-Path $env:TEMP ("agent-graph-fault-" + [guid]::NewGuid().ToString() + '.txt')
try {
    docker pause $Dependency | Out-Host
    Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$GatewayBaseUrl/api/agent/sessions/$sessionId/messages:stream" `
        -Headers ($headers + @{ Accept = 'text/event-stream' }) -ContentType 'application/json; charset=utf-8' `
        -Body $payloadBytes -OutFile $temporaryFile -TimeoutSec 180
    $raw = Get-Content -Raw -LiteralPath $temporaryFile
    $events = @([regex]::Matches($raw, '(?m)^event:\s*([^\r\n]+)') | ForEach-Object { $_.Groups[1].Value })
} finally {
    docker unpause $Dependency | Out-Host
    Remove-Item -LiteralPath $temporaryFile -Force -ErrorAction SilentlyContinue
}
$prometheus = (Invoke-WebRequest -UseBasicParsing http://localhost:8086/actuator/prometheus).Content
$afterMatch = [regex]::Match($prometheus, 'reader_agent_graph_recall_total\{outcome="fallback",\}\s+([0-9.]+)')
$after = if ($afterMatch.Success) { [double]$afterMatch.Groups[1].Value } else { 0 }
[pscustomobject]@{
    passed = ($events -contains 'delta' -and $events -contains 'done' -and ($Dependency -ne 'reader-neo4j' -or $after -gt $before))
    dependency = $Dependency
    events = ($events -join ',')
    graphFallbackBefore = $before
    graphFallbackAfter = $after
    graphFallbackDelta = $after - $before
    seedFound = $true
} | ConvertTo-Json -Compress
