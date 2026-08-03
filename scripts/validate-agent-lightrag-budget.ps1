param(
    [string]$GatewayBaseUrl = 'http://localhost:8080',
    [Parameter(Mandatory = $true)][string]$AccessToken,
    [Parameter(Mandatory = $true)][long]$AdminUserId,
    [ValidateRange(1, 90)][int]$Days = 7
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($AccessToken)) { throw 'AccessToken is required and is never printed.' }
if ($AdminUserId -le 0) { throw 'AdminUserId must be positive.' }

$headers = @{ Authorization = "Bearer $AccessToken" }
$uri = "$($GatewayBaseUrl.TrimEnd('/'))/api/agent/admin/usage-breakdown?days=$Days"
$response = Invoke-RestMethod -Method Get -Uri $uri -Headers $headers -TimeoutSec 15
if ($response.code -ne 200 -or $null -eq $response.data) { throw 'The administrator usage-breakdown request was rejected.' }

$data = $response.data
$requiredSections = @('system', 'history', 'graph', 'community', 'evidence', 'tool')
$retrieval = $data.retrieval
if ($null -eq $retrieval) { throw 'Missing privacy-safe retrieval trace aggregate.' }
$sectionNames = @($data.sectionTokens.PSObject.Properties.Name)
foreach ($name in $requiredSections) {
    if ($sectionNames -notcontains $name) { throw "Missing privacy-safe prompt section: $name" }
}
$recent = @($data.recent)
if ($recent.Count -gt 50) { throw 'The endpoint returned more than 50 recent rows.' }
$forbidden = @('userId', 'sessionId', 'requestId', 'prompt', 'content', 'apiKey', 'token')
foreach ($row in $recent) {
    foreach ($name in $forbidden) {
        if ($row.PSObject.Properties.Name -contains $name) { throw "Redacted usage row contains forbidden field: $name" }
    }
}

[ordered]@{
    passed = $true
    days = [int]$data.days
    requests = [int]$data.requests
    compositionRequests = [int]$data.sectionCompositionRequests
    recentRows = $recent.Count
    sectionTokens = [ordered]@{
        system = [long]$data.sectionTokens.system
        history = [long]$data.sectionTokens.history
        graph = [long]$data.sectionTokens.graph
        community = [long]$data.sectionTokens.community
        evidence = [long]$data.sectionTokens.evidence
        tool = [long]$data.sectionTokens.tool
        retrievalTraceRequests = [long]$retrieval.traceRequests
        retrievalCandidates = [long]$retrieval.candidateCount
        retrievalEvidence = [long]$retrieval.evidenceCount
        retrievalGraphEdges = [long]$retrieval.localGraphEdgeCount
        retrievalCommunityCards = [long]$retrieval.communityCardCount
        retrievalCommunityEscalations = [long]$retrieval.communityEscalations
    }
    timestamp = (Get-Date).ToUniversalTime().ToString('o')
} | ConvertTo-Json -Depth 6
