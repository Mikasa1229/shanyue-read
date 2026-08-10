param(
    [long]$BookId = 358679512818388992,
    [int]$StartChapter = 1,
    [int]$EndChapter = 100,
    [string]$Round = "round-0",
    [string]$OutputDirectory = "artifacts/jianlai-rag-evaluation",
    [switch]$EvaluateRetrieval
)

$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$repoRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = Join-Path $repoRoot $OutputDirectory
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$outputPath = Join-Path $outputRoot "$Round.json"
$goldPath = Join-Path $repoRoot "backend/reader-agent/src/test/resources/agent-jianlai-100-gold.json"
$gold = Get-Content -Raw -Encoding UTF8 $goldPath | ConvertFrom-Json

function Invoke-AgentSql([string]$Sql) {
    $value = docker exec reader-postgres psql -U postgres -d db_agent -At -F "`t" -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL evaluation query failed" }
    return @($value)
}
function Test-AllTerms([string]$Text, $Terms) {
    $values = @($Terms)
    if ($values.Count -eq 0) { return $false }
    foreach ($value in $values) { if (-not $Text.Contains([string]$value)) { return $false } }
    return $true
}
function Get-EvidenceChapter([string]$Text) {
    if ($Text -match '^\[Chapter\s+(\d+)\]') { return [int]$matches[1] - 1 }
    return $null
}

$chapterStart = $StartChapter - 1
$chapterEnd = $EndChapter - 1
$nodes = Invoke-AgentSql "SELECT id,name,node_type,first_chapter,last_chapter,regexp_replace(COALESCE(evidence,''), E'[\\n\\r\\t]+', ' ', 'g'),confidence FROM t_knowledge_graph_node WHERE canonical_book_id=$BookId AND first_chapter <= $chapterEnd AND last_chapter >= $chapterStart AND review_status='APPROVED' ORDER BY id"
$edges = Invoke-AgentSql "SELECT e.source_node_id,e.target_node_id,e.relation,e.first_chapter,regexp_replace(COALESCE(e.evidence,''), E'[\\n\\r\\t]+', ' ', 'g'),e.confidence FROM t_knowledge_graph_edge e WHERE e.canonical_book_id=$BookId AND e.first_chapter BETWEEN $chapterStart AND $chapterEnd AND e.review_status='APPROVED' ORDER BY e.id"
$aliases = Invoke-AgentSql "SELECT alias,node_id,node_type,first_chapter FROM t_knowledge_entity_alias WHERE canonical_book_id=$BookId AND first_chapter <= $chapterEnd ORDER BY alias"
$clues = Invoke-AgentSql "SELECT chapter_index,signal,excerpt FROM t_knowledge_clue WHERE canonical_book_id=$BookId AND chapter_index BETWEEN $chapterStart AND $chapterEnd AND review_status='APPROVED'"

$nodeRows = @($nodes | Where-Object { $_ } | ForEach-Object { $p=$_ -split "`t",7; [pscustomobject]@{id=[long]$p[0];name=$p[1];type=$p[2];first=[int]$p[3];last=[int]$p[4];evidence=$p[5];confidence=[double]$p[6]} })
$edgeRows = @($edges | Where-Object { $_ } | ForEach-Object { $p=$_ -split "`t",6; [pscustomobject]@{source=[long]$p[0];target=[long]$p[1];type=$p[2];chapter=[int]$p[3];evidence=$p[4];confidence=[double]$p[5]} })
$aliasRows = @($aliases | Where-Object { $_ } | ForEach-Object { $p=$_ -split "`t",4; [pscustomobject]@{alias=$p[0];nodeId=[long]$p[1];type=$p[2];chapter=[int]$p[3]} })
$nodeById = @{}; $nodeRows | ForEach-Object { $nodeById[$_.id] = $_ }
$namesByNode = @{}; $nodeRows | ForEach-Object { $namesByNode[$_.id] = @($_.name) }; $aliasRows | ForEach-Object { if ($namesByNode.ContainsKey($_.nodeId)) { $namesByNode[$_.nodeId] += $_.alias } }
$connectedIds = [Collections.Generic.HashSet[long]]::new(); $edgeRows | ForEach-Object { [void]$connectedIds.Add($_.source); [void]$connectedIds.Add($_.target) }
$characters = @($nodeRows | Where-Object type -eq "CHARACTER")
$events = @($nodeRows | Where-Object type -eq "EVENT" | Where-Object { $_.evidence -notlike "【*" })
$lowValuePattern = "挑水|来到|回到|看了|说了|问道|笑道|点头|摇头|一脚|观感|称呼为|走到|坐在"
$lowValueEvents = @($events | Where-Object { $_.name -match $lowValuePattern -or $_.evidence -match $lowValuePattern })
$unsupportedEdges = @($edgeRows | Where-Object {
    $edge = $_; $left=$nodeById[$edge.source]; $right=$nodeById[$edge.target]
    $leftSupported = $left -and ($left.type -eq 'EVENT' -or @($namesByNode[$edge.source] | Where-Object { $_ -and $edge.evidence.Contains($_) }).Count -gt 0)
    $rightSupported = $right -and ($right.type -eq 'EVENT' -or @($namesByNode[$edge.target] | Where-Object { $_ -and $edge.evidence.Contains($_) }).Count -gt 0)
    -not $left -or -not $right -or -not $leftSupported -or -not $rightSupported
})
$fragmentGroups = @($gold.canonicalEntities | ForEach-Object {
    $allNames = @($_.name) + @($_.aliases)
    $matched = @($nodeRows | Where-Object { $allNames -contains $_.name })
    if ($matched.Count -gt 1) { [pscustomobject]@{canonical=$_.name;fragments=@($matched.name)} }
})
$aliasOwner = @{}; $gold.canonicalEntities | ForEach-Object { $canonical=$_.name; $aliasOwner[$canonical]=$canonical; @($_.aliases) | ForEach-Object { $aliasOwner[$_]=$canonical } }
$aliasPollution = @($aliasRows | Where-Object {
    $nodeName = if ($nodeById[$_.nodeId]) { $nodeById[$_.nodeId].name } else { $null }
    $aliasOwner.ContainsKey($_.alias) -and $aliasOwner.ContainsKey($nodeName) -and $aliasOwner[$_.alias] -ne $aliasOwner[$nodeName]
})

$nameToNodeIds = @{}
foreach ($node in $nodeRows) {
    if (-not $nameToNodeIds.ContainsKey($node.name)) { $nameToNodeIds[$node.name] = @() }
    $nameToNodeIds[$node.name] += $node.id
}
foreach ($alias in $aliasRows) {
    if (-not $nameToNodeIds.ContainsKey($alias.alias)) { $nameToNodeIds[$alias.alias] = @() }
    $nameToNodeIds[$alias.alias] += $alias.nodeId
}
function Get-EntityIds([string]$Name) {
    $names = @($Name)
    $canonical = @($gold.canonicalEntities | Where-Object name -eq $Name | Select-Object -First 1)
    if ($canonical.Count -gt 0) { $names += @($canonical[0].aliases) }
    $ids = @()
    foreach ($name in $names) { if ($nameToNodeIds.ContainsKey($name)) { $ids += @($nameToNodeIds[$name]) } }
    return @($ids | Select-Object -Unique)
}

$relationHits = @($gold.requiredRelations | ForEach-Object {
    $relationGold = $_
    $leftIds = Get-EntityIds $relationGold.source
    $rightIds = Get-EntityIds $relationGold.target
    $acceptedTypes = @($relationGold.acceptedTypes)
    $direction = if ($relationGold.direction) { [string]$relationGold.direction } else { "EITHER" }
    $candidateEdges = @($edgeRows | Where-Object {
        $forward = $_.source -in $leftIds -and $_.target -in $rightIds
        $reverse = $_.source -in $rightIds -and $_.target -in $leftIds
        $pairMatches = if ($direction -eq "FORWARD") { $forward } else { $forward -or $reverse }
        # A listed chapter establishes that the gold relation belongs within the 100-chapter corpus.
        # Later in-bound evidence is valid too; requiring the first mention exactly made recall depend
        # on a model's window choice instead of relation semantics and evidence quality.
        $chapterMatches = $_.chapter -ge $chapterStart -and $_.chapter -le $chapterEnd
        $pairMatches -and $chapterMatches -and $_.type -in $acceptedTypes
    })
    $evidenceMatches = @($candidateEdges | Where-Object {
        $evidenceTerms = @($relationGold.minEvidenceTerms | Where-Object { $_ })
        $evidenceTerms.Count -eq 0 -or (Test-AllTerms $_.evidence $evidenceTerms)
    })
    [pscustomobject]@{id=$relationGold.id;source=$relationGold.source;target=$relationGold.target;kind=$relationGold.kind;acceptedTypes=$acceptedTypes;direction=$direction;hit=($evidenceMatches.Count -gt 0);candidateTypes=@($candidateEdges.type);evidenceChapters=@($relationGold.evidenceChapters)}
})
$characterIds = @($characters | ForEach-Object id)
$characterEdges = @($edgeRows | Where-Object { $_.source -in $characterIds -and $_.target -in $characterIds })
$genericCharacterEdges = @($characterEdges | Where-Object { $_.type -in @('KNOWS','INTERACTS_WITH') })
$unsupportedCharacterEdges = @($characterEdges | Where-Object {
    $edge = $_
    $sourceNames = @($namesByNode[$edge.source] | Where-Object { $_ })
    $targetNames = @($namesByNode[$edge.target] | Where-Object { $_ })
    -not (@($sourceNames | Where-Object { $edge.evidence.Contains($_) }).Count -gt 0 -and
            @($targetNames | Where-Object { $edge.evidence.Contains($_) }).Count -gt 0)
})
$identityHits = @($gold.canonicalEntities | Where-Object { @($_.aliases).Count -gt 0 } | ForEach-Object {
    $canonical = $_
    $canonicalNodes = @($nodeRows | Where-Object { $_.name -eq $canonical.name })
    $resolvedAliases = @($aliasRows | Where-Object { $alias=$_.alias; $alias -in @($canonical.aliases) -and $_.nodeId -in @($canonicalNodes.id) })
    [pscustomobject]@{name=$canonical.name;hit=($resolvedAliases.Count -gt 0);aliases=@($canonical.aliases)}
})
$forbiddenRelationHits = @($gold.forbiddenRelations | ForEach-Object {
    $rule = $_
    $leftIds = Get-EntityIds $rule.source
    $rightIds = Get-EntityIds $rule.target
    $direction = if ($rule.direction) { [string]$rule.direction } else { "FORWARD" }
    $matches = @($edgeRows | Where-Object {
        $forward = $_.source -in $leftIds -and $_.target -in $rightIds
        $reverse = $_.source -in $rightIds -and $_.target -in $leftIds
        $pairMatches = if ($direction -eq "FORWARD") { $forward } else { $forward -or $reverse }
        $pairMatches -and $_.type -in @($rule.types)
    })
    if ($matches.Count) { [pscustomobject]@{id=$rule.id;source=$rule.source;target=$rule.target;types=@($matches.type);evidence=@($matches.evidence)} }
})
$retrievalResults = @()
if ($EvaluateRetrieval) {
    $envValues = @{}; Get-Content (Join-Path $repoRoot ".env") | ForEach-Object { if ($_ -match '^\s*([^#=][^=]*)=(.*)$') { $envValues[$matches[1].Trim()]=$matches[2].Trim().Trim('"').Trim("'") } }
    $headers = @{'X-Agent-Internal-Token'=$envValues['AGENT_INTERNAL_TOKEN']}
    foreach ($case in $gold.retrievalCases) {
        $encoded = [uri]::EscapeDataString($case.query)
        $uri = "http://localhost:8086/internal/agent/evaluation/books/$BookId/retrieve?currentChapter=$($case.maxChapterIndex)&limit=5&query=$encoded"
        $response = Invoke-RestMethod -Headers $headers -Uri $uri -Method Get -TimeoutSec 60
        $evidence = @($response.data.evidence); $firstRank = 0
        [string[]]$expectedTerms = @($case.expectedTerms)
        for ($index=0; $index -lt $evidence.Count; $index++) {
            $candidate = [string]$evidence[$index]
            $allTermsFound = $expectedTerms.Length -gt 0
            for ($termIndex=0; $termIndex -lt $expectedTerms.Length; $termIndex++) {
                if ($candidate.IndexOf($expectedTerms[$termIndex], [StringComparison]::Ordinal) -lt 0) { $allTermsFound=$false; break }
            }
            $expectedChapters = @($case.expectedEvidenceChapters | Where-Object { $_ -ne $null })
            $chapterMatches = $expectedChapters.Count -eq 0 -or ((Get-EvidenceChapter $candidate) -in $expectedChapters)
            if ($allTermsFound -and $chapterMatches) { $firstRank=$index+1; break }
        }
        $forbidden = @(); foreach ($term in @($case.forbiddenTerms | Where-Object { $_ })) {
            if (@($evidence | Where-Object { ([string]$_).Contains([string]$term) }).Count -gt 0) { $forbidden += $term }
        }
        $relationId = [string]$case.expectedRelationId
        $relationAvailable = if ([string]::IsNullOrWhiteSpace($relationId)) { $true } else { @($relationHits | Where-Object { $_.id -eq $relationId -and $_.hit }).Count -gt 0 }
        $retrievalResults += [pscustomobject]@{id=$case.id;hit=($firstRank -gt 0);relationAvailable=$relationAvailable;firstRank=$firstRank;reciprocalRank=$(if($firstRank){1.0/$firstRank}else{0});forbiddenTerms=$forbidden}
    }
}

function Ratio($Numerator, $Denominator) { if ($Denominator -eq 0) { return 0.0 }; return [math]::Round($Numerator / $Denominator, 4) }
$report = [ordered]@{
    schemaVersion = "2.0"; round = $Round; generatedAt = (Get-Date).ToString("o")
    bookId = $BookId; chapterRange = @{start=$StartChapter;end=$EndChapter}
    counts = @{nodes=$nodeRows.Count;edges=$edgeRows.Count;aliases=$aliasRows.Count;clues=$clues.Count}
    nodeCountByType = @{}; edgeCountByType = @{}
    metrics = [ordered]@{
        isolatedNodeRate = Ratio (@($nodeRows | Where-Object { -not $connectedIds.Contains($_.id) }).Count) $nodeRows.Count
        characterConnectedRate = Ratio (@($characters | Where-Object { $connectedIds.Contains($_.id) }).Count) $characters.Count
        edgeNodeRatio = Ratio $edgeRows.Count $nodeRows.Count
        lowValueAtomicEventRate = Ratio $lowValueEvents.Count $events.Count
        unsupportedEdgeEvidenceRate = Ratio $unsupportedEdges.Count $edgeRows.Count
        aliasFragmentGroupCount = $fragmentGroups.Count
        canonicalAliasPollutionCount = $aliasPollution.Count
        requiredRelationRecall = Ratio (@($relationHits | Where-Object hit).Count) $relationHits.Count
        semanticCharacterRelationRecall = Ratio (@($relationHits | Where-Object { $_.kind -ne "PLACE" -and $_.hit }).Count) @($relationHits | Where-Object { $_.kind -ne "PLACE" }).Count
        supportedCharacterRelationEvidenceRate = 1.0 - (Ratio $unsupportedCharacterEdges.Count $characterEdges.Count)
        distinctCharacterRelationTypes = @($characterEdges.type | Select-Object -Unique).Count
        genericCharacterRelationRate = Ratio $genericCharacterEdges.Count $characterEdges.Count
        identityResolutionRecall = Ratio (@($identityHits | Where-Object hit).Count) $identityHits.Count
        forbiddenRelationCount = $forbiddenRelationHits.Count
        retrievalRecallAt5 = Ratio (@($retrievalResults | Where-Object hit).Count) $retrievalResults.Count
        retrievalMrr = $(if($retrievalResults.Count){[math]::Round((($retrievalResults | Measure-Object reciprocalRank -Average).Average),4)}else{0})
        spoilerBoundaryViolationCount = @($retrievalResults | Where-Object { $_.forbiddenTerms.Count -gt 0 }).Count
    }
    failures = @{
        lowValueEvents = @($lowValueEvents | Select-Object -First 12 name,first,evidence)
        unsupportedEdges = @($unsupportedEdges | Select-Object -First 12 type,chapter,evidence)
        aliasFragments = $fragmentGroups
        aliasPollution = @($aliasPollution | Select-Object alias,nodeId,type,chapter)
        missingRequiredRelations = @($relationHits | Where-Object { -not $_.hit })
        relationResults = $relationHits
        unsupportedCharacterRelations = @($unsupportedCharacterEdges | Select-Object type,chapter,evidence)
        missingIdentityResolutions = @($identityHits | Where-Object { -not $_.hit })
        forbiddenRelations = $forbiddenRelationHits
        retrieval = $retrievalResults
    }
}
$nodeRows | Group-Object type | ForEach-Object { $report.nodeCountByType[$_.Name] = $_.Count }
$edgeRows | Group-Object type | ForEach-Object { $report.edgeCountByType[$_.Name] = $_.Count }
$report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $outputPath
Write-Host "Quality report: $outputPath"
$report | ConvertTo-Json -Depth 8
