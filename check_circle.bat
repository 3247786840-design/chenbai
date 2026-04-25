@echo off
echo ============ 检查Circle.java编译 ============
echo 检查文件：src\core\Circle.java

REM 检查文件是否存在
if not exist "src\core\Circle.java" (
    echo ✗ 文件不存在：src\core\Circle.java
    exit /b 1
)

echo ✓ 文件存在
echo 文件大小：%~z0 bytes

REM 尝试编译到nul（不输出，只检查语法）
echo 执行编译检查：javac -d nul "src\core\Circle.java"
javac -d nul "src\core\Circle.java" 2>compile_errors.txt

if %ERRORLEVEL% equ 0 (
    echo ✅ Circle.java 编译检查通过 (Java)
    del compile_errors.txt 2>nul
) else (
    echo ✗ Circle.java 编译检查失败
    type compile_errors.txt
    del compile_errors.txt 2>nul
)

REM 同时检查简单编译到输出目录
echo.
echo ============ 实际编译测试 ============
mkdir build\test_classes 2>nul
javac -d build\test_classes "src\core\Circle.java" 2>actual_compile_errors.txt

if %ERRORLEVEL% equ 0 (
    echo ✅ Circle.java 实际编译成功
    echo 生成的class文件：
    dir /b build\test_classes\core\*.class
    del actual_compile_errors.txt 2>nul
) else (
    echo ✗ Circle.java 实际编译失败
    type actual_compile_errors.txt
    del actual_compile_errors.txt 2>nul
)

echo.
echo ============ 检查完成 ============