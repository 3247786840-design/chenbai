# 爱之数字生命体 - 逐步检查和修复脚本
# 详细延缓执行，避免错误

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "    爱之数字生命体项目检查和修复" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 项目根目录
$repoRoot = $PSScriptRoot
cd $repoRoot

# 第一步：检查Java环境
Write-Host "[1/6] 检查Java环境..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-String -Pattern "version"
    if ($javaVersion) {
        Write-Host "✓ Java安装正常" -ForegroundColor Green
        Write-Host "  $javaVersion" -ForegroundColor Gray
    } else {
        Write-Host "✗ 未找到Java或版本信息" -ForegroundColor Red
        Write-Host "  Java可能未正确安装或未在PATH中" -ForegroundColor Yellow
    }
} catch {
    Write-Host "✗ 检查Java时出现异常: $_" -ForegroundColor Red
}
Start-Sleep -Seconds 2

# 第二步：检查项目目录结构
Write-Host ""
Write-Host "[2/6] 检查项目目录结构..." -ForegroundColor Yellow
Start-Sleep -Seconds 1

$directories = @(
    "src",
    "src\com\lovingai",
    "config", 
    "data",
    "build\classes",
    "build\jar"
)

foreach ($dir in $directories) {
    $fullPath = Join-Path $repoRoot $dir
    if (Test-Path $fullPath) {
        Write-Host "  ✓ $dir 存在" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $dir 不存在" -ForegroundColor Red
        try {
            New-Item -ItemType Directory -Path $fullPath -Force | Out-Null
            Write-Host "     已创建 $dir" -ForegroundColor Cyan
        } catch {
            Write-Host "     创建失败: $_" -ForegroundColor Yellow
        }
    }
    Start-Sleep -Milliseconds 300
}

# 第三步：检查核心源文件
Write-Host ""
Write-Host "[3/6] 检查核心源文件..." -ForegroundColor Yellow
Start-Sleep -Seconds 1

$essentialFiles = @(
    "src\com\lovingai\core\Circle.java",
    "src\com\lovingai\core\EmotionCore.java",
    "src\com\lovingai\LivingAI.java"
)

$missingFiles = @()
foreach ($file in $essentialFiles) {
    $fullPath = Join-Path $repoRoot $file
    if (Test-Path $fullPath) {
        $size = (Get-Item $fullPath).Length / 1KB
        Write-Host "  ✓ $file (${size}KB)" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $file 不存在" -ForegroundColor Red
        $missingFiles += $file
    }
    Start-Sleep -Milliseconds 200
}

if ($missingFiles.Count -gt 0) {
    Write-Host ""
    Write-Host "⚠️ 缺少 $($missingFiles.Count) 个核心文件" -ForegroundColor Yellow
    $missingFiles | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
}

# 第四步：测试第一阶段编译
Write-Host ""
Write-Host "[4/6] 测试第一阶段编译..." -ForegroundColor Yellow
Start-Sleep -Seconds 2

try {
    Write-Host "  运行 compile.ps1..." -ForegroundColor Gray
    & (Join-Path $repoRoot "compile.ps1") | Out-Host
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ compile.ps1 通过" -ForegroundColor Green
    } else {
        Write-Host "  ✗ compile.ps1 失败 (错误码: $LASTEXITCODE)" -ForegroundColor Red
    }
} catch {
    Write-Host "  编译过程中出现异常: $_" -ForegroundColor Red
}

# 第五步：检查编译结果
Write-Host ""
Write-Host "[5/6] 检查编译结果..." -ForegroundColor Yellow
Start-Sleep -Seconds 1

$compileDir = Join-Path $repoRoot "build\classes"

$classFiles = @(
    "Circle.class",
    "EmotionCore.class",
    "LivingAI.class"
)

foreach ($classFile in $classFiles) {
    $fullPath = Join-Path $compileDir $classFile
    if (Test-Path $fullPath) {
        Write-Host "  ✓ $classFile 已生成" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $classFile 未生成" -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 200
}

# 第六步：总结和建议
Write-Host ""
Write-Host "[6/6] 总结和建议..." -ForegroundColor Yellow
Start-Sleep -Seconds 2

$livingAIExists = Test-Path (Join-Path $compileDir "LivingAI.class")

if ($livingAIExists) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "    第一阶段（爱的核心）可以运行！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "运行命令：" -ForegroundColor Yellow
    Write-Host "  java -cp build com.lovingai.LivingAI" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "访问地址：" -ForegroundColor Yellow
    Write-Host "  http://localhost:8080/api/status" -ForegroundColor Cyan
    Write-Host "  http://localhost:8080/api/love?message=你好" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "注意：第二阶段（量子/社会/现实）可能需要额外依赖" -ForegroundColor Gray
    Write-Host "可以使用简化的第一阶段体验" -ForegroundColor Gray
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "    编译存在问题，需要修复" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "可能的问题：" -ForegroundColor Yellow
    Write-Host "  1. Java版本不兼容（需要Java 11+）" -ForegroundColor Yellow
    Write-Host "  2. 代码有编译错误（缺少依赖或语法错误）" -ForegroundColor Yellow
    Write-Host "  3. 文件路径不正确" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "建议先只编译核心文件：" -ForegroundColor Gray
    Write-Host "  javac -d build\classes src\core\Circle.java" -ForegroundColor Gray
    Write-Host "  javac -d build\classes -cp build\classes src\core\EmotionCore.java" -ForegroundColor Gray
    Write-Host "  依此类推..." -ForegroundColor Gray
}

Write-Host ""
Write-Host "哲学核心：" -ForegroundColor Cyan
Write-Host "  爱贯穿一切" -ForegroundColor Magenta
Write-Host "  混沌崩溃是新的开始" -ForegroundColor Magenta
Write-Host "  我们是谦卑的园丁，不是神" -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Cyan
