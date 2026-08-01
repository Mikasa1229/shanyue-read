param(
    [long]$CanonicalBookId = 358679512818388992,
    [string]$PostgresContainer = 'reader-postgres'
)

$ErrorActionPreference = 'Stop'
if ($CanonicalBookId -le 0) { throw 'CanonicalBookId must be positive.' }

function Invoke-Psql([string]$Database, [string]$Sql) {
    $result = docker exec $PostgresContainer psql -U postgres -d $Database -Atc $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL query failed for ${Database}: $result" }
    return ($result | Out-String).Trim()
}

$source = (Invoke-Psql 'db_novel' @"
SELECT c.title,
       COUNT(v.chapter_index),
       COALESCE(MIN(v.chapter_index), -1),
       COALESCE(MAX(v.chapter_index), -1),
       COUNT(v.chapter_index) FILTER (WHERE v.index_status = 'READY'),
       COUNT(v.chapter_index) FILTER (WHERE v.index_status = 'FAILED'),
       COUNT(v.chapter_index) FILTER (WHERE v.index_status = 'PENDING'),
       COUNT(v.chapter_index) - COUNT(DISTINCT v.chapter_index)
FROM t_canonical_book c
LEFT JOIN t_book_content_version v ON v.canonical_book_id = c.id
WHERE c.id = $CanonicalBookId
GROUP BY c.title;
"@) -split '\|'

if ($source.Count -lt 8) { throw "Canonical work $CanonicalBookId was not found or returned an invalid ledger row." }
$title = $source[0]
$sourceTotal = [int]$source[1]
$sourceMin = [int]$source[2]
$sourceMax = [int]$source[3]
$sourceReady = [int]$source[4]
$sourceFailed = [int]$source[5]
$sourcePending = [int]$source[6]
$sourceDuplicates = [int]$source[7]
$expectedRange = if ($sourceMax -ge $sourceMin) { $sourceMax - $sourceMin + 1 } else { 0 }

$agent = (Invoke-Psql 'db_agent' @"
SELECT
  (SELECT COUNT(*) FROM t_knowledge_document WHERE canonical_book_id = $CanonicalBookId),
  (SELECT COUNT(*) FROM t_knowledge_document WHERE canonical_book_id = $CanonicalBookId AND index_status = 'READY'),
  (SELECT COUNT(DISTINCT chapter_index) FROM t_knowledge_document WHERE canonical_book_id = $CanonicalBookId),
  (SELECT COUNT(*) FROM t_knowledge_chunk WHERE canonical_book_id = $CanonicalBookId),
  (SELECT COUNT(*) FROM t_knowledge_index_job WHERE canonical_book_id = $CanonicalBookId AND job_type = 'CHAPTER_INDEX' AND status = 'COMPLETED');
"@) -split '\|'

if ($agent.Count -lt 5) { throw 'Agent ledger query returned an invalid row.' }
$agentDocuments = [int]$agent[0]
$agentReady = [int]$agent[1]
$agentChapters = [int]$agent[2]
$agentChunks = [int]$agent[3]
$agentCompletedJobs = [int]$agent[4]

$passed = $sourceTotal -gt 0 -and
    $sourceTotal -eq $expectedRange -and
    $sourceReady -eq $sourceTotal -and
    $sourceFailed -eq 0 -and
    $sourcePending -eq 0 -and
    $sourceDuplicates -eq 0 -and
    $agentDocuments -eq $sourceTotal -and
    $agentReady -eq $sourceTotal -and
    $agentChapters -eq $sourceTotal -and
    $agentCompletedJobs -eq $sourceTotal -and
    $agentChunks -gt 0

[pscustomobject]@{
    passed = $passed
    canonicalBookId = $CanonicalBookId
    title = $title
    source = [pscustomobject]@{
        total = $sourceTotal
        minChapter = $sourceMin
        maxChapter = $sourceMax
        ready = $sourceReady
        failed = $sourceFailed
        pending = $sourcePending
        duplicateIndexes = $sourceDuplicates
    }
    agent = [pscustomobject]@{
        documents = $agentDocuments
        readyDocuments = $agentReady
        indexedChapters = $agentChapters
        chunks = $agentChunks
        completedJobs = $agentCompletedJobs
    }
    timestamp = (Get-Date).ToUniversalTime().ToString('o')
} | ConvertTo-Json -Depth 5

if (-not $passed) { exit 2 }
