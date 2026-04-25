$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

& .\compile.ps1 | Out-Host

$base = "http://127.0.0.1:8080"
$proc = $null

function Assert-True($cond, $msg) {
    if (-not $cond) { throw $msg }
}

function Normalize-JsonObject {
    param([object]$Value)
    if ($null -eq $Value) { return $null }
    $current = $Value
    for ($i = 0; $i -lt 3; $i++) {
        if (-not ($current -is [string])) { break }
        $s = $current.Trim()
        if ($s.StartsWith("{") -or $s.StartsWith("[") -or $s.StartsWith('"')) {
            try {
                $next = $s | ConvertFrom-Json -Depth 20
                if ($null -eq $next) { break }
                $current = $next
                continue
            } catch {
                break
            }
        }
        break
    }
    return $current
}

function Invoke-JsonPost {
    param(
        [string]$Url,
        [hashtable]$Body,
        [int]$TimeoutSec = 20
    )
    $json = $Body | ConvertTo-Json -Depth 6
    $raw = Invoke-RestMethod -Uri $Url -Method Post -Body $json -ContentType "application/json" -TimeoutSec $TimeoutSec
    return Normalize-JsonObject $raw
}

function Invoke-TextGet {
    param(
        [string]$Url,
        [int]$TimeoutSec = 10
    )
    return [string](Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec $TimeoutSec)
}

function Test-ChatContract {
    $url = "$base/api/chat"
    $resp = Invoke-JsonPost -Url $url -Body @{
        message        = "hello, give a short self-introduction"
        conversationId = "contract-chat-default"
        expressionMode = "auto"
        useImported    = "false"
        useLocalLlm    = "false"
    } -TimeoutSec 45

    if ($null -eq $resp) { throw "chat response is null" }

    $text = ""
    if ($null -ne $resp.response) {
        $text = [string]$resp.response
    } elseif ($null -ne $resp.reply) {
        $text = [string]$resp.reply
    }
    Assert-True (-not [string]::IsNullOrWhiteSpace($text)) "chat response empty"
}

function Test-ObserveContracts {
    $globalRaw = Invoke-TextGet -Url ($base + "/api/observe/global?topN=5") -TimeoutSec 6
    Assert-True ($globalRaw -match '(?i)(\"conversationCount\"\s*:|conversationCount\s*=)') "observe.global.conversationCount missing"

    $convRaw = Invoke-TextGet -Url ($base + "/api/observe/conversation?conversationId=contract-chat-default&timelineLimit=8") -TimeoutSec 6
    Assert-True ($convRaw -match '(?i)(\"timeline\"\s*:|timeline\s*=)') "observe.conversation.timeline missing"

    $metricsRaw = Invoke-TextGet -Url ($base + "/api/observe/metrics/daily?limit=10") -TimeoutSec 6
    Assert-True ($metricsRaw -match '(?i)(\"total\"\s*:|total\s*=)') "observe.metrics.total missing"
}

function Test-RecoveryContract {
    $verify = Normalize-JsonObject (Invoke-RestMethod -Uri ($base + "/api/life/recovery/verify") -Method Get -TimeoutSec 6)
    Assert-True ($null -ne $verify.restoreReady) "recovery.verify.restoreReady missing"
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
    Write-Host "Port 8080 already occupied, run contracts probe only."
}

try {
    $ready = $false
    for ($i = 0; $i -lt 80; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            $null = Invoke-RestMethod -Uri ($base + "/api/status") -Method Get -TimeoutSec 3
            $ready = $true
            break
        } catch {
        }
    }
    Assert-True $ready "status endpoint not ready for contract tests"

    Test-ChatContract
    Test-ObserveContracts
    Test-RecoveryContract

    Write-Host "OK: contract tests for chat/observe/recovery passed."
} finally {
    if ($null -ne $proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
}

