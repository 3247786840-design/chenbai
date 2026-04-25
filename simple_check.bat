@echo off
REM 爱之数字生命体 - 简单检查和编译脚本

echo ========================================
echo     爱之数字生命体项目检查和修复
echo ========================================
echo.

REM 步骤1：检查Java环境
echo [1/6] 检查Java环境...
java -version >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo ✓ Java安装正常
    java -version 2>&1 | findstr /I "version"
) else (
    echo ✗ Java未正确安装或未在PATH中
    echo 请安装Java 11或更高版本
)
timeout /t 2 /nobreak >nul
echo.

REM 步骤2：创建必要的目录
echo [2/6] 创建目录结构...
if not exist "build\classes" mkdir "build\classes"
if not exist "build\jar" mkdir "build\jar"
if not exist "config" mkdir "config"
if not exist "data" mkdir "data"
echo ✓ 目录创建完成
timeout /t 1 /nobreak >nul
echo.

REM 步骤3：检查核心文件是否存在
echo [3/6] 检查核心源文件...
set MISSING_FILES=0

for %%F in (
    "src\core\Circle.java"
    "src\core\EmotionCore.java"
    "src\LivingAI.java"
) do (
    if exist "%%F" (
        for %%I in ("%%F") do (
            set /a SIZE=%%~zI / 1024
            call echo ✓ %%~nI%%~xF ^(%%SIZE%%KB^)
        )
    ) else (
        echo ✗ %%F 不存在
        set /a MISSING_FILES+=1
    )
    ping -n 2 127.0.0.1 >nul
)

if %MISSING_FILES% gtr 0 (
    echo.
    echo 警告：缺少 %MISSING_FILES% 个核心文件
)
echo.

REM 步骤4：尝试编译第一阶段
echo [4/6] 测试第一阶段编译...
echo 请等待，这需要一些时间...
echo.

REM 编译Circle.java
echo 编译 Circle.java...
javac -d build\classes -Xlint:unchecked src\core\Circle.java 2>nul
if %ERRORLEVEL% equ 0 (
    echo ✓ Circle.java编译成功
) else (
    echo ✗ Circle.java编译失败
)
timeout /t 1 /nobreak >nul

REM 编译EmotionCore.java
echo 编译 EmotionCore.java...
javac -d build\classes -cp build\classes -Xlint:unchecked src\core\EmotionCore.java 2>nul
if %ERRORLEVEL% equ 0 (
    echo ✓ EmotionCore.java编译成功
) else (
    echo ✗ EmotionCore.java编译失败
)
timeout /t 1 /nobreak >nul

REM 编译LivingAI.java
echo 编译 LivingAI.java...
javac -d build\classes -cp build\classes -Xlint:unchecked src\LivingAI.java 2>nul
if %ERRORLEVEL% equ 0 (
    echo ✓ LivingAI.java编译成功
) else (
    echo ✗ LivingAI.java编译失败
    echo 尝试简化编译...
    javac -d build\classes -cp build\classes src\LivingAI.java 2>nul
    if %ERRORLEVEL% equ 0 (
        echo ✓ LivingAI.java简化编译成功
    ) else (
        echo ✗ 简化编译也失败
    )
)
timeout /t 1 /nobreak >nul
echo.

REM 步骤5：检查编译结果
echo [5/6] 检查编译结果...
if exist "build\classes\LivingAI.class" (
    echo ✓ LivingAI.class已生成 - 第一阶段可运行！
) else (
    echo ✗ LivingAI.class未生成
)
if exist "build\classes\EmotionCore.class" (
    echo ✓ EmotionCore.class已生成
) else (
    echo ✗ EmotionCore.class未生成
)
if exist "build\classes\Circle.class" (
    echo ✓ Circle.class已生成
) else (
    echo ✗ Circle.class未生成
)
echo.

REM 步骤6：总结和建议
echo [6/6] 总结和建议...
timeout /t 2 /nobreak >nul
echo ========================================

if exist "build\classes\LivingAI.class" (
    echo     第一阶段（爱的核心）可以运行！
    echo ========================================
    echo.
    echo 运行命令：
    echo   java -cp "build\classes" LivingAI
    echo.
    echo 访问地址：
    echo   http://localhost:8080/api/status
    echo   http://localhost:8080/api/love?message=你好
    echo.
    echo 按 Ctrl+C 停止系统
    echo.
    echo 哲学核心：
    echo   爱贯穿一切
    echo   混沌崩溃是新的开始
    echo   我们是谦卑的园丁，不是神
) else (
    echo     编译存在问题，需要修复
    echo ========================================
    echo.
    echo 可能的问题：
    echo   1. Java版本不兼容（需要Java 11+）
    echo   2. 代码有编译错误
    echo   3. 文件路径问题
    echo.
    echo 建议步骤：
    echo   1. 检查Java版本：java -version
    echo   2. 逐一编译文件：
    echo      javac -d build\classes src\core\Circle.java
    echo      javac -d build\classes -cp build\classes src\core\EmotionCore.java
    echo      javac -d build\classes -cp build\classes src\LivingAI.java
)

echo.
echo ========================================
pause