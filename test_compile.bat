@echo off
REM 爱之数字生命体 - 测试编译脚本
REM 只编译第一阶段核心，确保基本功能可用

echo 爱之数字生命体 - 最小化编译测试
echo ======================================
echo.

echo 步骤1：创建编译目录...
if not exist "build\classes" mkdir "build\classes"
echo ✓ 编译目录准备完成
echo.

echo 步骤2：编译Circle.java（圆的核心类）...
javac -d build\classes -Xlint:unchecked src\core\Circle.java
if %ERRORLEVEL% neq 0 (
    echo ✗ Circle.java编译失败
    goto :error
)
echo ✓ Circle.java编译成功
echo.

echo 步骤3：编译EmotionCore.java（情感核心）...
javac -d build\classes -cp build\classes -Xlint:unchecked src\core\EmotionCore.java
if %ERRORLEVEL% neq 0 (
    echo ✗ EmotionCore.java编译失败
    goto :error
)
echo ✓ EmotionCore.java编译成功
echo.

echo 步骤4：编译LivingAI.java（主系统）...
javac -d build\classes -cp build\classes -Xlint:unchecked src\LivingAI.java
if %ERRORLEVEL% neq 0 (
    echo ✗ LivingAI.java编译失败
    echo 尝试简化编译...
    javac -d build\classes -cp build\classes src\LivingAI.java 2>nul
    if %ERRORLEVEL% neq 0 (
        echo ✗ 简化编译也失败
        goto :error
    )
    echo ✓ LivingAI.java简化编译成功
) else (
    echo ✓ LivingAI.java编译成功
)
echo.

echo 步骤5：编译成功，准备运行...
echo.
echo ======================================
echo 第一阶段核心编译完成！
echo 现在可以运行以下命令启动系统：
echo.
echo   java -cp build\classes LivingAI
echo.
echo 或者运行完整启动器（包含第二阶段，需要额外编译）：
echo   javac -d build\classes -cp build\classes src\Launcher.java
echo   java -cp build\classes Launcher
echo.
echo 系统启动后，访问以下地址：
echo   http://localhost:8080/api/status
echo   http://localhost:8080/api/love?message=你好
echo.
echo 按Ctrl+C停止系统
echo ======================================
goto :end

:error
echo.
echo ======================================
echo 编译过程中出现错误
echo 请检查：
echo   1. Java是否安装正确（Java 11+）
echo   2. 项目的文件路径是否正确
echo   3. 是否缺少Java包
echo ======================================
pause

:end
pause