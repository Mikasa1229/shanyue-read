param(
    [string]$GatewayBaseUrl = 'http://localhost:8080',
    [long]$CanonicalBookId = 358679512818388992,
    [int]$CurrentChapter = 0
)

$ErrorActionPreference = 'Stop'
$stamp = (Get-Date).ToString('yyyyMMddHHmmssfff')
$username = "agentflow_$stamp"
$password = 'TestPass_123'
$credentials = @{ username = $username; password = $password; nickname = 'Agent Flow' } | ConvertTo-Json
$register = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/auth/register" -ContentType 'application/json' -Body $credentials
$login = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/auth/login" -ContentType 'application/json' -Body $credentials
if ($login.code -ne 200 -or [string]::IsNullOrWhiteSpace([string]$login.data.token)) { throw 'Login failed.' }

$token = [string]$login.data.token
$userId = [long]$login.data.userInfo.id
$headers = @{ Authorization = "Bearer $token" }
$me = Invoke-RestMethod -Method Get -Uri "$GatewayBaseUrl/api/users/me" -Headers $headers
$credits = Invoke-RestMethod -Method Get -Uri "$GatewayBaseUrl/api/users/me/credits" -Headers $headers
if ($credits.code -ne 200 -or $null -eq $credits.data -or [int]$credits.data.availableCredits -ne 3) {
    throw 'New users must receive exactly three starter credits.'
}
$session = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/agent/sessions" -Headers $headers `
    -ContentType 'application/json' -Body (@{ title = 'LightRAG smoke'; context = 'indexed work' } | ConvertTo-Json)
if ($session.code -ne 200) { throw 'Agent session creation failed.' }

$sessionId = [long]$session.data.id
$message = @{
    content = 'Give a short evidence-backed recap from the currently visible indexed content. If evidence is insufficient, say so.'
    mode = 'PLATFORM'
    canonicalBookId = $CanonicalBookId
    currentChapter = $CurrentChapter
    currentBookTitle = 'indexed work'
} | ConvertTo-Json
$temporaryFile = Join-Path $env:TEMP ("agent-sse-" + [guid]::NewGuid().ToString() + '.txt')
try {
    Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$GatewayBaseUrl/api/agent/sessions/$sessionId/messages:stream" `
        -Headers ($headers + @{ Accept = 'text/event-stream' }) -ContentType 'application/json' -Body $message `
        -OutFile $temporaryFile -TimeoutSec 180
    $raw = Get-Content -Raw -LiteralPath $temporaryFile
    $events = @([regex]::Matches($raw, '(?m)^event:\s*([^\r\n]+)') | ForEach-Object { $_.Groups[1].Value })
    $bytes = (Get-Item -LiteralPath $temporaryFile).Length
    foreach ($requiredEvent in @('meta', 'delta', 'recommendations', 'graph', 'done')) {
        if ($events -notcontains $requiredEvent) { throw "SSE stream did not contain required event: $requiredEvent" }
    }
} finally {
    Remove-Item -LiteralPath $temporaryFile -Force -ErrorAction SilentlyContinue
}

[pscustomobject]@{
    passed = ($register.code -eq 200 -and $login.code -eq 200 -and $me.code -eq 200 -and $credits.code -eq 200 -and $session.code -eq 200 -and $bytes -gt 0)
    registerCode = $register.code
    loginCode = $login.code
    userLookupCode = $me.code
    creditsCode = $credits.code
    starterCredits = $credits.data.availableCredits
    sessionCode = $session.code
    streamBytes = $bytes
    events = ($events -join ',')
    userIdPresent = ($userId -gt 0)
    timestamp = (Get-Date).ToUniversalTime().ToString('o')
} | ConvertTo-Json -Compress
