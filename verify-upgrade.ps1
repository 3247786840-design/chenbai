param(
  [int]$Rounds = 200
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

& .\compile.ps1 | Out-Host

$samplesPath = Join-Path $PSScriptRoot "tests\upgrade-samples.tsv"
if (-not (Test-Path $samplesPath)) {
  throw "missing samples file: $samplesPath"
}

$lines = Get-Content -Path $samplesPath -Encoding UTF8
if ($lines.Count -lt 2) { throw "samples file empty" }

$samples = @()
for ($i = 1; $i -lt $lines.Count; $i++) {
  $ln = $lines[$i]
  if ([string]::IsNullOrWhiteSpace($ln)) { continue }
  $p = $ln -split "`t", -1
  if ($p.Count -lt 7) { continue }
  $samples += [pscustomobject]@{
    sampleId = $p[0].Trim()
    scenario = $p[1].Trim()
    messageKind = $p[2].Trim()
    useLocalLlm = ($p[3].Trim().ToLowerInvariant() -eq "true")
    localaiBase = $p[4].Trim()
    useImported = ($p[5].Trim().ToLowerInvariant() -eq "true")
    expressionMode = $p[6].Trim()
  }
}
if ($samples.Count -lt 1) { throw "no samples parsed" }

$base = "http://127.0.0.1:8080"
$proc = $null

function New-LongNarrative([int]$minChars) {
  $chunk = @(
    "This is a long-input pressure sample. It is not trying to be correct; it is trying to be continuous."
    "It contains hesitation, revision, and a question that appears only near the end."
    "It is intentionally formatted like a pasted narrative: multiple paragraphs, pauses, and imperfect framing."
    ""
    "Paragraph 1: a scene — wind outside, light inside, and a decision that is still not grounded."
    "Paragraph 2: the contradiction — I want closeness, but I fear using closeness as a bandage."
    "Paragraph 3: leave a thread — if you can only respond to one point, which one will you choose?"
    ""
    "End: give one clear response sentence first, then ask me one question you genuinely care about."
  ) -join "`n"
  $sb = New-Object System.Text.StringBuilder
  while ($sb.Length -lt $minChars) {
    [void]$sb.Append($chunk)
    [void]$sb.Append("`n`n")
  }
  return $sb.ToString().Substring(0, [Math]::Min($sb.Length, $minChars + 80))
}

function Build-Message([string]$kind) {
  switch ($kind) {
    "short_q" { return "Are you here?" }
    "paraphrase" { return "Paraphrase your last answer without losing details, then tell me the next step you would take." }
    "long_narrative" { return (New-LongNarrative 1200) }
    "disconnect_short" { return "Model unreachable test. Reply with one sentence and ask me one question. My name is Alice." }
    "disconnect_long" { return (New-LongNarrative 1400) }
    default { return "Hello." }
  }
}

function Ensure-Server {
  try {
    $existing = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
  } catch {
    $existing = $null
  }
  if ($existing) { return $null }
  $p = Start-Process -FilePath "java" `
    -ArgumentList @("-cp", "build", "com.lovingai.LivingAI") `
    -WorkingDirectory $PSScriptRoot `
    -PassThru `
    -WindowStyle Hidden
  for ($i = 0; $i -lt 120; $i++) {
    Start-Sleep -Milliseconds 750
    try {
      Invoke-RestMethod -Uri "$base/api/status" -Method Get -TimeoutSec 3 | Out-Null
      return $p
    } catch {
    }
  }
  try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
  throw "server did not become ready"
}

$proc = Ensure-Server

$ok = 0
$fail = 0
$failSamples = New-Object System.Collections.Generic.List[string]
$runId = "baselineRun_" + (Get-Date).ToString("yyyyMMdd_HHmmss_fff")

try {
  for ($i = 0; $i -lt $Rounds; $i++) {
    $s = $samples[$i % $samples.Count]
    $msg = Build-Message $s.messageKind
    $cid = $runId + "_" + $s.sampleId
    $body = @{
      message = $msg
      conversationId = $cid
      useImported = $s.useImported
      useLocalLlm = $s.useLocalLlm
      expressionMode = $s.expressionMode
    }
    if ($s.useLocalLlm -and -not [string]::IsNullOrWhiteSpace($s.localaiBase)) {
      $body.localaiBase = $s.localaiBase
    }
    try {
      $r = Invoke-RestMethod -Uri "$base/api/chat" -Method Post -TimeoutSec 30 `
        -ContentType "application/json" `
        -Body ($body | ConvertTo-Json -Compress)
      if ($null -eq $r.response -or [string]::IsNullOrWhiteSpace([string]$r.response)) {
        $fail++
        $failSamples.Add("empty_response round=$i sample=$($s.sampleId) kind=$($s.messageKind)")
      } else {
        $ok++
      }
    } catch {
      $fail++
      $failSamples.Add("exception round=$i sample=$($s.sampleId) kind=$($s.messageKind) err=$($_.Exception.Message)")
    }
  }

  $metrics = Invoke-RestMethod -Uri "$base/api/observe/upgrade/metrics?runId=$runId&limit=60000" -Method Get -TimeoutSec 10

  Write-Host "OK rounds=$ok fail=$fail total=$Rounds"
  Write-Host ("metrics runId={0} dialogueStarted={1} fallbackContinuityRate={2} overSuppressionRateLongInput={3} dupRate={4} falseFigureExtractRate={5}" -f `
    $metrics.runId, $metrics.dialogueStarted, $metrics.fallbackContinuityRate, $metrics.overSuppressionRateLongInputWhenUnreachable, $metrics.duplicateRequestRate, $metrics.falseFigureExtractRate)

  if ($failSamples.Count -gt 0) {
    $n = [Math]::Min(8, $failSamples.Count)
    Write-Host "failSamples (first $n):"
    for ($k = 0; $k -lt $n; $k++) { Write-Host ("  " + $failSamples[$k]) }
  }
} finally {
  if ($null -ne $proc -and -not $proc.HasExited) {
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch {}
  }
}
