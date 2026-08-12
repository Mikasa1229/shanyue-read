param(
    [string]$AgentBaseUrl = 'http://localhost:8086',
    [string]$PostgresContainer = 'reader-postgres'
)

$ErrorActionPreference = 'Stop'
$baseUrl = $AgentBaseUrl.TrimEnd('/')

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$health = Invoke-RestMethod -Method Get -Uri "$baseUrl/actuator/health"
Assert-Condition ($health.status -eq 'UP') 'Agent health is not UP.'

# Browser-facing endpoints must only be reachable after the Gateway injects its secret.
try {
    Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$baseUrl/api/agent/infrastructure" -ErrorAction Stop | Out-Null
    throw 'Direct Agent API access succeeded; deploy matching AGENT_GATEWAY_TOKEN values and restart the Agent.'
} catch {
    if ($_.Exception.Message -like 'Direct Agent API access succeeded*') { throw }
    if ($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404) {
        throw "Direct Agent API did not return 404: $($_.Exception.Message)"
    }
}

$versions = docker exec $PostgresContainer psql -U postgres -d db_agent -Atc "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version IN ('26','27','28','29','30');"
Assert-Condition ($versions.Trim() -eq '26,27,28,29,30,31') 'Flyway migrations V26-V31 are not all recorded as successful.'

[pscustomobject]@{
    passed = $true
    agentBaseUrl = $baseUrl
    health = $health.status
    directApi = '404 not found'
    flywayVersions = $versions.Trim()
    timestamp = (Get-Date).ToUniversalTime().ToString('o')
} | ConvertTo-Json
