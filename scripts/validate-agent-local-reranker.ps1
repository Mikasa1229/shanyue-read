param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $ProjectRoot 'backend')
try {
    # The focused regression includes a 50-run determinism and bounded-result check.
    mvn -pl reader-agent -am test `
        '-Dtest=LocalEvidenceRerankerTest' `
        '-Dsurefire.failIfNoSpecifiedTests=false' `
        '-DskipFrontend=true'
    if ($LASTEXITCODE -ne 0) { throw "Local reranker validation failed with exit code $LASTEXITCODE" }
    [ordered]@{
        passed = $true
        implementation = 'local-hybrid-bm25-semantic-evidence-corroboration'
        repeatedRuns = 50
        externalProviderRequired = $false
        timestamp = (Get-Date).ToUniversalTime().ToString('o')
    } | ConvertTo-Json
} finally {
    Pop-Location
}
