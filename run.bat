@echo off
REM 爱之数字生命体 - 运行（包名：com.lovingai.*）
setlocal
cd /d "%~dp0"

echo 爱之数字生命体 运行脚本 v2.1
echo ==============================
echo.

if not exist "build\com\lovingai\LivingAI.class" (
    echo 错误：尚未编译。请先运行 compile.bat 或 .\compile.ps1
    pause
    exit /b 1
)

set CLASSPATH=build
if exist "lib\*.jar" set CLASSPATH=build;lib\*

if "%1"=="--phase1-only" (
    echo 模式：仅第一阶段（主类 LivingAI）
    java -cp "%CLASSPATH%" com.lovingai.LivingAI
    goto :end
)

if "%1"=="--no-phase2" (
    echo 模式：启动器，禁用第二阶段心跳
    java -cp "%CLASSPATH%" com.lovingai.Launcher --no-phase2
    goto :end
)

echo 模式：启动器（第一阶段 + 第二阶段心跳）
java -cp "%CLASSPATH%" com.lovingai.Launcher

:end
echo.
pause
