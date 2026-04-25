$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

& .\compile.ps1 | Out-Host

$base = "http://127.0.0.1:8080"
$proc = $null

function Assert-True($cond, $msg) {
    if (-not $cond) { throw $msg }
}

function Probe-CoreEndpoints {
    $manifest = Invoke-RestMethod -Uri "$base/api/identity/manifest" -Method Get -TimeoutSec 4
    Assert-True ($null -ne $manifest.entityName -and "$($manifest.entityName)".Length -gt 0) "manifest.entityName empty"

    $status = Invoke-RestMethod -Uri "$base/api/status" -Method Get -TimeoutSec 4
    Assert-True ($null -ne $status.httpPort) "status.httpPort missing"

    $obs = Invoke-RestMethod -Uri "$base/api/observe/global?topN=3" -Method Get -TimeoutSec 4
    Assert-True ($null -ne $obs.conversationCount) "observe.global missing conversationCount"

    $conv = Invoke-RestMethod -Uri "$base/api/observe/conversation?conversationId=default&timelineLimit=4" -Method Get -TimeoutSec 4
    Assert-True ($null -ne $conv.timeline) "observe.conversation missing timeline"

    $metrics = Invoke-RestMethod -Uri "$base/api/observe/metrics/daily?limit=20" -Method Get -TimeoutSec 4
    Assert-True ($null -ne $metrics.total) "observe.metrics.daily missing total"

    $snap = Invoke-RestMethod -Uri "$base/api/life/snapshot/verify" -Method Get -TimeoutSec 4
    Assert-True ($null -ne $snap.snapshotExists) "snapshot.verify missing snapshotExists"

    Write-Host "OK: smoke endpoints validated."
}

try {
    $existing = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
} catch {
    $existing = $null
}

if (-not $existing) {
    $proc = Start-Process -FilePath "java" `
        -ArgumentList @("-cp", "build", "com.lovingai.LivingAI") `
        -WorkingDirectory $PSScriptRoot `
        -PassThru `
        -WindowStyle Hidden
}
if ($existing) {
    Write-Host "Port 8080 already occupied, run compile-only smoke with best-effort probe."
    try {
        $null = Invoke-RestMethod -Uri "$base/api/status" -Method Get -TimeoutSec 2
    } catch {
        Write-Host "WARN: existing 8080 service not probeable, skip runtime probe."
    }
    exit 0
}

try {
    $ok = $false
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Milliseconds 750
        try {
            Probe-CoreEndpoints
            $ok = $true
            break
        } catch {
        }
    }
    Assert-True $ok "smoke probe timeout"
} finally {
    if ($null -ne $proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
}
