param(
    [Parameter(Mandatory = $true)]
    [string]$DestRoot,
    [string]$ReleaseName = ""
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ReleaseName)) {
    $ReleaseName = "LovingAI_github_" + (Get-Date -Format "yyyyMMdd_HHmmss")
}

if (-not (Test-Path $DestRoot)) {
    New-Item -ItemType Directory -Force -Path $DestRoot | Out-Null
}

$src = $PSScriptRoot
$dest = Join-Path $DestRoot $ReleaseName

if (Test-Path $dest) {
    Remove-Item -Recurse -Force $dest
}
New-Item -ItemType Directory -Force -Path $dest | Out-Null

Write-Host "== copy source tree (privacy-safe) ==" -ForegroundColor Cyan

foreach ($item in Get-ChildItem -LiteralPath $src -Force) {
    if ($item.Name -in @("build", ".vs")) { continue }
    if ($item.Name -eq "data") { continue }
    Copy-Item -Recurse -Force -LiteralPath $item.FullName -Destination (Join-Path $dest $item.Name)
}

Write-Host "== data templates (sanitized) ==" -ForegroundColor Cyan

$data = Join-Path $dest "data"
New-Item -ItemType Directory -Force -Path $data | Out-Null

if (Test-Path (Join-Path $src "data\\localai.properties")) {
    Copy-Item -Force (Join-Path $src "data\\localai.properties") (Join-Path $data "localai.properties")
}
if (Test-Path (Join-Path $src "data\\preferences.json")) {
    Copy-Item -Force (Join-Path $src "data\\preferences.json") (Join-Path $data "preferences.json")
}
if (Test-Path (Join-Path $src "data\\perception.properties")) {
    Copy-Item -Force (Join-Path $src "data\\perception.properties") (Join-Path $data "perception.properties")
}
if (Test-Path (Join-Path $src "data\\perception-sources.tsv")) {
    Copy-Item -Force (Join-Path $src "data\\perception-sources.tsv") (Join-Path $data "perception-sources.tsv")
}
if (Test-Path (Join-Path $src "data\\web-fetch-allowlist.txt")) {
    Copy-Item -Force (Join-Path $src "data\\web-fetch-allowlist.txt") (Join-Path $data "web-fetch-allowlist.txt")
}
foreach ($dir in @("protocol", "registry", "sandbox", "evolution")) {
    $p = Join-Path $src ("data\\" + $dir)
    if (Test-Path $p) {
        Copy-Item -Recurse -Force -LiteralPath $p -Destination (Join-Path $data $dir)
    }
}
if (Test-Path (Join-Path $src "data\\perception")) {
    Copy-Item -Recurse -Force -LiteralPath (Join-Path $src "data\\perception") -Destination (Join-Path $data "perception")
    $usb = Join-Path $data "perception\\usb-inbox"
    if (Test-Path $usb) { Remove-Item -Recurse -Force $usb }
}

$mem = Join-Path $data "memory"
New-Item -ItemType Directory -Force -Path $mem | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $mem "collapse-episodes") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $mem "backups") | Out-Null

$emptyFiles = @(
    "conversation-state.tsv",
    "observe-events.ndjson",
    "observe-linear-memory.tsv",
    "observe-expression-daily.tsv",
    "observe-answer-visibility-daily.tsv",
    "observe-metrics-daily.tsv",
    "life-completion-daily.tsv",
    "choice-ledger.ndjson",
    "world-figures.tsv",
    "world-figures-pending.tsv",
    "philosophy-diary.md",
    "dev-log.md",
    "identity-chain.log",
    "life-snapshot-latest.json",
    "bridge.log"
)
foreach ($f in $emptyFiles) {
    $p = Join-Path $mem $f
    if (-not (Test-Path $p)) {
        New-Item -ItemType File -Force -Path $p | Out-Null
    }
    Clear-Content -Path $p -ErrorAction SilentlyContinue
}

if (Test-Path (Join-Path $src "data\\memory\\collapse-episodes\\README.md")) {
    Copy-Item -Force (Join-Path $src "data\\memory\\collapse-episodes\\README.md") (Join-Path $mem "collapse-episodes\\README.md")
}
if (-not (Test-Path (Join-Path $mem "collapse-episodes\\README.md"))) {
    New-Item -ItemType File -Force -Path (Join-Path $mem "collapse-episodes\\README.md") | Out-Null
    Set-Content -Encoding UTF8 -Path (Join-Path $mem "collapse-episodes\\README.md") -Value @(
        "This folder is intentionally empty in the open-source package.",
        "Put your own collapse-episode notes here if needed."
    )
}

$reality = Join-Path $data "reality"
New-Item -ItemType Directory -Force -Path $reality | Out-Null
$bridge = Join-Path $reality "bridge.log"
if (-not (Test-Path $bridge)) { New-Item -ItemType File -Force -Path $bridge | Out-Null }
Clear-Content -Path $bridge -ErrorAction SilentlyContinue

if (Test-Path (Join-Path $src "data\\perception\\usb-inbox\\README.txt")) {
    $usb = Join-Path $data "perception\\usb-inbox"
    New-Item -ItemType Directory -Force -Path $usb | Out-Null
    Copy-Item -Force (Join-Path $src "data\\perception\\usb-inbox\\README.txt") (Join-Path $usb "README.txt")
}

Write-Host "== zip ==" -ForegroundColor Cyan
$zip = Join-Path $DestRoot ($ReleaseName + ".zip")
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $dest "*") -DestinationPath $zip -Force

Write-Host "OK:" $dest
Write-Host "OK:" $zip
