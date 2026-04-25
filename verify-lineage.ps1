$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

& .\compile.ps1 | Out-Host

$base = "http://127.0.0.1:8080"
$proc = $null

function Assert-True($cond, $msg) {
  if (-not $cond) { throw $msg }
}

function Probe-Lineage {
  $snapExport = Invoke-RestMethod -Uri "$base/api/life/snapshot/export" -Method Post -TimeoutSec 8
  Assert-True ($snapExport.ok -eq $true) "snapshot export failed"
  Assert-True ($null -ne $snapExport.bundlePath) "snapshot export missing bundlePath"

  $snapList = Invoke-RestMethod -Uri ($base + "/api/life/snapshot/list?limit=20") -Method Get -TimeoutSec 6
  Assert-True ($null -ne $snapList.count) "snapshot list missing count"
  $hasBundle = $false
  foreach ($it in $snapList.items) {
    try {
      if ($null -ne $it.name -and $it.name -like "life-lineage-*") { $hasBundle = $true }
    } catch { }
  }
  Assert-True $hasBundle "snapshot list should include life-lineage bundle"

  $restore = Invoke-RestMethod -Uri ($base + "/api/life/snapshot/restore") -Method Post -TimeoutSec 10 `
    -ContentType "application/x-www-form-urlencoded" `
    -Body "mode=core"
  Assert-True ($restore.ok -eq $true) "snapshot restore failed"

  $reseed = Invoke-RestMethod -Uri ($base + "/api/life/reseed") -Method Post -TimeoutSec 10 `
    -ContentType "application/x-www-form-urlencoded" `
    -Body "keepCore=true&keepFigures=true"
  Assert-True ($reseed.ok -eq $true) "reseed failed"

  Write-Host "OK: lineage snapshot/list/restore/reseed validated."
}

try {
  $existing = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
} catch {
  $existing = $null
}

if ($existing) {
  throw "Port 8080 already occupied. Stop the running service first."
}

$proc = Start-Process -FilePath "java" `
  -ArgumentList @("-cp", "build", "com.lovingai.LivingAI") `
  -WorkingDirectory $PSScriptRoot `
  -PassThru `
  -WindowStyle Hidden

try {
  $ok = $false
  for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Milliseconds 500
    try {
      $null = Invoke-RestMethod -Uri "$base/api/status" -Method Get -TimeoutSec 2
      Probe-Lineage
      $ok = $true
      break
    } catch { }
  }
  Assert-True $ok "lineage probe timeout"
} finally {
  if ($null -ne $proc -and -not $proc.HasExited) {
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
  }
}

