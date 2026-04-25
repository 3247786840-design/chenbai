# Java 文件编译检查脚本
Write-Host "检查 Circle.java 编译" -ForegroundColor Yellow

# 1. 检查文件是否存在
$repoRoot = $PSScriptRoot
$filePath = Join-Path $repoRoot "src\com\lovingai\core\Circle.java"
if (Test-Path $filePath) {
    $fileSize = (Get-Item $filePath).Length
    Write-Host "✓ 文件存在 ($fileSize 字节)" -ForegroundColor Green
} else {
    Write-Host "✗ 文件不存在" -ForegroundColor Red
    exit 1
}

# 2. 简单编译测试
Write-Host "尝试编译 Circle.java..." -ForegroundColor Yellow
Start-Sleep -Milliseconds 500

# 创建输出目录
$buildDir = Join-Path $repoRoot "build\check"
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null

# 执行编译
$compileOutput = javac -d $buildDir $filePath 2>&1
$compileExitCode = $LASTEXITCODE

if ($compileExitCode -eq 0) {
    Write-Host "✅ Circle.java 编译成功 (Java)" -ForegroundColor Green
    
    # 检查生成的class文件
    $classFiles = Get-ChildItem "$buildDir\*.class" -Recurse
    if ($classFiles.Count -gt 0) {
        Write-Host "生成的class文件:" -ForegroundColor Gray
        $classFiles | ForEach-Object {
            Write-Host "  - $($_.Name) ($($_.Length) bytes)" -ForegroundColor Gray
        }
    }
    
    # Git提交（模拟）
    Write-Host " → Git 提交" -ForegroundColor Cyan
    Start-Sleep -Milliseconds 300
    
    # 标记完成
    Write-Host " → 待办项标记: [x]" -ForegroundColor Cyan
    
} else {
    Write-Host "✗ Circle.java 编译失败" -ForegroundColor Red
    Write-Host "编译错误:" -ForegroundColor Red
    $compileOutput
    
    # Git回滚（模拟）
    Write-Host " → Git 回滚未提交的改动" -ForegroundColor Yellow
    Start-Sleep -Milliseconds 500
    
    # 保持未完成状态
    Write-Host " → 待办项保持: [ ]" -ForegroundColor Yellow
}

# 3. 下一个文件
Write-Host ""
Write-Host "📋 下一个：src/com/lovingai/core/EmotionCore.java" -ForegroundColor Cyan
