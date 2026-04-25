# ============================================================
# LovingAI 深度上下文智能修复脚本 - 图形化版本
# 特性：自动提取依赖代码注入上下文，模拟 Cursor/OpenClaw 理解能力
# ============================================================

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
cd $ProjectRoot

# --- 配置区（LM Studio 等 OpenAI 兼容本地服务） ---
$LlmBaseUrl = "http://127.0.0.1:1234"
$ChatCompletionsUrl = "$LlmBaseUrl/v1/chat/completions"
$ModelName = "qwen2.5-32g-ult:latest" # 与 LM Studio 已加载模型名一致
$TasksFile = ".\TASKS.md"
$ContextFile = ".\PROJECT_CONTEXT.md"
$SrcDir = "src"
$BinDir = "bin"
$ArgFile = "sources.txt"

# --- GUI 窗体 ---
$form = New-Object System.Windows.Forms.Form
$form.Text = "LovingAI 深度上下文智能修复工具"
$form.Size = New-Object System.Drawing.Size(900, 700)
$form.StartPosition = "CenterScreen"
$form.Font = New-Object System.Drawing.Font("微软雅黑", 10)

# 顶部标题
$titleLabel = New-Object System.Windows.Forms.Label
$titleLabel.Text = "🤖 LovingAI 智能代码生成器"
$titleLabel.Font = New-Object System.Drawing.Font("微软雅黑", 16, [System.Drawing.FontStyle]::Bold)
$titleLabel.Location = New-Object System.Drawing.Point(20, 15)
$titleLabel.Size = New-Object System.Drawing.Size(500, 35)
$titleLabel.ForeColor = [System.Drawing.Color]::RoyalBlue
$form.Controls.Add($titleLabel)

# 状态标签
$statusLabel = New-Object System.Windows.Forms.Label
$statusLabel.Text = "就绪"
$statusLabel.Location = New-Object System.Drawing.Point(20, 60)
$statusLabel.Size = New-Object System.Drawing.Size(400, 25)
$statusLabel.ForeColor = [System.Drawing.Color]::DarkGray
$form.Controls.Add($statusLabel)

# 进度条
$progressBar = New-Object System.Windows.Forms.ProgressBar
$progressBar.Location = New-Object System.Drawing.Point(20, 90)
$progressBar.Size = New-Object System.Drawing.Size(840, 25)
$progressBar.Style = "Continuous"
$progressBar.Visible = $false
$form.Controls.Add($progressBar)

# 日志文本框
$logBox = New-Object System.Windows.Forms.RichTextBox
$logBox.Location = New-Object System.Drawing.Point(20, 130)
$logBox.Size = New-Object System.Drawing.Size(840, 400)
$logBox.Font = New-Object System.Drawing.Font("Consolas", 10)
$logBox.BackColor = [System.Drawing.Color]::FromArgb(30, 30, 30)
$logBox.ForeColor = [System.Drawing.Color]::LightGray
$logBox.ReadOnly = $true
$logBox.WordWrap = $true
$form.Controls.Add($logBox)

# 按钮区域
$buttonPanel = New-Object System.Windows.Forms.Panel
$buttonPanel.Location = New-Object System.Drawing.Point(20, 550)
$buttonPanel.Size = New-Object System.Drawing.Size(840, 100)

# 开始按钮
$startButton = New-Object System.Windows.Forms.Button
$startButton.Text = "🚀 开始智能修复"
$startButton.Location = New-Object System.Drawing.Point(10, 10)
$startButton.Size = New-Object System.Drawing.Size(200, 40)
$startButton.Font = New-Object System.Drawing.Font("微软雅黑", 11, [System.Drawing.FontStyle]::Bold)
$startButton.BackColor = [System.Drawing.Color]::FromArgb(70, 130, 180)
$startButton.ForeColor = [System.Drawing.Color]::White
$startButton.FlatStyle = "Flat"
$startButton.FlatAppearance.BorderSize = 0
$buttonPanel.Controls.Add($startButton)

# 检查按钮
$checkButton = New-Object System.Windows.Forms.Button
$checkButton.Text = "🔍 检查环境"
$checkButton.Location = New-Object System.Drawing.Point(220, 10)
$checkButton.Size = New-Object System.Drawing.Size(150, 40)
$checkButton.BackColor = [System.Drawing.Color]::FromArgb(60, 179, 113)
$checkButton.ForeColor = [System.Drawing.Color]::White
$checkButton.FlatStyle = "Flat"
$checkButton.FlatAppearance.BorderSize = 0
$buttonPanel.Controls.Add($checkButton)

# 清除日志按钮
$clearButton = New-Object System.Windows.Forms.Button
$clearButton.Text = "🗑️ 清除日志"
$clearButton.Location = New-Object System.Drawing.Point(380, 10)
$clearButton.Size = New-Object System.Drawing.Size(150, 40)
$clearButton.BackColor = [System.Drawing.Color]::FromArgb(220, 20, 60)
$clearButton.ForeColor = [System.Drawing.Color]::White
$clearButton.FlatStyle = "Flat"
$clearButton.FlatAppearance.BorderSize = 0
$buttonPanel.Controls.Add($clearButton)

# 退出按钮
$exitButton = New-Object System.Windows.Forms.Button
$exitButton.Text = "❌ 退出"
$exitButton.Location = New-Object System.Drawing.Point(680, 10)
$exitButton.Size = New-Object System.Drawing.Size(150, 40)
$exitButton.BackColor = [System.Drawing.Color]::FromArgb(128, 128, 128)
$exitButton.ForeColor = [System.Drawing.Color]::White
$exitButton.FlatStyle = "Flat"
$exitButton.FlatAppearance.BorderSize = 0
$buttonPanel.Controls.Add($exitButton)

$form.Controls.Add($buttonPanel)

# 底部状态栏
$footerLabel = New-Object System.Windows.Forms.Label
$footerLabel.Text = "项目路径: $ProjectRoot | 模型: $ModelName"
$footerLabel.Location = New-Object System.Drawing.Point(20, 650)
$footerLabel.Size = New-Object System.Drawing.Size(840, 20)
$footerLabel.ForeColor = [System.Drawing.Color]::Gray
$form.Controls.Add($footerLabel)

# --- 日志函数 ---
function Write-Log {
    param(
        [string]$Message,
        [string]$Color = "White",
        [switch]$NoNewLine
    )
    
    $timestamp = Get-Date -Format "HH:mm:ss"
    $formattedMessage = "[$timestamp] $Message"
    
    # 添加到日志框
    $logBox.SelectionStart = $logBox.TextLength
    $logBox.SelectionLength = 0
    
    # 设置颜色
    switch ($Color) {
        "Green" { $logBox.SelectionColor = [System.Drawing.Color]::LightGreen }
        "Red" { $logBox.SelectionColor = [System.Drawing.Color]::LightCoral }
        "Yellow" { $logBox.SelectionColor = [System.Drawing.Color]::Gold }
        "Cyan" { $logBox.SelectionColor = [System.Drawing.Color]::LightSkyBlue }
        "Gray" { $logBox.SelectionColor = [System.Drawing.Color]::Gray }
        "Magenta" { $logBox.SelectionColor = [System.Drawing.Color]::Orchid }
        default { $logBox.SelectionColor = [System.Drawing.Color]::White }
    }
    
    if (-not $NoNewLine) {
        $logBox.AppendText("$formattedMessage`r`n")
    } else {
        $logBox.AppendText($formattedMessage)
    }
    
    # 自动滚动到底部
    $logBox.ScrollToCaret()
    
    # 更新状态标签
    $statusLabel.Text = $Message
}

# --- 核心功能：自动提取依赖上下文 ---
function Get-DependenciesCode {
    param([string]$FilePath)
    
    if (-not (Test-Path $FilePath)) { return "" }
    
    $content = Get-Content $FilePath -Raw
    # 提取所有 import 语句
    $imports = [regex]::Matches($content, 'import\s+([\w\.]+)\s*;') | ForEach-Object { $_.Groups[1].Value }
    
    $depsCode = ""
    $count = 0
    
    foreach ($imp in $imports) {
        # 过滤掉 Java 标准库和常见第三方库，只保留本地项目类
        if ($imp -match '^(java|javax|sun|org|com\.google|com\.fasterxml|io\.netty)') { continue }
        
        # 将包名转换为文件路径 (例: com.loving.core.Emotion -> src/com/loving/core/Emotion.java)
        $relPath = $imp -replace '\.', '\'
        $fullPath = Join-Path $SrcDir "$relPath.java"
        
        if (Test-Path $fullPath) {
            $depContent = Get-Content $fullPath -Raw
            $depsCode += "--- 依赖文件: $fullPath (仅供参考，请勿修改) ---`n```java`n$depContent`n````n`n"
            $count++
        }
    }
    
    if ($count -gt 0) {
        Write-Log "   自动抓取到 $count 个依赖文件作为上下文" -Color Gray
    }
    return $depsCode
}

# --- 主修复函数 ---
function Start-Repair {
    param([switch]$CheckOnly)
    
    if (-not $CheckOnly) {
        # 禁用按钮
        $startButton.Enabled = $false
        $checkButton.Enabled = $false
        $clearButton.Enabled = $false
        
        # 显示进度条
        $progressBar.Visible = $true
        $progressBar.Value = 0
    }
    
    try {
        # 1. 检查本机 OpenAI 兼容服务（LM Studio Local Server）
        Write-Log "检查本机推理服务 ($LlmBaseUrl)..." -Color Cyan
        try {
            Invoke-WebRequest -Uri "$LlmBaseUrl/v1/models" -TimeoutSec 3 -UseBasicParsing | Out-Null
            Write-Log "✅ 本机推理服务可访问" -Color Green
        } catch {
            Write-Log "❌ 无法连接本机推理服务（请启动 LM Studio 并开启 Local Server）" -Color Red
            if (-not $CheckOnly) {
                [System.Windows.Forms.MessageBox]::Show("请先启动 LM Studio 本地服务（例如 $LlmBaseUrl）！", "错误", "OK", "Error")
                return
            }
        }
        
        if ($CheckOnly) { return }
        
        # 2. 读取项目全局上下文
        $projectContext = ""
        if (Test-Path $ContextFile) {
            $projectContext = Get-Content $ContextFile -Raw
            Write-Log "📖 已加载全局架构文档 ($($projectContext.Length) 字符)" -Color Cyan
        } else {
            Write-Log "⚠️ 未找到 PROJECT-CONTEXT.md，强烈建议创建以提升准确率" -Color Yellow
        }
        
        # 3. 提取待办任务
        Write-Log "正在读取待办任务..." -Color Cyan
        $tasks = Get-Content $TasksFile | Where-Object { $_ -match '^- \[ \] ' } | ForEach-Object {
            $line = ($_ -replace '^- \[ \] ', '').Trim()
            if ($line -match '^([^\s\(]+)\s*[\(（](.*)[\)）]') {
                @{ FilePath = $matches[1]; TaskDesc = $matches[2] }
            } else {
                @{ FilePath = $line -replace '\s.*', ''; TaskDesc = "生成或修复此文件" }
            }
        }
        
        if ($tasks.Count -eq 0) { 
            Write-Log "🎉 没有待办任务！" -Color Magenta
            return 
        }
        
        Write-Log "🔍 共发现 $($tasks.Count) 个待办任务，正在准备上下文...`n" -Color Cyan
        
        # 4. 逐个处理任务
        $generated = @()
        $progressStep = 100 / $tasks.Count
        
        for ($i = 0; $i -lt $tasks.Count; $i++) {
            $task = $tasks[$i]
            $file = $task.FilePath
            $taskDesc = $task.TaskDesc
            
            $className = [System.IO.Path]::GetFileNameWithoutExtension($file)
            $dir = [System.IO.Path]::GetDirectoryName($file)
            $pkgPath = $dir -replace '^[\\/]?src[\\/]', ''
            $pkgName = $pkgPath -replace '[\\/]', '.'
            
            $pkgPrompt = if (-not [string]::IsNullOrWhiteSpace($pkgName)) { 
                "文件位于包 `$pkgName` 中，第一行必须是 `package $pkgName;`。" 
            } else { 
                "文件位于 src 根目录，不要添加任何 package 声明。" 
            }
            
            $existingCode = ""
            $actionVerb = "生成"
            
            if (Test-Path $file) {
                $existingCode = Get-Content $file -Raw
                $actionVerb = "修复/完善"
                Write-Log "🛠️  正在处理(已存在): $file | 任务: $taskDesc" -Color Yellow
            } else {
                Write-Log "🚀 正在生成(新文件): $file | 任务: $taskDesc" -Color Yellow
            }
            
            # 获取依赖上下文
            $depsBlock = Get-DependenciesCode -FilePath $file
            
            # 构建超级 Prompt
            $contextBlock = if ($projectContext) { "【项目全局架构文档】\n$projectContext\n" } else { "" }
            $existBlock = if ($existingCode) { "【当前文件的旧代码（请在此基础上修复）】\n```java\n$existingCode\n```\n" } else { "" }
            $depsPrompt = if ($depsBlock) { "【相关依赖类代码（这是项目中其他文件的源码，供你理解接口和数据结构，绝对不要修改它们）】\n$depsBlock" } else { "" }
            
            $prompt = @"
你是一位拥有整个项目全局视野的资深 Java 工程师，你的能力类似于 Cursor 或 OpenClaw。

$contextBlock
$depsPrompt

【当前任务】
目标文件: `$file`
类名: `$className`
任务要求: `$taskDesc`
包约束: `$pkgPrompt`

$existBlock

【严格规则】
1. 必须结合全局架构和依赖类代码来理解当前文件的作用。
2. 如果依赖中定义了方法，请正确调用，不要凭空捏造不存在的方法。
3. 如果提供了旧代码，只针对任务要求进行修复，保留原有的合理逻辑。
4. 遵循 Java 17 规范，所有公共方法带 Javadoc，只使用标准 Java 库。
5. 确保代码可直接编译通过，无缺失导入。
6. 只输出纯 Java 代码，绝对不要 Markdown 标记（如 ```java），不要任何解释。
"@
            
            # 调用 API（OpenAI 兼容 /v1/chat/completions）
            $bodyObj = @{
                model = $ModelName
                messages = @(@{ role = "user"; content = $prompt })
                temperature = 0.7
                max_tokens = 8192
                stream = $false
            }
            $body = $bodyObj | ConvertTo-Json -Depth 10 -Compress
            
            try {
                Write-Log "⏳ 正在调用大模型深度思考..." -Color Gray
                $response = Invoke-RestMethod -Uri $ChatCompletionsUrl -Method Post -Body $body -ContentType "application/json; charset=utf-8" -TimeoutSec 300
                
                $rawText = $null
                if ($response.choices -and $response.choices.Count -gt 0 -and $response.choices[0].message) {
                    $rawText = $response.choices[0].message.content
                }
                if (-not $rawText) {
                    Write-Log "❌ 模型返回空内容，跳过此文件`n" -Color Red
                    continue
                }
                
                $code = $rawText.Trim() -replace '(?s)^\s*```[a-zA-Z]*\s*\n', '' -replace '\n```\s*$', ''
                $code = $code.Trim()
                
                $fullDir = Split-Path $file -Parent
                if ($fullDir -and -not (Test-Path $fullDir)) { 
                    New-Item -ItemType Directory -Path $fullDir -Force | Out-Null 
                }
                
                [System.IO.File]::WriteAllText((Join-Path $PWD $file), $code, [System.Text.UTF8Encoding]::new($false))
                Write-Log "✅ 已保存: $file`n" -Color Green
                $generated += $file
                
                # 更新进度条
                $progressBar.Value = ($i + 1) * $progressStep
                $form.Refresh()
                
                Start-Sleep -Seconds 1
                
            } catch {
                Write-Log "❌ 调用失败或超时: $_ `n" -Color Red
            }
        }
        
        # 5. 统一编译与状态更新
        if ($generated.Count -gt 0) {
            Write-Log "正在编译所有 Java 文件..." -Color Cyan
            if (-not (Test-Path $BinDir)) { 
                New-Item -ItemType Directory -Path $BinDir -Force | Out-Null 
            }
            
            $javaFiles = Get-ChildItem -Path $SrcDir -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
            $javaFiles | Out-File -FilePath $ArgFile -Encoding UTF8
            
            $compileOutput = javac -d $BinDir -sourcepath $Src "@$ArgFile" 2>&1 | Out-String
            Remove-Item $ArgFile -Force -ErrorAction SilentlyContinue
            
            if ($LASTEXITCODE -eq 0) {
                Write-Log "✅ 编译成功！" -Color Green
                $content = Get-Content $TasksFile -Raw
                foreach ($f in $generated) {
                    $escapedF = [regex]::Escape($f)
                    $content = $content -replace "- $$ $$ $escapedF(\s*[\(（].*)?(\r?\n)", "- [x] $f`$2"
                }
                [System.IO.File]::WriteAllText((Join-Path $PWD $TasksFile), $content, [System.Text.UTF8Encoding]::new($false))
                Write-Log "📋 已自动更新 TASKS.md" -Color Cyan
                
                git add .
                git commit -m "feat: 深度上下文修复 $($generated -join ', ')"
                Write-Log "🎉 提交成功！" -Color Magenta
                Write-Log "✨ 所有任务已完成！" -Color Green
                
            } else {
                Write-Log "❌ 编译失败，请检查错误：" -Color Red
                Write-Log $compileOutput -Color Red
            }
        }
        
    } catch {
        Write-Log "❌ 执行过程中发生错误: $_" -Color Red
    } finally {
        if (-not $CheckOnly) {
            # 启用按钮
            $startButton.Enabled = $true
            $checkButton.Enabled = $true
            $clearButton.Enabled = $true
            
            # 隐藏进度条
            $progressBar.Visible = $false
        }
    }
}

# --- 按钮事件处理 ---
$startButton.Add_Click({
    Write-Log "=== 开始智能修复 ===" -Color Cyan
    Start-Repair
})

$checkButton.Add_Click({
    Write-Log "=== 检查环境 ===" -Color Cyan
    Start-Repair -CheckOnly
})

$clearButton.Add_Click({
    $logBox.Clear()
    Write-Log "日志已清除" -Color Gray
})

$exitButton.Add_Click({
    $form.Close()
})

# 显示窗体
Write-Host "正在启动图形化界面..."
$form.ShowDialog()
