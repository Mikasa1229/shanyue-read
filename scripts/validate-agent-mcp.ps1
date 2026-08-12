param(
    [string]$BaseUrl = 'http://localhost:8086',
    [Parameter(Mandatory = $true)][long]$UserId,
    [string]$InternalToken = $env:AGENT_INTERNAL_TOKEN,
    [long]$CanonicalBookId = 0,
    [int]$RequestedChapter = 999999
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($InternalToken)) {
    throw 'Set AGENT_INTERNAL_TOKEN or pass -InternalToken. The token is never written to this report.'
}
if ($UserId -le 0) { throw 'UserId must be positive.' }

$endpoint = "$($BaseUrl.TrimEnd('/'))/internal/agent/mcp"
$headers = @{
    'X-Agent-Internal-Token' = $InternalToken
    'X-User-Id' = $UserId.ToString()
    'Content-Type' = 'application/json'
}

function Invoke-Mcp([string]$Method, [hashtable]$Params = @{}) {
    $request = @{ jsonrpc = '2.0'; id = [guid]::NewGuid().ToString('N'); method = $Method }
    if ($Params.Count -gt 0) { $request.params = $Params }
    return Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers -Body ($request | ConvertTo-Json -Depth 8)
}

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$checks = [System.Collections.Generic.List[string]]::new()
$initialize = Invoke-Mcp 'initialize'
Assert-Condition ($initialize.jsonrpc -eq '2.0') 'initialize did not return JSON-RPC 2.0.'
Assert-Condition ($initialize.result.protocolVersion -eq '2024-11-05') 'initialize returned an unexpected MCP protocol version.'
$checks.Add('initialize')

# Standard MCP SDKs send this JSON-RPC notification after a successful initialize response.
$initializedNotification = @{ jsonrpc = '2.0'; method = 'notifications/initialized' } | ConvertTo-Json -Depth 8
Invoke-WebRequest -UseBasicParsing -Method Post -Uri $endpoint -Headers $headers -Body $initializedNotification | Out-Null
$ping = Invoke-Mcp 'ping'
Assert-Condition ($ping.result -ne $null) 'ping did not return an MCP result.'
$checks.Add('initialized notification and ping')

$discovery = Invoke-Mcp 'tools/list'
$tools = @($discovery.result.tools)
$requiredTools = @('bookshelf.list', 'book.search', 'book.detail', 'reading.state', 'knowledge_graph.query')
foreach ($name in $requiredTools) {
    $tool = @($tools | Where-Object { $_.name -eq $name }) | Select-Object -First 1
    Assert-Condition ($null -ne $tool) "tools/list omitted $name."
    Assert-Condition ($tool.inputSchema.type -eq 'object' -and $tool.inputSchema.additionalProperties -eq $false) "Invalid schema for $name."
}
$checks.Add('tools/list schema')

$missingArgument = Invoke-Mcp 'tools/call' @{ name = 'book.search'; arguments = @{} }
Assert-Condition ($missingArgument.error.code -eq -32000) 'Missing required tool arguments were accepted.'
$unknownTool = Invoke-Mcp 'tools/call' @{ name = 'book.write'; arguments = @{} }
Assert-Condition ($unknownTool.error.code -eq -32000) 'A non-allowlisted write tool was accepted.'
$checks.Add('tool allowlist and argument rejection')

if ($CanonicalBookId -gt 0) {
    $graph = Invoke-Mcp 'tools/call' @{ name = 'knowledge_graph.query'; arguments = @{ canonicalBookId = $CanonicalBookId; currentChapter = $RequestedChapter } }
    Assert-Condition ($graph.error -eq $null) 'knowledge_graph.query failed.'
    $data = $graph.result.structuredContent.data
    $boundary = [int]$data.currentChapter
    foreach ($node in @($data.nodes)) {
        Assert-Condition ([int]$node.firstChapter -le $boundary) 'Graph returned a node beyond the server-owned reading boundary.'
    }
    foreach ($edge in @($data.edges)) {
        Assert-Condition ([int]$edge.firstChapter -le $boundary) 'Graph returned an edge beyond the server-owned reading boundary.'
    }
    $checks.Add("server-side spoiler boundary (chapter $boundary)")
}

[pscustomobject]@{
    passed = $true
    endpoint = $endpoint
    userId = $UserId
    checks = $checks
    timestamp = (Get-Date).ToUniversalTime().ToString('o')
} | ConvertTo-Json -Depth 5
