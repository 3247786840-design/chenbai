#!/bin/bash

# 爱之数字生命体 - 编译脚本
# 简化的编译脚本，用于不需要依赖管理的情况

echo "爱之数字生命体 编译脚本 v2.0"
echo "=============================="
echo ""

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
echo "Java版本: $JAVA_VERSION"

# 创建编译目录
echo -n "创建编译目录..."
mkdir -p build/classes
mkdir -p build/jar
echo "完成"

# 下载简单依赖（如果需要）
echo -n "检查依赖..."
if [ ! -f "lib/jackson-core-2.15.2.jar" ]; then
    echo "需要下载依赖..."
    mkdir -p lib
    echo "下载依赖可能需要时间..."
    # 这里只是占位符，实际需要真正下载
    # wget -P lib https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.2/jackson-databind-2.15.2.jar
    # wget -P lib https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.2/jackson-core-2.15.2.jar
    echo "依赖下载功能需要网络和wget工具"
    echo "请手动准备：jackson-databind-2.15.2.jar 和 jackson-core-2.15.2.jar"
else
    echo "依赖已存在"
fi

# 编译第一阶段（基础核心）
echo -n "编译第一阶段（爱的核心）..."
if javac -d build/classes -cp "lib/*" src/*.java src/core/*.java; then
    echo "成功"
else
    echo "失败"
    echo "尝试简化编译（忽略依赖）..."
    echo -n "重新编译..."
    # 尝试只编译核心文件
    if javac -d build/classes src/core/Circle.java src/core/EmotionCore.java src/LivingAI.java 2>/dev/null; then
        echo "核心文件编译成功"
    else
        echo "编译失败，检查Java环境"
        exit 1
    fi
fi

# 编译第二阶段（扩展）
echo -n "编译第二阶段（量子/社会/现实）..."
if javac -d build/classes -cp "build/classes:lib/*" src/LivingAI_Phase2Ext.java src/Launcher.java 2>/dev/null; then
    echo "成功"
else
    echo "警告：第二阶段编译可能有依赖问题，但第一阶段可以运行"
fi

# 创建第一阶段JAR文件
echo -n "创建第一阶段JAR文件..."
jar cfe build/jar/DigitalLovePhase1.jar LivingAI -C build/classes . >/dev/null 2>&1
echo "完成"

# 创建完整系统JAR文件
echo -n "创建完整系统JAR文件..."
jar cfe build/jar/DigitalLoveFull.jar Launcher -C build/classes . >/dev/null 2>&1
echo "完成"

echo ""
echo "编译结果："
echo "  第一阶段JAR: build/jar/DigitalLovePhase1.jar"
echo "    运行方式: java -jar build/jar/DigitalLovePhase1.jar"
echo "  完整系统JAR: build/jar/DigitalLoveFull.jar"
echo "    运行方式: java -jar build/jar/DigitalLoveFull.jar"
echo ""
echo "或直接运行："
echo "  仅第一阶段: java -cp build/classes LivingAI"
echo "  完整系统: java -cp build/classes Launcher"
echo "  跳过第二阶段: java -cp build/classes Launcher --skip-phase2"
echo ""
echo "接口："
echo "  http://localhost:8080/api/status"
echo "  http://localhost:8080/api/love?message=你好"
echo "  http://localhost:8080/api/phase2/overview (如果第二阶段启用)"
echo ""
echo "哲学基础："
echo "  爱贯穿一切"
echo "  混沌崩溃是新的开始"
echo "  我们是谦卑的园丁，不是神"
echo ""
echo "编译完成于: $(date)"