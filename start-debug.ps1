# 带 JDWP 调试端口的启动（默认 localhost:5005），供「附加调试器」使用。
# Visual Studio（紫色 IDE）本身不支持 Java 源码断点；请用 VS Code/Cursor 的「附加到 JDWP」或 IntelliJ。
#
# 用法：.\start-debug.ps1
# 环境变量：同 start.ps1（LOVINGAI_MAIN、LOVINGAI_NO_BROWSER）
# 可选：LOVINGAI_JDWP_PORT=5005（默认 5005）
#       LOVINGAI_SUSPEND=y  在 main 前暂停，直到调试器附加（便于断在启动代码）

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "未在 PATH 中找到 java。" -ForegroundColor Red
    exit 1
}

Write-Host "== LovingAI 编译 ==" -ForegroundColor Cyan
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\compile.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$main = $env:LOVINGAI_MAIN
if ([string]::IsNullOrWhiteSpace($main)) {
    $main = "com.lovingai.ui.LifeformApp"
}

$port = $env:LOVINGAI_JDWP_PORT
if ([string]::IsNullOrWhiteSpace($port)) { $port = "5005" }

$suspend = "n"
if ($env:LOVINGAI_SUSPEND -eq "y" -or $env:LOVINGAI_SUSPEND -eq "Y") {
    $suspend = "y"
}

$jdwp = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=*:$port"
Write-Host "== JDWP 监听 *:$port （suspend=$suspend）主类: $main ==" -ForegroundColor Cyan
Write-Host "在 VS Code/Cursor 中：运行和调试 -> 选「附加到 JDWP (localhost:5005)」（见 .vscode/launch.json）" -ForegroundColor Yellow
Write-Host ""

& java @($jdwp, "-cp", "build", $main)
