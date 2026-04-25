param(
    [Parameter(Mandatory = $true)]
    [string]$DestRoot,
    [string]$ReleaseName = ""
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ReleaseName)) {
    $ReleaseName = "LovingAI_release_" + (Get-Date -Format "yyyy-MM-dd")
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

Write-Host "== compile ==" -ForegroundColor Cyan
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $src "compile.ps1") | Out-Host
if ($LASTEXITCODE -ne 0) { throw "compile failed: $LASTEXITCODE" }

Write-Host "== copy runtime ==" -ForegroundColor Cyan
Copy-Item -Recurse -Force (Join-Path $src "build") (Join-Path $dest "build")
Copy-Item -Recurse -Force (Join-Path $src "config") (Join-Path $dest "config")
Copy-Item -Recurse -Force (Join-Path $src "docs") (Join-Path $dest "docs")
Copy-Item -Force (Join-Path $src "LICENSE") (Join-Path $dest "LICENSE")
Copy-Item -Force (Join-Path $src "README.md") (Join-Path $dest "README.md")
Copy-Item -Force (Join-Path $src "QUICK_START.md") (Join-Path $dest "QUICK_START.md")
Copy-Item -Force (Join-Path $src "CARE_GUIDE.md") (Join-Path $dest "CARE_GUIDE.md")
Copy-Item -Force (Join-Path $src "API_EXAMPLES.md") (Join-Path $dest "API_EXAMPLES.md")

Write-Host "== data template (privacy-safe) ==" -ForegroundColor Cyan
$data = Join-Path $dest "data"
New-Item -ItemType Directory -Force -Path $data | Out-Null

Copy-Item -Force (Join-Path $src "data\\localai.properties") (Join-Path $data "localai.properties")
Copy-Item -Force (Join-Path $src "data\\preferences.json") (Join-Path $data "preferences.json")
Copy-Item -Force (Join-Path $src "data\\perception.properties") (Join-Path $data "perception.properties")
Copy-Item -Force (Join-Path $src "data\\perception-sources.tsv") (Join-Path $data "perception-sources.tsv")
Copy-Item -Force (Join-Path $src "data\\web-fetch-allowlist.txt") (Join-Path $data "web-fetch-allowlist.txt")
Copy-Item -Recurse -Force (Join-Path $src "data\\protocol") (Join-Path $data "protocol")
Copy-Item -Recurse -Force (Join-Path $src "data\\registry") (Join-Path $data "registry")
Copy-Item -Recurse -Force (Join-Path $src "data\\sandbox") (Join-Path $data "sandbox")
Copy-Item -Recurse -Force (Join-Path $src "data\\perception") (Join-Path $data "perception")
Copy-Item -Recurse -Force (Join-Path $src "data\\evolution") (Join-Path $data "evolution")

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
    "philosophy-diary.md",
    "dev-log.md",
    "identity-chain.log",
    "life-snapshot-latest.json",
    "self-core.properties",
    "goal-stack.tsv"
)
foreach ($f in $emptyFiles) {
    $p = Join-Path $mem $f
    if (-not (Test-Path $p)) {
        New-Item -ItemType File -Force -Path $p | Out-Null
    }
    Clear-Content -Path $p -ErrorAction SilentlyContinue
}

Write-Host "== launchers ==" -ForegroundColor Cyan
$runGui = @"
@echo off
setlocal
cd /d "%~dp0"
set "JAVA_OPTS=-Dlovingai.dialogue.appendQuestion=false -Dlovingai.autonomy.saturation.threshold=0.99 -Dlovingai.localai.retryTimes=2 -Dlovingai.localai.retryDelayMs=1200"
if not "%LOVINGAI_JAVA_OPTS%"=="" set "JAVA_OPTS=%JAVA_OPTS% %LOVINGAI_JAVA_OPTS%"
java %JAVA_OPTS% -cp build com.lovingai.ui.LifeformApp
set ERR=%ERRORLEVEL%
if %ERR% neq 0 (
  echo.
  echo 启动失败，退出码 %ERR%
  pause
)
exit /b %ERR%
"@
Set-Content -Encoding UTF8 -Path (Join-Path $dest "run_gui.cmd") -Value $runGui

$runServer = @"
@echo off
setlocal
cd /d "%~dp0"
set "JAVA_OPTS=-Dlovingai.dialogue.appendQuestion=false -Dlovingai.autonomy.saturation.threshold=0.99 -Dlovingai.localai.retryTimes=2 -Dlovingai.localai.retryDelayMs=1200"
if not "%LOVINGAI_JAVA_OPTS%"=="" set "JAVA_OPTS=%JAVA_OPTS% %LOVINGAI_JAVA_OPTS%"
java %JAVA_OPTS% -cp build com.lovingai.LivingAI
set ERR=%ERRORLEVEL%
if %ERR% neq 0 (
  echo.
  echo 启动失败，退出码 %ERR%
  pause
)
exit /b %ERR%
"@
Set-Content -Encoding UTF8 -Path (Join-Path $dest "run_server.cmd") -Value $runServer

$note = @"
发布包说明

1) 需要 Java 17+（JRE/JDK 均可），并确保命令行能运行 java。
2) LM Studio：先启动 Local Server，再运行 run_gui.cmd。
3) data/memory 是本机私有数据目录；本包已清空作者本机的对话/记忆/日志，避免泄露。

可用环境变量：
- LOVINGAI_JAVA_OPTS：追加 JVM 参数，例如：
  -Dlovingai.localai.maxTokens=2048 -Dlovingai.localai.progressLogSec=2
"@
Set-Content -Encoding UTF8 -Path (Join-Path $dest "RELEASE_README.txt") -Value $note

Write-Host "== zip ==" -ForegroundColor Cyan
$zip = Join-Path $DestRoot ($ReleaseName + ".zip")
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $dest "*") -DestinationPath $zip -Force

Write-Host "OK:" $dest
Write-Host "OK:" $zip
