# 可选：限制 java.exe 出站（需管理员 PowerShell）。本机回环不受影响。
# 使用前请确认 javaw.exe/java.exe 路径；解除：Get-NetFirewallRule -DisplayName "LovingAI Java Outbound Block" | Remove-NetFirewallRule
$ErrorActionPreference = "Stop"
$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $java) {
    Write-Host "未找到 java.exe"
    exit 1
}
Write-Host "将为程序创建出站阻止规则: $java"
New-NetFirewallRule -DisplayName "LovingAI Java Outbound Block" -Direction Outbound -Program $java -Action Block -Profile Any -ErrorAction SilentlyContinue
Write-Host "完成。仅影响该 java 路径的出站连接；127.0.0.1 监听仍可用。"
