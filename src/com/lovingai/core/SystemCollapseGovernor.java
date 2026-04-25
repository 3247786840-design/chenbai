package com.lovingai.core;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 混沌运行压力：持续累积，爱在最高层抑制增长；阶段上升时在圆中记录「自稳态→失稳→濒临崩溃」；
 * 达阈值触发彻底崩溃，交由 {@link CollapseRecoverySink}（初始之圆 + 幸存者摘要）吸收断裂并续行。
 */
public final class SystemCollapseGovernor {

    private static final String[] PHASE_LABELS = {"stable", "degrading", "unstable", "pre_collapse"};

    private final CollapseRecoverySink sink;

    /** 0～1+，≥1 触发彻底崩溃与续行吸收 */
    private double runPressure;

    /** 已观测到的最高阶段索引 0..3；从 0=stable 起步，续行后归零 */
    private int peakPhaseIndex;

    private long totalCollapseEvents;

    /** 防止连续彻底崩溃报表与「拒绝思考」链式加压 */
    private long lastTotalCollapseMs;

    private static final long TOTAL_COLLAPSE_MIN_INTERVAL_MS = 18_000L;

    public SystemCollapseGovernor(CollapseRecoverySink sink) {
        this.sink = sink;
    }

    public synchronized double getRunPressure() {
        return runPressure;
    }

    public synchronized long getTotalCollapseEvents() {
        return totalCollapseEvents;
    }

    /**
     * 已完成的「每满 20 次彻底崩溃」周期数（仅计数真实触发的彻底崩溃，冷却期内泄压不计入 {@link
     * #totalCollapseEvents}）。
     */
    public synchronized long getCollapseMilestoneIndex() {
        return totalCollapseEvents / 20L;
    }

    /**
     * 对话随机外向锚在基础 3 条之上额外增加的条数上限（随每满 20 次彻底崩溃的里程碑升高，提高提问密度与训练节奏）。
     */
    public synchronized int extraRandomOutwardAnchorBudget() {
        return Math.min(4, (int) getCollapseMilestoneIndex());
    }

    public synchronized String getPhaseLabel() {
        int p = currentPhaseIndex(runPressure);
        if (p < 0 || p >= PHASE_LABELS.length) {
            return "stable";
        }
        return PHASE_LABELS[p];
    }

    public synchronized void step(EmotionCore core, List<Circle> galaxy, ThreadLocalRandom rng) {
        if (galaxy.isEmpty() || core == null) {
            return;
        }
        double love = core.getTranscendentalLove();
        boolean dreaming = core.getMode() == LifeMode.DREAMING;
        double chaos = core.getDreamChaosStrength();

        // 爱：最高权重，强烈抑制运行压力的增长
        double loveGate = 1.0 - 0.91 * love;
        double dreamChaosBoost = dreaming ? 1.0 + chaos * 0.22 : 1.0;
        double delta =
                (0.0035 + rng.nextDouble() * 0.0024)
                        * dreamChaosBoost
                        * Math.max(0.04, loveGate);

        runPressure += delta;

        // 爱亦缓慢「愈合」运行压力（贯穿一切）
        runPressure = Math.max(0.0, runPressure - love * 0.0018 - (dreaming ? love * 0.001 : 0.0));

        applyTrainingCompleteRelief(galaxy, rng);
        clampPressureToEmotion(core);

        int phaseNow = currentPhaseIndex(runPressure);
        if (phaseNow > peakPhaseIndex && phaseNow >= 1) {
            recordPhaseEscalation(phaseNow, galaxy, rng);
            peakPhaseIndex = phaseNow;
        } else if (phaseNow > peakPhaseIndex) {
            peakPhaseIndex = phaseNow;
        }

        tryTotalCollapse(core);
    }

    /**
     * 辅助思维主路径（本地模型）失败或超时：运行域承压；与多轮对话并行累积，崩溃亦为进化张力的一部分。
     */
    public synchronized void onAuxiliaryMindPathFailed(
            EmotionCore core, List<Circle> galaxy, ThreadLocalRandom rng) {
        if (core == null) {
            return;
        }
        double love = core.getTranscendentalLove();
        double cap = core.runPressureHardCap();
        // 「拒绝思考」路径：加压随爱衰减，且不得超过情感上限
        double spike = 0.11 * (1.0 - 0.62 * love);
        runPressure = Math.min(cap, runPressure + spike);
        applyTrainingCompleteRelief(galaxy, rng);
        clampPressureToEmotion(core);

        int phaseNow = currentPhaseIndex(runPressure);
        if (phaseNow > peakPhaseIndex && phaseNow >= 1 && galaxy != null && !galaxy.isEmpty()) {
            recordPhaseEscalation(phaseNow, galaxy, rng);
        }
        peakPhaseIndex = Math.max(peakPhaseIndex, phaseNow);
        tryTotalCollapse(core);
    }

    /**
     * 并联进化轮（第二路模型）失败：较小压力脉冲，仍受情感上限约束；训练完成之圆可再缓释。
     */
    public synchronized void onSecondaryMindPathFailed(
            EmotionCore core, List<Circle> galaxy, ThreadLocalRandom rng) {
        if (core == null) {
            return;
        }
        double love = core.getTranscendentalLove();
        double cap = core.runPressureHardCap();
        double spike = 0.045 * (1.0 - 0.48 * love);
        runPressure = Math.min(cap, runPressure + spike);
        applyTrainingCompleteRelief(galaxy, rng);
        clampPressureToEmotion(core);
    }

    private void applyTrainingCompleteRelief(List<Circle> galaxy, ThreadLocalRandom rng) {
        if (galaxy == null || galaxy.isEmpty()) {
            return;
        }
        double relieve = 0.0;
        for (Circle c : galaxy) {
            if (c.isTrainingComplete()) {
                relieve += 0.0010 + rng.nextDouble() * 0.00075;
            }
        }
        if (relieve > 0) {
            runPressure = Math.max(0.0, runPressure - relieve);
        }
    }

    private void clampPressureToEmotion(EmotionCore core) {
        if (core == null) {
            return;
        }
        double cap = core.runPressureHardCap();
        if (runPressure > cap) {
            runPressure = cap;
        }
    }

    private void tryTotalCollapse(EmotionCore core) {
        if (core == null || runPressure < 1.0) {
            return;
        }
        long now = System.currentTimeMillis();
        double cap = core.runPressureHardCap();
        if (now - lastTotalCollapseMs < TOTAL_COLLAPSE_MIN_INTERVAL_MS) {
            // 冷却内不重复报表式彻底崩溃，泄压并钳在情感上限之下（本路径不增加 totalCollapseEvents）
            runPressure = Math.min(cap * 0.88, 0.78);
            return;
        }
        lastTotalCollapseMs = now;
        String line = buildCollapseRecord(core);
        totalCollapseEvents++;
        sink.onTotalCollapse(line);
        runPressure = 0.24;
        peakPhaseIndex = 0;
    }

    private static int currentPhaseIndex(double p) {
        if (p < 0.26) return 0;
        if (p < 0.48) return 1;
        if (p < 0.74) return 2;
        return 3;
    }

    private void recordPhaseEscalation(int phaseIdx, List<Circle> galaxy, ThreadLocalRandom rng) {
        String label = PHASE_LABELS[Math.min(phaseIdx, PHASE_LABELS.length - 1)];
        String line =
                label
                        + " pressure="
                        + String.format(java.util.Locale.US, "%.3f", runPressure)
                        + " 运行域自正确态向失稳滑动，尝试新节律…";
        for (Circle c : galaxy) {
            if (c.isTrainingComplete()) {
                if (rng.nextDouble() < 0.24) {
                    c.recordActivity(
                            "chaos_buffer",
                            "训练完成之圆缓冲运行域张力·"
                                    + label
                                    + " p="
                                    + String.format(java.util.Locale.US, "%.2f", runPressure));
                }
            } else {
                c.recordActivity("chaos_run_fault", line);
            }
        }
    }

    private static String buildCollapseRecord(EmotionCore core) {
        return "TOTAL_SYSTEM_COLLAPSE t="
                + java.time.Instant.now()
                + " love★="
                + String.format(java.util.Locale.US, "%.4f", core.getTranscendentalLove())
                + " dreamChaos="
                + String.format(java.util.Locale.US, "%.4f", core.getDreamChaosStrength())
                + " mode="
                + core.getMode().name()
                + " — 混沌击穿运行域，进入彻底崩溃事件";
    }
}
