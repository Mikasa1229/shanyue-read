$cand=Get-Content artifacts\source-candidates.json -Raw | ConvertFrom-Json
$names=@('📦 天籁小说','沧浪文学/恰猫小说','笔趣阁5','笔下小说网','笔趣阁 la','键盘小说网','笔趣阁','笔趣阁quge7','7017k小说','顶点小说maxreader.net','手打吧','无忧小说网','快书读')
$base='http://localhost:8082/api/book-sources'
foreach($n in $names){
  $s=$cand | Where-Object {$_.bookSourceName -eq $n} | Select-Object -First 1
  if($null -eq $s){ Write-Output "$n`tMISSING"; continue }
  try {
    $json=@($s)|ConvertTo-Json -Depth 10 -Compress
    Invoke-RestMethod "$base/import/json" -Method Post -ContentType 'application/json' -Body (@{json=$json}|ConvertTo-Json -Compress) | Out-Null
    $all=(Invoke-RestMethod "${base}?page=1&size=200").data.records
    $db=$all|Where-Object {$_.sourceUrl -eq $s.bookSourceUrl}|Select-Object -First 1
    $x=Invoke-RestMethod "$base/$($db.id)/search?keyword=%E5%89%91%E6%9D%A5&page=1"
    $h=@($x.data)|Where-Object {$_.name -eq '剑来'}|Select-Object -First 1
    if($null -eq $h){Write-Output "$n`tNO";continue}
    $ch=Invoke-RestMethod "$base/$($db.id)/chapters?bookUrl=$([uri]::EscapeDataString($h.bookUrl))"
    $f=@($ch.data)|Select-Object -First 1
    if($null -eq $f){Write-Output "$n`tTOC0";continue}
    $b=Invoke-RestMethod "$base/$($db.id)/content?chapterUrl=$([uri]::EscapeDataString($f.chapterUrl))"
    Write-Output "$n`tPASS`t$(@($ch.data).Count)`t$($b.data.content.Length)"
  } catch {Write-Output "$n`tERR`t$($_.Exception.Message)"}
}
