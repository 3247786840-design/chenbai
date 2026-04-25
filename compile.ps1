$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force -Path build | Out-Null
$files = Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -sourcepath src -d build @files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "OK: compiled to .\build"
