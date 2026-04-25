@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
set ERR=%ERRORLEVEL%
if %ERR% neq 0 (
  echo.
  echo 启动失败，退出码 %ERR%
  pause
)
exit /b %ERR%
