$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "== verify-release: smoke =="
& .\verify-smoke.ps1 | Out-Host

Write-Host "== verify-release: regression =="
& .\verify-regression.ps1 | Out-Host

Write-Host "== verify-release: contracts =="
& .\verify-contracts.ps1 | Out-Host

Write-Host "== verify-release: dream-laws =="
& .\verify-dreamlaws.ps1 | Out-Host

Write-Host "OK: verify-release (smoke + regression + contracts + dream-laws)"
