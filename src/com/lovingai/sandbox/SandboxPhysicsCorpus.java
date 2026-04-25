package com.lovingai.sandbox;

import com.lovingai.LivingAI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 沙盒可用的「现实已知物理」参照文本：存于 {@code data/sandbox/physics-reference.txt}，
 * 供 {@link MirageSandbox} 在预演前做锚定注记，并略提高与现实对齐的权重。
 */
public final class SandboxPhysicsCorpus {

    public static final SandboxPhysicsCorpus INSTANCE = new SandboxPhysicsCorpus();

    private static final Path PATH = Paths.get(LivingAI.BASE_DIR, "sandbox", "physics-reference.txt");
    private static final int MAX_BYTES = 200_000;

    private final Object lock = new Object();
    private volatile String cached = "";

    public record PhysicsProfile(
            double conservationWeight,
            double causalityWeight,
            double continuityWeight,
            double uncertaintyWeight,
            double boundaryWeight,
            double complexityWeight,
            String note) {}

    private SandboxPhysicsCorpus() {}

    /** 确保已从磁盘加载；若文件不存在则写入内置种子。 */
    public void ensureLoaded() {
        synchronized (lock) {
            if (Files.isRegularFile(PATH)) {
                try {
                    cached = Files.readString(PATH, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    cached = "";
                }
                return;
            }
            try {
                Files.createDirectories(PATH.getParent());
                String seed = defaultSeed();
                Files.writeString(PATH, seed, StandardCharsets.UTF_8);
                cached = seed;
            } catch (IOException e) {
                cached = defaultSeed();
            }
        }
    }

    /**
     * 内置种子：现实物理主干规律与常用常数（教学/参照用摘要，非穷尽证明）。
     * 若磁盘上已有旧版 physics-reference.txt，需删除该文件或在 GUI 点「填入内置种子」才会替换为本版。
     */
    public static String defaultSeed() {
        return DEFAULT_PHYSICS_SEED;
    }

    private static final String DEFAULT_PHYSICS_SEED =
            """
# =============================================================================
# 现实物理参照 · 沙盒锚定（主干规律摘要，SI / 国际通行表述）
# =============================================================================

## 0. 量纲与单位制
- 国际单位制 SI 七个基本量：长度 m、质量 kg、时间 s、电流 A、热力学温度 K、物质的量 mol、发光强度 cd。
- 导出例：力 N=kg·m/s²，能量 J=N·m，功率 W=J/s，电荷 C=A·s，电势 V=W/A，电阻 Ω=V/A，电容 F=C/V，磁通 Wb=V·s，磁感应强度 T=Wb/m²。
- 词头：k=10³，M=10⁶，G=10⁹，m=10⁻³，μ=10⁻⁶，n=10⁻⁹，p=10⁻¹²。

## 1. 基本物理常数（常用数值阶）
- 真空光速 c ≈ 2.99792458×10⁸ m/s（精确值定义 SI 米）。
- 万有引力常数 G ≈ 6.67430×10⁻¹¹ m³·kg⁻¹·s⁻²。
- 普朗克常数 h ≈ 6.62607015×10⁻³⁴ J·s；ħ = h/(2π)。
- 元电荷 e ≈ 1.602176634×10⁻¹⁹ C（精确值定义 SI 安培）。
- 真空介电常数 ε₀ ≈ 8.854×10⁻¹² F/m；真空磁导率 μ₀ = 4π×10⁻⁷ H/m；c² = 1/(ε₀μ₀)。
- 电子质量 m_e ≈ 9.109×10⁻³¹ kg；质子质量 m_p ≈ 1.673×10⁻²⁷ kg。
- 玻尔兹曼常数 k_B ≈ 1.380649×10⁻²³ J/K；阿伏伽德罗常数 N_A ≈ 6.02214076×10²³ mol⁻¹。
- 标准重力加速度 g ≈ 9.81 m/s²（海平面附近近似）。

## 2. 经典力学（质点与刚体）
- 牛顿第一定律：惯性系中不受合外力则速度不变。
- 牛顿第二定律：F = dp/dt；低速下 F = m a，p = m v。
- 牛顿第三定律：作用力与反作用力等大反向共线。
- 动量守恒：孤立系总动量不变；碰撞中常配合能量分析。
- 角动量 L = r×p；力矩 τ = r×F = dL/dt；孤立系对点/轴角动量守恒。
- 功 W = ∫ F·dr；功率 P = dW/dt = F·v。
- 动能 E_k = ½mv²（非相对论）；势能：均匀重力 mgh，弹性 ½kx²，万有引力 -Gm₁m₂/r。
- 机械能守恒：仅有保守力做功时 E_k + E_p 不变。
- 万有引力大小 F = G m₁ m₂ / r²；重力势能 U ≈ -Gm₁m₂/r。
- 开普勒行星运动：椭圆轨道，面积速度恒定；周期 T² ∝ a³（平方反比引力场）。
- 谐振动 x = A cos(ωt+φ)；ω² = k/m；周期 T = 2π/ω。
- 阻尼/受迫振动：能量耗散与共振（定性）。

## 3. 连续介质与流体力学（工程常用）
- 密度 ρ = m/V；压强 p = F/A。
- 静液压 p = p₀ + ρgh；阿基米德浮力 = 排开流体重量。
- 连续性方程（定常一维）ρ A v = 常数；伯努利方程（理想定常沿流线）p + ½ρv² + ρgh = 常数。
- 粘滞：雷诺数 Re = ρ v L / η 判别层流/湍流（定性）。
- 表面张力与毛细现象（定性）。

## 4. 热力学
- 第零定律：热平衡传递性 → 温度定义。
- 第一定律：ΔU = Q − W（符号约定：系统吸热 Q 正，对外做功 W 正）。
- 第二定律：孤立系熵不减；开尔文/克劳修斯表述；热机效率 η ≤ 1 − T_c/T_h（卡诺上限）。
- 第三定律：T→0 时完美晶体熵趋于常数（常取 0）。
- 理想气体状态方程 pV = n R T = N k_B T；内能 U 对理想单原子气体 E_k = (3/2) N k_B T。
- 熵 dS = δQ_rev/T；自由能 F = U − TS，吉布斯 G = H − TS（化学势与相变分析）。

## 5. 电磁学（宏观）
- 库仑定律：点电荷 F = k q₁ q₂ / r²，k = 1/(4πε₀)。
- 电场 E；电势 φ，E = −∇φ；高斯定理 ∮ E·dA = Q_enc/ε₀。
- 电容 C = Q/V；平行板 C ≈ ε A/d。
- 稳恒电流 I = dQ/dt；欧姆定律 V = I R；焦耳热 P = I²R。
- 基尔霍夫：节点 ΣI=0，回路 ΣV=0。
- 毕奥–萨伐尔/安培定律：稳恒电流产生磁场 B；安培环路 ∮ B·dl = μ₀ I_enc。
- 法拉第感应定律 ∮ E·dl = − dΦ_B/dt；楞次定律判断感应电流方向。
- 麦克斯韦方程组（微分形式 SI）：∇·E = ρ/ε₀；∇·B = 0；∇×E = −∂B/∂t；∇×B = μ₀ J + μ₀ε₀ ∂E/∂t。
- 洛伦兹力 F = q (E + v×B)。
- 电磁波：真空中 v=c；平面波 E⊥B⊥传播方向。

## 6. 光学
- 几何光学：反射 θ_i = θ_r；折射 n₁ sin θ₁ = n₂ sin θ₂（斯涅尔）。
- 薄透镜 1/f = 1/s + 1/s′；放大率 m = −s′/s。
- 波动光学：干涉光程差决定相长/相消；衍射限制分辨率（定性）。
- 偏振：线偏振、圆偏振（定性）。

## 7. 狭义相对论（惯性系）
- 时空间隔 s² = −(cΔt)² + Δx²+Δy²+Δz²（符号约定依教材）。
- 时间膨胀 Δt = γ Δt₀，长度收缩 L = L₀/γ，γ = 1/√(1−v²/c²)。
- 质能等价 E = mc²；相对论能量 E² = (pc)² + (m₀c²)²。

## 8. 量子物理（入门层次）
- 光子能量 E = h f；物质波 de Broglie λ = h/p。
- 海森堡不确定性 Δx Δp ≳ ħ/2（数量级）。
- 薛定谔方程（形式）：iħ ∂ψ/∂t = Ĥ ψ；|ψ|² 概率密度。
- 原子：玻尔/量子数 n,l,m_l,m_s；泡利不相容；壳层结构（定性）。
- 自旋：费米子半整数、玻色子整数（定性）。

## 9. 原子核与粒子（定性）
- 核结合能 B，质量亏损 Δm；裂变/聚变释放能量。
- α/β/γ 衰变；半衰期；辐射剂量单位（定性了解 Gy、Sv）。
- 标准模型：夸克/轻子/规范玻色子/希格斯（定性）。

## 10. 广义相对论与宇宙学（一句锚定）
- 引力即时空弯曲；史瓦西半径 R_s = 2GM/c²。
- 宇宙学：哈勃定律 v ≈ H₀ d；大爆炸+ΛCDM 为当前标准模型框架（定性）。

## 11. 统计与涨落（定性）
- 麦克斯韦速率分布；布朗运动；熵的统计诠释 S = k_B ln Ω（玻尔兹曼）。

# — 以上为「大部分主干物理规律」的压缩索引；具体计算请以教材与测量为准。 —
""";

    public String getText() {
        ensureLoaded();
        return cached;
    }

    public int getCharCount() {
        return getText().length();
    }

    /**
     * 全文替换。用于从 GUI 或 POST 导入。
     *
     * @throws IOException 写入失败
     * @throws IllegalArgumentException 超长
     */
    public void replaceAll(String text) throws IOException {
        String t = text == null ? "" : text;
        if (t.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("内容超过上限 " + MAX_BYTES + " 字节");
        }
        synchronized (lock) {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, t, StandardCharsets.UTF_8);
            cached = t;
        }
    }

    /** 单行化摘要，写入沙盒认知注记。 */
    public String snippetForNote(int maxLen) {
        ensureLoaded();
        String t = cached.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) {
            return "（未配置物理参照；可在 /api/sandbox/physics 或 GUI 导入）";
        }
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen) + "…";
    }

    /**
     * 有足够参照文本时，略增「现实亲和」：已知定律越多，越倾向把预演系在可核对现实一侧。
     */
    public double anchorAffinityBonus() {
        ensureLoaded();
        int len = cached.replaceAll("\\s", "").length();
        if (len < 24) {
            return 0;
        }
        return Math.min(0.14, 0.03 + len / 8000.0);
    }

    /**
     * 软规则画像：不返回“硬判断”，而返回连续权重，供沙盒组合。
     * 权重同时受「库内物理参照覆盖度」和「当前刺激关键词」影响。
     */
    public PhysicsProfile buildProfile(String stimulus) {
        ensureLoaded();
        String corpus = cached == null ? "" : cached.toLowerCase();
        String stim = stimulus == null ? "" : stimulus.toLowerCase();
        String joined = corpus + " " + stim;

        double conservation = scoreByKeywords(joined, "守恒", "energy", "动量", "角动量", "continuity", "质量守恒");
        double causality = scoreByKeywords(joined, "因果", "causal", "before", "after", "时序", "反馈", "闭环");
        double continuity = scoreByKeywords(joined, "连续", "smooth", "流体", "微分", "梯度", "伯努利", "连续介质");
        double uncertainty = scoreByKeywords(joined, "不确定", "fluctuation", "量子", "随机", "统计", "噪声", "entropy");
        double boundary = scoreByKeywords(joined, "边界", "约束", "constraint", "initial", "初值", "稳态", "收敛");

        int len = (corpus + stim).replaceAll("\\s+", "").length();
        double complexity = clamp01(0.18 + Math.min(0.62, len / 12000.0));

        conservation = clamp01(0.22 + conservation * 0.78);
        causality = clamp01(0.20 + causality * 0.80);
        continuity = clamp01(0.16 + continuity * 0.84);
        uncertainty = clamp01(0.10 + uncertainty * 0.90);
        boundary = clamp01(0.18 + boundary * 0.82);

        String note =
                String.format(
                        java.util.Locale.US,
                        "physProfile cons=%.2f cause=%.2f cont=%.2f uncert=%.2f bound=%.2f cx=%.2f",
                        conservation,
                        causality,
                        continuity,
                        uncertainty,
                        boundary,
                        complexity);

        return new PhysicsProfile(
                conservation, causality, continuity, uncertainty, boundary, complexity, note);
    }

    private static double scoreByKeywords(String text, String... keys) {
        if (text == null || text.isBlank() || keys == null || keys.length == 0) return 0.0;
        int hit = 0;
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            if (text.contains(k.toLowerCase())) hit++;
        }
        return Math.min(1.0, hit / (double) keys.length);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
