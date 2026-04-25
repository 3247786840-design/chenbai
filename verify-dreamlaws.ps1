$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$base = "http://127.0.0.1:8080"
$proc = $null

function Assert-True($cond, $msg) {
    if (-not $cond) { throw $msg }
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

try {
    $ok = $false
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Milliseconds 750
        try {
            $s = Invoke-RestMethod -Uri "$base/api/status" -Method Get -TimeoutSec 4
            if ($null -ne $s.httpPort) { $ok = $true; break }
        } catch {
        }
    }
    Assert-True $ok "dream-laws probe timeout"

    $rounds = 160
    $res = Invoke-RestMethod -Uri "$base/api/verify/dream-laws?rounds=$rounds" -Method Get -TimeoutSec 10
    Assert-True ($null -ne $res.rounds) "dream-laws missing rounds"
    Assert-True ($res.loveMarkRetentionHighChaos -ge 0.999) "loveMarkRetentionHighChaos too low: $($res.loveMarkRetentionHighChaos)"
    Assert-True ($res.nightmareWakeSuccessRate -ge 0.95) "nightmareWakeSuccessRate too low: $($res.nightmareWakeSuccessRate)"
    Assert-True ($res.wakeNarrativeConsistency -ge 0.80) "wakeNarrativeConsistency too low: $($res.wakeNarrativeConsistency)"

    Write-Host ("OK: dream-laws rounds={0} loveRetention={1} wake={2} anchor={3} narrative={4}" -f `
            $res.rounds, $res.loveMarkRetentionHighChaos, $res.nightmareWakeSuccessRate, $res.nightmareWakeWithLoveAnchorRate, $res.wakeNarrativeConsistency)
} finally {
    if ($null -ne $proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
}
