param(
    [string]$ApiBase = "http://localhost:8082/api/book-sources",
    [string]$Output = "docs/书源校验报告-2026-08-13.md",
    [string[]]$SourceIds = @()
)

$ErrorActionPreference = "Stop"
$books = @("剑来", "斗罗大陆", "西游记", "十日终焉", "龙族", "吞噬星空")
$sources = (Invoke-RestMethod "${ApiBase}?page=1&size=200").data.records |
    Where-Object { $_.enabled -eq $true }
if ($SourceIds.Count -gt 0) {
    $requestedIds = @($SourceIds | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $sources = $sources | Where-Object { $requestedIds -contains [string]$_.id }
}

function Enc([string]$Value) { [Uri]::EscapeDataString($Value) }
function Pick([string]$Keyword, $Rows) {
    $Rows | Where-Object {
        $_.name -eq $Keyword -or
        ($Keyword -eq "龙族" -and $_.name -match "^龙族(?:（全集）|全集|I：火之晨曦|II：悼亡者之瞳|III：黑月之潮|IV：奥丁之渊|V：悼亡者的归来)?$")
    } | Select-Object -First 1
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("# 书源完整链路校验报告")
$lines.Add("")
$lines.Add("生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')；标准：搜索命中、目录非空、首章正文不少于 200 字。")
$lines.Add("")
$lines.Add("| 书源 | 书名 | 搜索 | 目录 | 首章正文 | 结果 | 失败阶段 |")
$lines.Add("|---|---|---:|---:|---:|---|---|")
foreach ($source in $sources) {
    foreach ($book in $books) {
        $search = $false; $toc = $false; $contentLength = 0; $failure = ""
        try {
            $rows = @((Invoke-RestMethod "$ApiBase/$($source.id)/search?keyword=$(Enc $book)&page=1").data)
            $hit = Pick $book $rows
            if ($null -eq $hit) { $failure = "搜索" }
            else {
                $search = $true
                $chapters = @((Invoke-RestMethod "$ApiBase/$($source.id)/chapters?bookUrl=$(Enc $hit.bookUrl)").data)
                if ($chapters.Count -eq 0 -or [string]::IsNullOrWhiteSpace($chapters[0].chapterUrl)) { $failure = "目录" }
                else {
                    $toc = $true
                    $body = Invoke-RestMethod "$ApiBase/$($source.id)/content?chapterUrl=$(Enc $chapters[0].chapterUrl)"
                    $contentLength = ([string]$body.data.content).Length
                    if ($contentLength -lt 200) { $failure = "正文" }
                }
            }
        } catch { $failure = "请求异常" }
        $ok = ($search -and $toc -and $contentLength -ge 200)
        $result = if ($ok) { "通过" } else { "失败" }
        $lines.Add("| $($source.sourceName) | $book | $([int]$search) | $([int]$toc) | $contentLength | $result | $failure |")
    }
}
$lines | Set-Content -Path $Output -Encoding UTF8
Write-Output "报告已写入 $Output"
