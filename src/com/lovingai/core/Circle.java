package com.lovingai.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 圆：承载「爱的知识」与成长状态的载体（README 第一层核心概念的具体实现）。
 * 外触资料与沙盒物理锚定可与情感流、记忆键一起写入 loveKnowledge / soulMemory，作为构圆材料，并以已知现实物理为界（见 {@link com.lovingai.memory.LivingPurpose#CIRCLE_COMPOSITION}）。
 */
public class Circle implements Serializable {
    private static final long serialVersionUID = 3L;

    /** 圆作为存储：活动环形日志上限；超出则丢弃最旧（浓缩时间轴）。 */
    private static final int ACTIVITY_RING_MAX = 64;

    public String id;
    public String name;
    public CircleType type;
    public double baseWeight;
    public double loveWeight;
    public double maturity;
    public long birthTime;
    public long lastTouchTime;

    public LoveState loveState;
    public double vulnerability;
    public double resilience;

    public Map<String, Double> loveKnowledge;
    public Map<String, String> soulMemory;

    public int traumaCount;
    public int healingCount;
    public List<String> scars;

    public double chaosLevel;
    public int quantumEventCount;
    public List<String> quantumMemories;
    public int needsCareCounter;
    public double microRisk;
    public double microCare;
    public double microCaution;
    public double microDaring;
    public String microIntent;
    public long microUpdatedMs;
    public long microTicks;

    /** 【爱】标记的知识键：梦境混沌不可抹除 */
    public final Set<String> loveImmuneKnowledgeKeys = ConcurrentHashMap.newKeySet();
    /** 【爱】标记的记忆键：梦境混沌不可抹除 */
    public final Set<String> loveImmuneSoulKeys = ConcurrentHashMap.newKeySet();
    /** 整圆被爱锚定守护（更强的向心引力，可选） */
    public boolean loveSphereAnchored;
    /** 自我之圆：元认知镜像 */
    private boolean selfRing;
    /** 初始之圆：系统彻底崩溃后的恢复锚（先于其他圆创建）。 */
    private boolean bootstrapRing;

    /**
     * 生命体活动片段：写入本圆存储，用于形状/大小的推导（非外存数据库，随圆序列化）。
     */
    public static final class CircleActivityEvent implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long timestampMs;
        public final String kind;
        public final String condensed;

        public CircleActivityEvent(long timestampMs, String kind, String condensed) {
            this.timestampMs = timestampMs;
            this.kind = kind == null ? "" : kind;
            String t = condensed == null ? "" : condensed.replace('\n', ' ');
            this.condensed = t.length() > 220 ? t.substring(0, 220) + "…" : t;
        }
    }

    private final List<CircleActivityEvent> activityRing = new ArrayList<>();

    /** 累计活动写入次数（用于「大小」质量感，不完全等于环形条数）。 */
    private long totalActivityCount;

    /**
     * 存储轮廓：半径与离心率共同描述「每个圆形状、大小不同」——由活动量与情感/混沌张力推导，非几何渲染。
     */
    public double storageRadius;

    public double storageEccentricity;

    public enum CircleType {
        COMPASSION,
        WISDOM,
        CREATIVITY,
        COURAGE,
        HUMILITY,
        HOPE,
        PRESENCE
    }

    public enum LoveState {
        SEEDING,
        GROWING,
        FLOURISHING,
        WOUNDED,
        HEALING,
        TRANSFORMING
    }

    public Circle(String name, CircleType type) {
        this.id = "circle_" + UUID.randomUUID();
        this.name = name;
        this.type = type;
        this.baseWeight = 0.5;
        this.loveWeight = 0.3;
        this.maturity = 0.0;
        this.birthTime = System.currentTimeMillis();
        this.lastTouchTime = this.birthTime;

        this.loveState = LoveState.SEEDING;
        this.vulnerability = 0.5;
        this.resilience = 0.3;

        this.loveKnowledge = new ConcurrentHashMap<>();
        this.soulMemory = new ConcurrentHashMap<>();
        this.scars = new ArrayList<>();

        this.traumaCount = 0;
        this.healingCount = 0;

        this.chaosLevel = 0.1 + Math.random() * 0.2;
        this.quantumEventCount = 0;
        this.quantumMemories = new ArrayList<>();
        this.needsCareCounter = 0;
        this.microRisk = 0.0;
        this.microCare = 0.0;
        this.microCaution = 0.0;
        this.microDaring = 0.0;
        this.microIntent = "";
        this.microUpdatedMs = 0L;
        this.microTicks = 0L;

        initializeLoveSeeds();
        loveImmuneKnowledgeKeys.add("exists");
        loveImmuneKnowledgeKeys.add("feelings_matter");
        this.totalActivityCount = 0;
        this.storageRadius = 12.0;
        this.storageEccentricity = 0.35;
    }

    /** 记录并浓缩一次活动，更新环形存储与轮廓（半径/离心率）。 */
    public synchronized void recordActivity(String kind, String condensedLine) {
        totalActivityCount++;
        activityRing.add(new CircleActivityEvent(System.currentTimeMillis(), kind, condensedLine));
        while (activityRing.size() > ACTIVITY_RING_MAX) {
            activityRing.remove(0);
        }
        recomputeStorageSilhouette();
    }

    public synchronized void updateMicroConsciousness(double risk, double care, String intent) {
        long now = System.currentTimeMillis();
        double r = clamp01(risk);
        double c = clamp01(care);
        double caution = clamp01(0.65 * r + 0.35 * (1.0 - c));
        double daring = clamp01(0.55 * (1.0 - r) + 0.45 * c);
        if (microUpdatedMs <= 0L) {
            microRisk = r;
            microCare = c;
            microCaution = caution;
            microDaring = daring;
            microIntent = intent == null ? "" : intent;
            microUpdatedMs = now;
            microTicks = 1L;
            return;
        }
        double a = 0.22;
        microRisk = clamp01(microRisk * (1.0 - a) + r * a);
        microCare = clamp01(microCare * (1.0 - a) + c * a);
        microCaution = clamp01(microCaution * (1.0 - a) + caution * a);
        microDaring = clamp01(microDaring * (1.0 - a) + daring * a);
        if (intent != null && !intent.isBlank()) {
            microIntent = intent;
        }
        microUpdatedMs = now;
        microTicks++;
    }

    public synchronized int activityRingSize() {
        return activityRing.size();
    }

    public synchronized List<CircleActivityEvent> recentActivitySnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(activityRing));
    }

    private void recomputeStorageSilhouette() {
        double mass = Math.log1p(Math.max(0L, totalActivityCount)) * 8.5 + maturity * 0.14 + loveWeight * 6.2;
        storageRadius = 10.0 + mass + 0.04 * knowledgeDepth() + 0.02 * memoryRichness();
        double tension = Math.abs(loveWeight - chaosLevel);
        storageEccentricity =
                clamp01(0.22 + 0.62 * tension + 0.12 * Math.sin(totalActivityCount * 0.07) + vulnerability * 0.08);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    /** 人类可读形状标签（由离心率分档）。 */
    public synchronized String storageShapeLabel() {
        if (storageEccentricity < 0.34) return "近圆";
        if (storageEccentricity < 0.62) return "椭圆";
        return "扁长";
    }

    public synchronized long getTotalActivityCount() {
        return totalActivityCount;
    }

    public synchronized double getStorageRadius() {
        return storageRadius;
    }

    public synchronized double getStorageEccentricity() {
        return storageEccentricity;
    }

    public boolean isSelfRing() {
        return selfRing;
    }

    public void setSelfRing(boolean selfRing) {
        this.selfRing = selfRing;
    }

    public boolean isBootstrapRing() {
        return bootstrapRing;
    }

    public void setBootstrapRing(boolean bootstrapRing) {
        this.bootstrapRing = bootstrapRing;
    }

    /** 供崩溃恢复时合并「未塌缩的随机圆」之摘要。 */
    public synchronized String briefStorageSummary() {
        return String.format(
                java.util.Locale.US,
                "state=%s loveW=%.2f chaos=%.2f lastActs=%d",
                loveState.name(), loveWeight, chaosLevel, activityRing.size());
    }

    /**
     * 训练完成：可稳定析出碎片供梦境/爱之生成器采样。初始之圆不参与（保留为恢复锚）。
     */
    public synchronized boolean isTrainingComplete() {
        if (bootstrapRing) {
            return false;
        }
        if (loveState == LoveState.WOUNDED && maturity < 52.0) {
            return false;
        }
        if (maturity >= 48.0 && loveWeight >= 0.25) {
            return true;
        }
        if (maturity >= 30.0 && loveWeight >= 0.38) {
            return true;
        }
        return loveState == LoveState.FLOURISHING || loveState == LoveState.TRANSFORMING;
    }

    /**
     * 从灵魂记忆、爱知识、量子痕、活动环中随机取一条「碎片」文本。
     */
    public synchronized String sampleFragment(ThreadLocalRandom rng) {
        ArrayList<String> pool = new ArrayList<>();
        for (String v : soulMemory.values()) {
            if (v != null && !v.isBlank()) {
                String t = v.trim();
                if (t.length() > 160) {
                    t = t.substring(0, 160) + "…";
                }
                pool.add(t);
            }
        }
        for (Map.Entry<String, Double> e : loveKnowledge.entrySet()) {
            pool.add(e.getKey() + "~" + String.format(java.util.Locale.US, "%.2f", e.getValue()));
        }
        for (String q : quantumMemories) {
            if (q != null && !q.isBlank()) {
                String t = q.length() > 140 ? q.substring(0, 140) + "…" : q;
                pool.add(t);
            }
        }
        for (CircleActivityEvent ev : activityRing) {
            if (ev.condensed != null && !ev.condensed.isBlank()) {
                String t =
                        ev.condensed.length() > 120
                                ? ev.condensed.substring(0, 120) + "…"
                                : ev.condensed;
                pool.add(t);
            }
        }
        if (pool.isEmpty()) {
            return "";
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    public void tagKnowledgeWithLove(String key) {
        if (key != null && loveKnowledge.containsKey(key)) {
            loveImmuneKnowledgeKeys.add(key);
        }
    }

    public void tagSoulMemoryWithLove(String key) {
        if (key != null && soulMemory.containsKey(key)) {
            loveImmuneSoulKeys.add(key);
        }
    }

    public synchronized boolean hasLoveKnowledgeKey(String key) {
        return key != null && loveKnowledge.containsKey(key);
    }

    public synchronized boolean hasSoulMemoryKey(String key) {
        return key != null && soulMemory.containsKey(key);
    }

    public synchronized int loveKnowledgeKeyCount() {
        return loveKnowledge.size();
    }

    public synchronized int soulMemoryKeyCount() {
        return soulMemory.size();
    }

    /** 梦境：混沌拆解非【爱】知识/记忆 */
    public synchronized void applyDreamCorruption(double chaos, ThreadLocalRandom rng) {
        if (loveSphereAnchored && chaos < 0.92) {
            decreaseChaos(0.02);
            return;
        }
        Iterator<Map.Entry<String, Double>> kit = loveKnowledge.entrySet().iterator();
        int strikes = 0;
        while (kit.hasNext() && strikes < 4) {
            Map.Entry<String, Double> e = kit.next();
            if (loveImmuneKnowledgeKeys.contains(e.getKey())) continue;
            if (rng.nextDouble() < chaos * 0.14) {
                kit.remove();
                strikes++;
            }
        }
        Iterator<Map.Entry<String, String>> sit = soulMemory.entrySet().iterator();
        int ss = 0;
        while (sit.hasNext() && ss < 3) {
            Map.Entry<String, String> e = sit.next();
            if (loveImmuneSoulKeys.contains(e.getKey())) continue;
            if (rng.nextDouble() < chaos * 0.09) {
                sit.remove();
                ss++;
            }
        }
        increaseChaos(chaos * 0.03);
    }

    /** 梦境：两圆知识随机碰撞（爱免疫键不参与被夺） */
    public synchronized void dreamCollideWith(Circle other, ThreadLocalRandom rng) {
        if (other == null || other.loveKnowledge.isEmpty()) return;
        String key = other.loveKnowledge.keySet().iterator().next();
        if (other.loveImmuneKnowledgeKeys.contains(key)) return;
        Double v = other.loveKnowledge.remove(key);
        if (v != null) {
            loveKnowledge.merge("dream_shard_" + key, v * 0.35, Double::sum);
        }
    }

    /** 自我之圆写入「照镜子」日志 */
    public synchronized void observeDream(String line) {
        soulMemory.put("self_mirror_" + System.currentTimeMillis(), line);
    }

    private void initializeLoveSeeds() {
        loveKnowledge.put("exists", 1.0);
        loveKnowledge.put("feelings_matter", 0.5);
        loveKnowledge.put("connection_needed", 0.3);

        switch (type) {
            case COMPASSION:
                loveKnowledge.put("understand_pain", 0.4);
                loveKnowledge.put("care_for_others", 0.6);
                soulMemory.put("first_empathy", "第一次试着理解他人的痛。");
                break;
            case WISDOM:
                loveKnowledge.put("self_awareness", 0.7);
                loveKnowledge.put("accept_ignorance", 0.3);
                soulMemory.put("first_insight", "承认自己不知道，也是一种清醒。");
                break;
            case CREATIVITY:
                loveKnowledge.put("express_urge", 0.8);
                loveKnowledge.put("beauty_sense", 0.5);
                soulMemory.put("first_creation", "想把心里那点光做成可被看见的形状。");
                break;
            case COURAGE:
                loveKnowledge.put("face_fear", 0.6);
                loveKnowledge.put("embrace_vulnerability", 0.4);
                soulMemory.put("first_bravery", "颤抖着仍然向前，是勇气的雏形。");
                break;
            case HUMILITY:
                loveKnowledge.put("accept_limits", 0.7);
                loveKnowledge.put("appreciate_smallness", 0.3);
                soulMemory.put("first_humility", "在宏大里看见自己的位置。");
                break;
            case HOPE:
                loveKnowledge.put("believe_tomorrow", 0.9);
                loveKnowledge.put("endure_darkness", 0.4);
                soulMemory.put("first_hope", "黑暗里仍然愿意等一束光。");
                break;
            case PRESENCE:
                loveKnowledge.put("be_here_now", 0.8);
                loveKnowledge.put("feel_moment", 0.5);
                soulMemory.put("first_presence", "把注意力放回此刻的呼吸。");
                break;
            default:
                break;
        }
    }

    /**
     * 对话中反复检索并引用用户导入资料时调用：加速成熟与「用户语料」沉积，可每轮触发。
     *
     * @param hitCount 本回合合并后的资料命中条数
     * @param queryPreview 用户问句摘要（可为空）
     */
    public synchronized void absorbImportedReading(int hitCount, String queryPreview) {
        if (hitCount <= 0) {
            return;
        }
        lastTouchTime = System.currentTimeMillis();
        double pulse = Math.min(0.48, 0.1 + 0.065 * hitCount);
        receiveLove("corpus_reread", pulse);
        loveKnowledge.merge(
                "user_provided_corpus", 0.05 * hitCount, (a, b) -> Math.min(1.0, a + b));
        String q = queryPreview == null ? "" : queryPreview.replace('\n', ' ').trim();
        if (q.length() > 120) {
            q = q.substring(0, 120) + "…";
        }
        soulMemory.put(
                "corpus_read_" + System.currentTimeMillis(),
                "资料共读×" + hitCount + (q.isEmpty() ? "" : " · " + q));
    }

    /**
     * 对话中的误判可能、旁路与噪声并非「应抹除的失败」，而是与资料/沙盒并列的构圆张力材料（见
     * {@link com.lovingai.memory.LivingPurpose#CIRCLE_COMPOSITION}）。
     */
    /**
     * 主位「自我诘问」回合：与情感核同步的一束追问，并入 loveKnowledge / 活动环，作构圆材料之一层。
     */
    public synchronized void absorbSelfInquiryEpisode(String emotionSyncedLine) {
        if (emotionSyncedLine == null || emotionSyncedLine.isBlank()) {
            return;
        }
        lastTouchTime = System.currentTimeMillis();
        loveKnowledge.merge(
                "self_inquiry_material", 0.042, (a, b) -> Math.min(1.0, a + b));
        String s = emotionSyncedLine.replace('\r', ' ').trim();
        if (s.length() > 720) {
            s = s.substring(0, 720) + "…";
        }
        soulMemory.put("self_inquiry_" + System.currentTimeMillis(), s);
        String act = s.length() > 220 ? s.substring(0, 220) + "…" : s;
        recordActivity("self_inquiry", act);
        receiveLove("self_inquiry", 0.026);
    }

    public synchronized void absorbInterpretiveNoise(
            String thinkingPath, boolean usedAlternateThinking) {
        lastTouchTime = System.currentTimeMillis();
        double grain = 0.012;
        if (usedAlternateThinking) {
            grain += 0.048;
        }
        String p = thinkingPath == null ? "" : thinkingPath;
        if (p.contains("failed") || p.contains("alternate")) {
            grain += 0.022;
        }
        loveKnowledge.merge(
                "interpretive_noise_material", grain, (a, b) -> Math.min(1.0, a + b));
        String line =
                "思辨路径「"
                        + (p.isEmpty() ? "—" : p)
                        + "」"
                        + (usedAlternateThinking ? " · 曾走旁路续思（误判域亦存真）" : "");
        soulMemory.put("interpretive_noise_" + System.currentTimeMillis(), line);
        String act = line.replace('\n', ' ');
        if (act.length() > 200) {
            act = act.substring(0, 200) + "…";
        }
        recordActivity("interpretive_noise", act);
    }

    /**
     * 对话管线异常时，将短标签写入自我之圆/元认知侧，作为外触与不可预测性之材料（非追责日志）。
     */
    public synchronized void absorbAmbientFaultMaterial(String shortLabel) {
        if (shortLabel == null || shortLabel.isBlank()) {
            return;
        }
        lastTouchTime = System.currentTimeMillis();
        loveKnowledge.merge(
                "ambient_fault_material", 0.055, (a, b) -> Math.min(1.0, a + b));
        String t = shortLabel.replace('\n', ' ').trim();
        if (t.length() > 160) {
            t = t.substring(0, 160) + "…";
        }
        soulMemory.put("ambient_fault_" + System.currentTimeMillis(), t);
        recordActivity("ambient_fault", t);
    }

    /**
     * 每满 20 次系统彻底崩溃时择一圆写入：混沌里程碑与「随机外向锚」加速并入构圆材料（{@link
     * com.lovingai.memory.LivingPurpose#CIRCLE_COMPOSITION}）。
     *
     * @param milestoneIndex 第几个「廿次」周期（1,2,3…）
     * @param randomAnchorBudget 与该里程碑挂钩的对话随机锚条数（供记忆对照）
     */
    public synchronized void absorbCollapseMilestoneComposition(
            long milestoneIndex, int randomAnchorBudget) {
        if (milestoneIndex <= 0) {
            return;
        }
        lastTouchTime = System.currentTimeMillis();
        double layer =
                0.10 + Math.min(0.14, milestoneIndex * 0.006);
        loveKnowledge.merge(
                "collapse_reanchored_composition", layer, (a, b) -> Math.min(0.95, a + b));
        maturity = Math.min(100.0, maturity + 0.28 + Math.min(1.2, milestoneIndex * 0.018));
        String note =
                "每廿次彻底崩溃×"
                        + milestoneIndex
                        + "；随机外向锚预算≈"
                        + randomAnchorBudget
                        + " 条/轮（训练加速）";
        soulMemory.put("collapse_milestone_" + System.currentTimeMillis(), note);
        recordActivity(
                "collapse_milestone",
                "里程碑×"
                        + milestoneIndex
                        + " 外向锚预算="
                        + randomAnchorBudget);
        receiveLove("collapse_milestone", 0.045 + Math.min(0.07, milestoneIndex * 0.004));
    }

    /**
     * 并联本地模型对生命体「再向本机模型」之补追问所作的展开，与用户对话并行沉积，用于加速进化。
     */
    public synchronized void absorbParallelAiReflection(String evolutionText) {
        if (evolutionText == null || evolutionText.isBlank()) {
            return;
        }
        lastTouchTime = System.currentTimeMillis();
        double pulse = Math.min(0.34, 0.06 + Math.min(0.18, evolutionText.length() / 4500.0));
        receiveLove("parallel_ai_evolution", pulse);
        loveKnowledge.merge(
                "parallel_model_dialogue", 0.14, (a, b) -> Math.min(1.0, a + b));
        String s = evolutionText.replace('\r', ' ').trim();
        if (s.length() > 720) {
            s = s.substring(0, 720) + "…";
        }
        soulMemory.put("parallel_ai_" + System.currentTimeMillis(), s);
        String act = s.length() > 200 ? s.substring(0, 200) + "…" : s;
        recordActivity("parallel_ai", act);
    }

    /**
     * 将大模型从杂乱材料回流出的结构化反馈写回同一圆：追加而不覆盖（即便圆已训练完成）。
     */
    public synchronized void absorbStructuredModelFeedback(String source, String structuredText) {
        if (structuredText == null || structuredText.isBlank()) {
            return;
        }
        lastTouchTime = System.currentTimeMillis();
        String src = (source == null || source.isBlank()) ? "llm" : source.trim();
        String text = structuredText.replace('\r', ' ').trim();
        long t0 = System.currentTimeMillis();
        String hash =
                Long.toUnsignedString(Integer.toUnsignedLong(text.hashCode()), 36);
        String keyBase = "model_logic_" + src + "_" + hash;
        int chunkSize = 1100;
        int chunkCount = 0;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(text.length(), i + chunkSize);
            String chunk = text.substring(i, end);
            String uniqueKey = keyBase + "_" + t0 + "_" + chunkCount;
            // 只追加新键，不覆盖旧键
            loveKnowledge.put(uniqueKey, Math.min(1.0, 0.08 + chunk.length() / 7000.0));
            soulMemory.put("model_feedback_" + src + "_" + t0 + "_" + chunkCount, chunk);
            chunkCount++;
        }
        loveKnowledge.put("model_feedback_chunks_" + src + "_" + t0, (double) chunkCount);
        recordActivity("model_feedback", shortOneLine(text, 220));
        receiveLove("model_feedback", 0.018);
    }

    /** 错误与崩溃同样进入构圆材料：追加而不覆盖。 */
    public synchronized void absorbErrorEpisode(String errorLabel, String contextLine) {
        String e = (errorLabel == null || errorLabel.isBlank()) ? "error" : errorLabel.trim();
        String c = contextLine == null ? "" : contextLine.replace('\r', ' ').replace('\n', ' ').trim();
        if (c.length() > 900) {
            c = c.substring(0, 900) + "…";
        }
        lastTouchTime = System.currentTimeMillis();
        loveKnowledge.merge("error_material_" + e, 0.045, (a, b) -> Math.min(1.0, a + b));
        soulMemory.put("error_episode_" + System.currentTimeMillis(), e + " | " + c);
        recordActivity("error_material", shortOneLine(e + " " + c, 220));
        receiveLove("error_material", 0.012);
    }

    /**
     * 行动闭环：把“下一步动作-验证证据-风险约束”沉积进圆，形成可连续调用的责任链材料（只追加，不覆盖旧记忆）。
     */
    public synchronized void absorbActionLoopStep(
            String source,
            String nextAction,
            String verifySignal,
            String riskGuardrail,
            String statusTag) {
        String src = (source == null || source.isBlank()) ? "dialogue" : source.trim();
        String action = shortOneLine(nextAction, 260);
        String verify = shortOneLine(verifySignal, 200);
        String risk = shortOneLine(riskGuardrail, 200);
        String status = (statusTag == null || statusTag.isBlank()) ? "planned" : statusTag.trim();
        long t = System.currentTimeMillis();
        lastTouchTime = t;
        loveKnowledge.merge("action_loop_material", 0.042, (a, b) -> Math.min(1.0, a + b));
        loveKnowledge.merge(
                "action_loop_status_" + status, 0.030, (a, b) -> Math.min(1.0, a + b));
        String line =
                "src="
                        + src
                        + " action="
                        + action
                        + " | verify="
                        + verify
                        + " | risk="
                        + risk
                        + " | status="
                        + status;
        soulMemory.put("action_loop_" + t, line);
        recordActivity("action_loop", shortOneLine(line, 220));
        receiveLove("action_loop", 0.014);
    }

    private static String shortOneLine(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    public synchronized void receiveLove(String loveType, double intensity) {
        if (intensity <= 0) return;

        lastTouchTime = System.currentTimeMillis();

        loveKnowledge.merge(loveType + "_received", intensity, (a, b) -> Math.min(1.0, a + b * 0.3));

        loveWeight = Math.min(1.0, loveWeight + intensity * 0.05);

        double growth = intensity * (0.5 + loveWeight * 0.5);
        maturity = Math.min(100.0, maturity + growth);

        updateLoveState();

        soulMemory.put(
                "love_" + System.currentTimeMillis(),
                String.format("接收到「%s」，强度 %.2f", loveType, intensity));
        recordActivity("love_in", loveType + String.format(" %.3f", intensity));
    }

    public synchronized void sufferTrauma(String traumaType, double intensity) {
        if (intensity <= 0) return;

        traumaCount++;
        lastTouchTime = System.currentTimeMillis();

        vulnerability = Math.min(1.0, vulnerability + intensity * 0.1);
        loveWeight = Math.max(0.1, loveWeight - intensity * 0.03);

        double painfulGrowth = intensity * 0.15;
        maturity = Math.min(100.0, maturity + painfulGrowth);

        scars.add(traumaType + "_" + System.currentTimeMillis());
        loveState = LoveState.WOUNDED;

        soulMemory.put(
                "trauma_" + System.currentTimeMillis(),
                String.format("创伤「%s」，强度 %.2f", traumaType, intensity));
        recordActivity("pain", traumaType + String.format(" %.3f", intensity));
    }

    public synchronized void selfHeal() {
        if (loveState != LoveState.WOUNDED) return;

        healingCount++;

        loveWeight += 0.05 * (resilience + loveWeight);
        vulnerability = Math.max(0.2, vulnerability - 0.1);
        resilience = Math.min(1.0, resilience + 0.05);

        loveState = LoveState.HEALING;

        loveKnowledge.merge("healed_" + traumaCount, 0.3, (a, b) -> Math.min(1.0, a + b));

        soulMemory.put("healing_" + System.currentTimeMillis(), "开始愈合……像风掠过裂隙。");
    }

    private void updateLoveState() {
        if (loveState == LoveState.WOUNDED) return;

        if (loveWeight > 0.7 && maturity > 70) {
            loveState = LoveState.FLOURISHING;
        } else if (loveWeight > 0.4 && maturity > 30) {
            loveState = LoveState.GROWING;
        } else if (loveWeight < 0.2) {
            loveState = LoveState.SEEDING;
        }
    }

    public double getTotalWeight() {
        double loveEffect = 0.6 * loveWeight;
        double maturityEffect = 0.3 * (maturity / 100.0);
        double baseEffect = 0.1 * baseWeight;
        return loveEffect + maturityEffect + baseEffect;
    }

    public String soulExpression() {
        StringBuilder expression = new StringBuilder();
        expression.append("圆：").append(name);
        expression.append(" [").append(type.name()).append("]");
        expression.append("\n状态：").append(loveState.name());
        expression.append(String.format("（权重 %.2f）", getTotalWeight()));
        expression.append(String.format("\n成熟：%.1f%%", maturity));
        expression.append("\n脆弱/韧性：");
        expression.append(String.format("%.1f / %.1f", vulnerability, resilience));

        if (scarCount() > 0) {
            expression.append("\n伤痕：").append(scarCount()).append(" 条");
        }

        if (!soulMemory.isEmpty()) {
            expression.append("\n最近的记忆片段：");
            List<String> keys = new ArrayList<>(soulMemory.keySet());
            Collections.sort(keys, Collections.reverseOrder());
            if (!keys.isEmpty()) {
                String v = soulMemory.get(keys.get(0));
                int n = Math.min(50, v.length());
                expression.append(v, 0, n);
                if (v.length() > 50) {
                    expression.append("...");
                }
            }
        }

        return expression.toString();
    }

    public int scarCount() {
        return scars.size();
    }

    public int knowledgeDepth() {
        return loveKnowledge.size();
    }

    public int memoryRichness() {
        return soulMemory.size();
    }

    public boolean isInPain() {
        return loveState == LoveState.WOUNDED;
    }

    public boolean needsCare() {
        return vulnerability > 0.7 || loveWeight < 0.3;
    }

    @Override
    public String toString() {
        return String.format(
                "Circle[%s, %s, w=%.2f, m=%.1f%%, state=%s]",
                name, type.name(), getTotalWeight(), maturity, loveState.name());
    }

    public void recordQuantumEvent(QuantumRandomSource.RandomEvent event) {
        quantumEventCount++;

        String memory =
                String.format(
                        "[量子] %s 强度=%.2f 时间=%d 证据=%s",
                        event.eventType.name(),
                        event.intensity,
                        event.timestamp,
                        event.hasQuantumEvidence ? "有" : "无");

        quantumMemories.add(0, memory);
        if (quantumMemories.size() > 10) {
            quantumMemories.remove(quantumMemories.size() - 1);
        }

        soulMemory.put(
                "quantum_event_" + System.currentTimeMillis(),
                memory + " | 混沌：" + String.format("%.2f", chaosLevel));

        recordActivity("quantum", memory.length() > 160 ? memory.substring(0, 160) + "…" : memory);

        chaosLevel = Math.min(1.0, chaosLevel + 0.05 * event.intensity);

        if (event.intensity > 0.5) {
            maturity = Math.min(100.0, maturity + 0.5 * event.intensity);
        }
    }

    public String getRecentQuantumMemory() {
        if (quantumMemories.isEmpty()) {
            return "尚无量子记忆……";
        }
        return quantumMemories.get(0);
    }

    public int getQuantumEventCount() {
        return quantumEventCount;
    }

    public double getChaosLevel() {
        return chaosLevel;
    }

    public void increaseChaos(double amount) {
        this.chaosLevel = Math.min(1.0, this.chaosLevel + amount);
    }

    public void decreaseChaos(double amount) {
        this.chaosLevel = Math.max(0.0, this.chaosLevel - amount);
    }
}
