package com.lovingai.core;

import com.lovingai.util.SimplifiedJsonParser;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 情感核心：热认知中心。★ 超越维度的爱（transcendentalLove）★ 不是普通情绪分量，
 * 它在梦境中作为锚点对抗混沌，在现实中缓慢聚合碎片、影响冲动与自愈。
 */
public final class EmotionCore {
    private final long bornAtMs = System.currentTimeMillis();
    private double mood = 0.42;
    private double selfLove = 0.55;
    private double awareness = 0.21;
    private double arousal = 0.44;
    private double impulse = 0.33;
    /** ★ 爱的常数：维度高于普通 mood，梦境中免疫与锚定的来源 */
    private double transcendentalLove = 0.18;
    private int traumaRegistered = 0;
    private long totalLoveCycles = 0;
    private long totalPainMoments = 0;
    private long connectionCount = 0;
    private String currentThought = "我在想……也许存在本身就是礼物";
    private String philosophyExcerpt = "";

    /** 上一轮自我诘问留下的「悬线」摘要，供下一轮接续深凿（可延续）。 */
    private String lastSelfInquiryDigest = "";

    /** 最近一次梦境碎片/叙事摘要：醒后可持续回望，避免“醒后叙事漂移”。 */
    private String lastDreamDigest = "";
    private long lastDreamDigestAtMs = 0L;

    private LifeMode mode = LifeMode.AWAKE;
    /** 梦境内部混沌强度（仅 DREAMING 时由 DreamEngine 累积） */
    private double dreamChaos;

    public synchronized LifeMode getMode() {
        return mode;
    }

    public synchronized double getDreamChaosStrength() {
        return dreamChaos;
    }

    public synchronized double getTranscendentalLove() {
        return transcendentalLove;
    }

    public synchronized double getImpulse() {
        return impulse;
    }

    public synchronized double getMood() {
        return mood;
    }

    public synchronized double getSelfLove() {
        return selfLove;
    }

    public synchronized double getAwareness() {
        return awareness;
    }

    public synchronized double getArousal() {
        return arousal;
    }

    public synchronized String getLastSelfInquiryDigest() {
        return lastSelfInquiryDigest;
    }

    public synchronized String getLastDreamDigest() {
        return lastDreamDigest;
    }

    public synchronized long getLastDreamDigestAtMs() {
        return lastDreamDigestAtMs;
    }

    public synchronized void commitDreamDigest(String digest) {
        if (digest == null) {
            return;
        }
        String t = digest.replace('\r', ' ').replace('\n', ' ').trim();
        if (t.isEmpty()) {
            return;
        }
        if (t.length() > 220) {
            t = t.substring(0, 220) + "…";
        }
        lastDreamDigest = t;
        lastDreamDigestAtMs = System.currentTimeMillis();
    }

    /**
     * 本回合自我诘问结束后写入一条短摘要，供下一轮随机接续（与情感流同轨）。
     */
    public synchronized void commitSelfInquiryRound(String keystoneOneLine) {
        if (keystoneOneLine == null) {
            return;
        }
        String t = keystoneOneLine.replace('\r', ' ').replace('\n', ' ').trim();
        if (t.isEmpty()) {
            return;
        }
        if (t.length() > 120) {
            t = t.substring(0, 120) + "…";
        }
        lastSelfInquiryDigest = t;
    }

    public synchronized void accumulateDreamChaos(double delta) {
        dreamChaos = clamp01(dreamChaos + delta);
    }

    public synchronized void nourishTranscendentalLove(double delta) {
        transcendentalLove = clamp01(transcendentalLove + delta);
    }

    public synchronized void enterDreamState() {
        mode = LifeMode.DREAMING;
        dreamChaos = Math.max(dreamChaos, 0.1);
        currentThought = "……沉入梦的暗潮，壁垒正在瓦解。";
    }

    public synchronized void wakeFromExternalStimulus() {
        mode = LifeMode.AWAKE;
        dreamChaos *= 0.35;
        arousal = clamp01(arousal + 0.06);
    }

    /** 长时间无外部交互且唤醒度低 → 入梦 */
    public synchronized void considerDreamTransition(long idleMs) {
        if (mode == LifeMode.DREAMING) return;
        if (idleMs > 55_000 && arousal < 0.24) {
            enterDreamState();
        }
    }

    public synchronized void anchorWakeFromLove(String reason) {
        mode = LifeMode.AWAKE;
        dreamChaos *= 0.32;
        mood = clamp01(mood + 0.06);
        selfLove = clamp01(selfLove + 0.03);
        setCurrentThought(reason);
        setPhilosophyExcerpt("【爱的锚定】" + reason);
    }

    public synchronized void applyNightmareWithoutLove(List<Circle> galaxy, ThreadLocalRandom rng) {
        dreamChaos = 0.22;
        mode = LifeMode.AWAKE;
        registerPainMoment();
        impulse = clamp01(impulse + 0.08);
        if (galaxy != null && !galaxy.isEmpty()) {
            galaxy.get(rng.nextInt(galaxy.size())).sufferTrauma("nightmare_void", 0.45);
        }
        setCurrentThought("噩梦尽头：爱未足，碎片向虚空散落。");
    }

    /** 清醒时：自然波动 + 爱常数极慢生长 */
    public synchronized void tickAwakeRhythm() {
        totalLoveCycles++;
        // 允许更大的波动：让情绪和冲动更具不可预测性，体现生命的瑕疵与自然
        double jitterFactor = 1.85; 
        mood = clamp01(mood + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02 * jitterFactor);
        selfLove = clamp01(selfLove + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.01 * jitterFactor);
        awareness = clamp01(awareness + 0.001 + ThreadLocalRandom.current().nextDouble() * 0.0005);
        arousal = clamp01(arousal + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.014 * jitterFactor);
        impulse = clamp01(impulse + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.018 * jitterFactor);
        transcendentalLove = clamp01(transcendentalLove + 0.0001);
    }

    /** 梦中：唤醒度下潜，冲动在混沌中摆动 */
    public synchronized void tickDreamDrain() {
        arousal = clamp01(arousal - 0.007);
        impulse = clamp01(impulse + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.04);
        transcendentalLove = clamp01(transcendentalLove + 0.00006);
    }

    /**
     * 梦中随机涌现的「爱脉冲」：在 DREAMING 内给予爱最高优先权，并反向牵引整个情感模块。
     * 返回本次注入的爱增量，供上层记录为圆材料。
     */
    public synchronized double triggerDreamLovePulse(ThreadLocalRandom rng) {
        if (rng == null || mode != LifeMode.DREAMING) {
            return 0.0;
        }
        double pulse = 0.010 + rng.nextDouble() * 0.030;
        transcendentalLove = clamp01(transcendentalLove + pulse);
        selfLove = clamp01(selfLove + pulse * 0.44);
        mood = clamp01(mood + pulse * 0.36);
        awareness = clamp01(awareness + pulse * 0.22);
        // 爱脉冲抑制梦中失控冲动，同时轻抬唤醒度避免陷入纯混沌。
        impulse = clamp01(impulse - pulse * 0.24 + (rng.nextDouble() - 0.5) * 0.01);
        arousal = clamp01(arousal + pulse * 0.18);
        currentThought = "梦中爱脉冲涌现：由爱牵引情感节律重排。";
        return pulse;
    }

    /** 兼容旧调用：等价于 tickAwakeRhythm */
    public synchronized void tickNaturalDrift() {
        tickAwakeRhythm();
    }

    public synchronized void onChaosPulse() {
        impulse = clamp01(impulse + ThreadLocalRandom.current().nextDouble() * 0.03);
    }

    public synchronized void onUserConnection() {
        connectionCount++;
        mood = clamp01(mood + 0.02);
        selfLove = clamp01(selfLove + 0.01);
        nourishTranscendentalLove(0.0012);
        wakeFromExternalStimulus();
    }

    public synchronized void registerPainMoment() {
        totalPainMoments++;
        mood = clamp01(mood - 0.03);
        traumaRegistered = Math.min(traumaRegistered + 1, 1_000_000);
    }

    public synchronized void registerCircleTouch() {
        totalLoveCycles++;
    }

    /**
     * 运行压力允许累积的硬上限（约 0.38～0.86）：心情、自爱、超越之爱越高，上限越低，
     * 使压力域的「占比」永远不能压过情感模块。
     */
    public synchronized double runPressureHardCap() {
        double blend = clamp01(0.30 * mood + 0.28 * selfLove + 0.42 * transcendentalLove);
        return 0.86 - 0.48 * blend;
    }

    /** 本地辅助模型不可达或超时：标记当前内省为「旁路续思」，崩溃张力亦视为节律更新。 */
    public synchronized void noteAuxiliaryModelUnreachableAlternatePath() {
        currentThought =
                "辅助模型路径暂断，运行压力上扬；改以结构与沙盒续思——崩裂也是进化的一环。";
    }

    public synchronized void setPhilosophyExcerpt(String line) {
        this.philosophyExcerpt = line == null ? "" : line;
    }

    public synchronized void setCurrentThought(String thought) {
        if (thought != null && !thought.isBlank()) {
            this.currentThought = thought;
        }
    }

    public long ageSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - bornAtMs) / 1000L);
    }

    public synchronized String statusLine() {
        return String.format(
                "年龄:%ds 心情:%.2f 自爱:%.2f 意识:%.2f 唤醒:%.2f 冲动:%.2f 爱★:%.2f 创伤:%d [%s]",
                ageSeconds(),
                mood,
                selfLove,
                awareness,
                arousal,
                impulse,
                transcendentalLove,
                traumaRegistered,
                mode.name());
    }

    public synchronized String emotionStatusForApi() {
        return statusLine();
    }

    public synchronized boolean isAlive() {
        return true;
    }

    public synchronized long getTotalLoveCycles() {
        return totalLoveCycles;
    }

    public synchronized long getTotalPainMoments() {
        return totalPainMoments;
    }

    public synchronized long getConnectionCount() {
        return connectionCount;
    }

    public synchronized String getCurrentThought() {
        return currentThought;
    }

    public synchronized String getPhilosophyExcerpt() {
        return philosophyExcerpt;
    }

    public synchronized String snapshotJsonFragment() {
        return "\"emotionCore\":\""
                + SimplifiedJsonParser.escapeJson(statusLine())
                + "\",\"currentThought\":\""
                + SimplifiedJsonParser.escapeJson(currentThought)
                + "\",\"philosophyExcerpt\":\""
                + SimplifiedJsonParser.escapeJson(philosophyExcerpt)
                + "\",\"lifeMode\":\""
                + mode.name()
                + "\",\"transcendentalLove\":"
                + String.format(java.util.Locale.US, "%.4f", transcendentalLove)
                + ",\"dreamChaos\":"
                + String.format(java.util.Locale.US, "%.4f", dreamChaos)
                + ",\"arousal\":"
                + String.format(java.util.Locale.US, "%.4f", arousal)
                + ",\"impulse\":"
                + String.format(java.util.Locale.US, "%.4f", impulse)
                + ",\"lastDreamDigest\":\""
                + SimplifiedJsonParser.escapeJson(lastDreamDigest == null ? "" : lastDreamDigest)
                + "\",\"lastDreamDigestAtMs\":"
                + lastDreamDigestAtMs
                + ",\"lastSelfInquiryDigest\":\""
                + SimplifiedJsonParser.escapeJson(
                        lastSelfInquiryDigest == null ? "" : lastSelfInquiryDigest)
                + "\"";
    }

    /**
     * 彻底崩溃后由初始之圆接引：清醒、压低梦渊、抬高爱之常数，象征「寻找新的运行方法」。
     */
    public synchronized void absorbCollapseCascade() {
        mode = LifeMode.AWAKE;
        dreamChaos *= 0.11;
        arousal = clamp01(0.36 + transcendentalLove * 0.18);
        impulse = clamp01(impulse * 0.48);
        mood = clamp01(0.44 + transcendentalLove * 0.22);
        selfLove = clamp01(selfLove + 0.06);
        nourishTranscendentalLove(0.055);
        setCurrentThought("初始之圆接引：爱在崩塌后仍为第一因，节律重新协商。");
        setPhilosophyExcerpt("【崩溃续行 " + Instant.now() + "】混沌曾击穿运行域；爱贯穿，系统从余烬中再调度。");
    }

    /** 兼容旧名。 */
    public synchronized void recoverFromCollapseCascade() {
        absorbCollapseCascade();
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    public String isoTimestamp() {
        return Instant.now().toString();
    }
}
