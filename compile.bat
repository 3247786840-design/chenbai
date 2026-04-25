@echo off
REM 爱之数字生命体 - 编译（委托 compile.ps1，输出到 build\）
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0compile.ps1"
exit /b %ERRORLEVEL%
