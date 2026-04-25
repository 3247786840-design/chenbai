# LovingAI 一键启动：编译 → 启动 Java（默认 Swing 界面 + HTTP :8080）→ 可选打开 /talk
# 用法：在资源管理器中双击 start.cmd，或：powershell -File .\start.ps1
# 环境变量：
#   LOVINGAI_MAIN      主类，默认 com.lovingai.ui.LifeformApp；仅后台可设为 com.lovingai.LivingAI
#   LOVINGAI_NO_BROWSER=1  不自动打开浏览器
#   LOVINGAI_PAUSE=1       结束前按任意键（便于看编译输出）
#   LOVINGAI_JAVA_OPTS  传入 JVM 参数（如 -Dlovingai.proactive.selfSolveTimeoutSec=60）
#                       工作目录不在项目根时：-Dlovingai.data.root=...\LovingAI\data

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "未在 PATH 中找到 java。请安装 JDK（建议 17）并将 bin 加入系统 PATH。" -ForegroundColor Red
    if ($env:LOVINGAI_PAUSE -eq "1") { [void][System.Console]::ReadKey($true) }
    exit 1
}

Write-Host "== LovingAI 编译 ==" -ForegroundColor Cyan
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\compile.ps1
if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败，退出码 $LASTEXITCODE" -ForegroundColor Red
    if ($env:LOVINGAI_PAUSE -eq "1") { [void][System.Console]::ReadKey($true) }
    exit $LASTEXITCODE
}

$main = $env:LOVINGAI_MAIN
if ([string]::IsNullOrWhiteSpace($main)) {
    $main = "com.lovingai.ui.LifeformApp"
}

Write-Host "== 启动 $main （工作目录: $PSScriptRoot）==" -ForegroundColor Cyan
$args = @()
# 针对 LM Studio 的默认稳态参数（优先保证长输入不断连、辅助链持续可用）。
$defaultJavaOpts = @(
    "-Dlovingai.autonomy.saturation.threshold=0.99",
    "-Dlovingai.localai.retryTimes=2",
    "-Dlovingai.localai.retryDelayMs=1200",
    "-Dlovingai.dialogue.primaryPromptSoftMaxChars=24000",
    "-Dlovingai.localai.temperature=1.0",
    "-Dlovingai.localai.topP=0.95"
)
$args += $defaultJavaOpts
if (-not [string]::IsNullOrWhiteSpace($env:LOVINGAI_JAVA_OPTS)) {
    $args += ($env:LOVINGAI_JAVA_OPTS -split "\s+")
}
$args += @("-cp", "build", $main)
$proc = Start-Process -FilePath "java" -ArgumentList $args -WorkingDirectory $PSScriptRoot -PassThru

if ($env:LOVINGAI_NO_BROWSER -ne "1") {
    Start-Sleep -Seconds 2
    $talk = "http://127.0.0.1:8080/talk"
    Write-Host "打开浏览器: $talk" -ForegroundColor DarkGray
    Start-Process $talk
}

Write-Host "已启动。关闭 Java 窗口即结束进程。" -ForegroundColor Green
Write-Host "对话页: http://127.0.0.1:8080/talk  | 状态: http://127.0.0.1:8080/api/status"

if ($env:LOVINGAI_PAUSE -eq "1") {
    Write-Host "按任意键关闭此窗口…"
    [void][System.Console]::ReadKey($true)
}
