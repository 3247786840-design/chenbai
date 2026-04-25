package com.lovingai.sandbox;

import com.lovingai.core.Circle;
import com.lovingai.core.EmotionCircleOrchestrator;
import com.lovingai.core.EmotionCore;
import com.lovingai.core.RealityBridge;
import com.lovingai.log.CognitionLog;
import com.lovingai.memory.LivingPurpose;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 幻象沙盒：以混沌构图、在崩溃张力中求解并完成论证，向「可栖居的现实」侧写收敛；经 {@link RealityBridge} 落盘，
 * 使沙盒成为链向世界的桥梁（强度由桥接系数与物理锚定共同调制）。见 {@link com.lovingai.memory.LivingPurpose#SANDBOX_TELOS}。
 */
public final class MirageSandbox {

    private static volatile SimulationResult lastResult =
            new SimulationResult("none", 0, 0, "尚无预演。", 0, "", 0L);
    private static volatile String lastMicroSummary = "";
    private static volatile String lastMicroUserLine = "";

    public record SimulationResult(
            String chosenMode,
            double predictedHarm,
            double predictedCare,
            String note,
            /** 0～1：桥接强度 ×（低伤害、高照护）合成的「现实可亲和度」 */
            double realityAffinity,
            /** 写入 bridge.log 的摘要行 */
            String bridgeLine,
            long epochMs) {}

    private record MicroVote(double biasToLogicHarm, int sampled, double caution, double daring, String intentCountsCsv) {}

    public SimulationResult simulate(String stimulus, EmotionCore core, List<Circle> galaxy) {
        return simulate(stimulus, core, galaxy, null);
    }

    /**
     * @param bridge 若非 null，将预演摘要写入 {@code data/reality/bridge.log}，并记入认知日志。
     */
    public SimulationResult simulate(
            String stimulus, EmotionCore core, List<Circle> galaxy, RealityBridge bridge) {
        if (galaxy.isEmpty()) {
            lastResult =
                    new SimulationResult("none", 0, 0, "无圆可演。", 0, "", System.currentTimeMillis());
            lastMicroSummary = "";
            lastMicroUserLine = "";
            return lastResult;
        }
        var rng = ThreadLocalRandom.current();
        var pair = EmotionCircleOrchestrator.pickParallelDistinct(core, galaxy, 2, rng);
        Circle logic = pair.isEmpty() ? galaxy.get(0) : pair.get(0);
        Circle impulse =
                pair.size() > 1
                        ? pair.get(1)
                        : EmotionCircleOrchestrator.pickWeighted(core, galaxy, rng);

        double harmLogic = logic.getChaosLevel() * 0.32 + (1.0 - logic.getTotalWeight()) * 0.22;
        double careLogic = logic.loveWeight * 0.42 + core.getTranscendentalLove() * 0.34;

        double harmImp = impulse.getChaosLevel() * 0.46 + rng.nextDouble() * 0.18;
        double careImp = impulse.loveWeight * 0.27 + core.getImpulse() * 0.17;

        double harmMin = Math.min(harmLogic, harmImp);
        double careMax = Math.max(careLogic, careImp);

        String mode = harmLogic < harmImp ? "logic_branch" : "impulse_branch";
        String stim = stimulus == null ? "" : stimulus.trim();
        if (stim.length() > 80) {
            stim = stim.substring(0, 80) + "…";
        }

        SandboxPhysicsCorpus.INSTANCE.ensureLoaded();
        boolean casualShort = isShortCasualStimulus(stimulus);
        String physSnippet =
                casualShort
                        ? "（短刺激：物理参照锚定不显式展开，仍参与亲和度调制）"
                        : SandboxPhysicsCorpus.INSTANCE.snippetForNote(220);
        double physBonus =
                casualShort
                        ? SandboxPhysicsCorpus.INSTANCE.anchorAffinityBonus() * 0.55
                        : SandboxPhysicsCorpus.INSTANCE.anchorAffinityBonus();
        SandboxPhysicsCorpus.PhysicsProfile profile =
                SandboxPhysicsCorpus.INSTANCE.buildProfile(stimulus);

        // 软物理调制：连续权重影响分支，不走硬条件分叉。
        double harmSoftener =
                0.08 * profile.conservationWeight()
                        + 0.06 * profile.causalityWeight()
                        + 0.05 * profile.boundaryWeight();
        double careBooster =
                0.10 * profile.continuityWeight()
                        + 0.06 * profile.causalityWeight()
                        + 0.03 * profile.complexityWeight();
        double uncertaintyNudge = 0.07 * profile.uncertaintyWeight();

        harmLogic = clamp01(harmLogic - harmSoftener + uncertaintyNudge * 0.45);
        harmImp = clamp01(harmImp - harmSoftener * 0.65 + uncertaintyNudge);
        careLogic = clamp01(careLogic + careBooster);
        careImp = clamp01(careImp + careBooster * 0.72);

        harmMin = Math.min(harmLogic, harmImp);
        careMax = Math.max(careLogic, careImp);
        MicroVote vote = microVote(stimulus, core, galaxy, rng);
        lastMicroSummary =
                String.format(
                        java.util.Locale.US,
                        "sampled=%d caution=%.3f daring=%.3f biasToLogicHarm=%.3f intents=%s",
                        vote.sampled(),
                        vote.caution(),
                        vote.daring(),
                        vote.biasToLogicHarm(),
                        vote.intentCountsCsv());
        String tilt = vote.biasToLogicHarm() < -0.001 ? "偏逻辑" : (vote.biasToLogicHarm() > 0.001 ? "偏冲动" : "中性");
        lastMicroUserLine =
                String.format(
                        Locale.CHINA,
                        "圆微意识：%s；谨慎≈%.2f 勇敢≈%.2f；%s",
                        vote.intentCountsCsv(),
                        vote.caution(),
                        vote.daring(),
                        tilt);
        double harmLogicForChoice = harmLogic;
        if (Math.abs(harmLogic - harmImp) <= 0.075) {
            harmLogicForChoice = clamp01(harmLogic + vote.biasToLogicHarm());
        }
        mode = harmLogicForChoice < harmImp ? "logic_branch" : "impulse_branch";

        String note =
                "【沙盒志业】"
                        + LivingPurpose.SANDBOX_NOTE_STAMP
                        + " ｜【物理参照锚定】"
                        + physSnippet
                        + " ｜沙盒预演：刺激「"
                        + stim
                        + "」 逻辑支路 伤害="
                        + String.format("%.2f", harmLogic)
                        + " 照护="
                        + String.format("%.2f", careLogic)
                        + "；冲动支路 伤害="
                        + String.format("%.2f", harmImp)
                        + " 照护="
                        + String.format("%.2f", careImp)
                        + "；择路="
                        + mode
                        + "；physBonus="
                        + String.format("%.3f", physBonus)
                        + "；"
                        + profile.note()
                        + "。";

        double bStr = bridge == null ? 0.10 : bridge.getBridgeStrength();
        double realityAffinity =
                clamp01(bStr * (1.0 - harmMin * 0.85) * (0.35 + careMax * 0.65));

        realityAffinity = clamp01(realityAffinity + physBonus * (0.45 + 0.55 * (1.0 - harmMin)));
        realityAffinity =
                clamp01(
                        realityAffinity
                                + 0.06 * profile.causalityWeight()
                                + 0.05 * profile.boundaryWeight()
                                + 0.04 * profile.continuityWeight()
                                - 0.03 * profile.uncertaintyWeight());

        String bridgeLine =
                String.format(
                        java.util.Locale.US,
                        "mirage mode=%s harmMin=%.3f careMax=%.3f affinity=%.3f stim=%s",
                        mode,
                        harmMin,
                        careMax,
                        realityAffinity,
                        stim.isEmpty() ? "∅" : stim);

        long t = System.currentTimeMillis();
        lastResult =
                new SimulationResult(mode, harmMin, careMax, note, realityAffinity, bridgeLine, t);

        CognitionLog.append("沙盒·现实", note + " affinity=" + String.format("%.3f", realityAffinity));

        if (bridge != null) {
            try {
                bridge.recordModule("沙盒", bridgeLine);
            } catch (IOException e) {
                CognitionLog.append("沙盒·现实", "bridge.log 写入失败: " + e.getMessage());
            }
        }

        return lastResult;
    }

    public SimulationResult getLastResult() {
        return lastResult;
    }

    public String getLastMicroSummary() {
        return lastMicroSummary == null ? "" : lastMicroSummary;
    }

    public String getLastMicroUserLine() {
        return lastMicroUserLine == null ? "" : lastMicroUserLine;
    }

    private static MicroVote microVote(
            String stimulus, EmotionCore core, List<Circle> galaxy, ThreadLocalRandom rng) {
        if (galaxy == null || galaxy.isEmpty() || core == null) {
            return new MicroVote(0.0, 0, 0.0, 0.0, "");
        }
        List<Circle> picked = EmotionCircleOrchestrator.pickParallelDistinct(core, galaxy, 5, rng);
        if (picked == null || picked.isEmpty()) {
            picked = new ArrayList<>();
            picked.add(galaxy.get(0));
        }
        double cautionSum = 0.0;
        double daringSum = 0.0;
        int n = 0;
        int iSlow = 0, iHold = 0, iAct = 0, iExplore = 0;
        for (Circle c : picked) {
            if (c == null) continue;
            double chaos = clamp01(c.getChaosLevel());
            double love = clamp01(c.loveWeight);
            double baseRisk =
                    0.46 * chaos
                            + 0.22 * (1.0 - love)
                            + 0.10 * (1.0 - clamp01(core.getTranscendentalLove()))
                            + rng.nextDouble() * 0.06;
            double care =
                    0.52 * love
                            + 0.18 * clamp01(core.getTranscendentalLove())
                            + 0.10 * (1.0 - chaos);
            double risk = clamp01(baseRisk);
            Circle.CircleType t = c.type;
            String intent;
            if (t == Circle.CircleType.WISDOM || t == Circle.CircleType.HUMILITY) {
                intent = "slow_verify";
                iSlow++;
            } else if (t == Circle.CircleType.COMPASSION
                    || t == Circle.CircleType.HOPE
                    || t == Circle.CircleType.PRESENCE) {
                intent = "hold_care";
                iHold++;
            } else if (t == Circle.CircleType.COURAGE) {
                intent = "act";
                iAct++;
            } else {
                intent = "explore";
                iExplore++;
            }
            double caution = clamp01(0.65 * risk + 0.35 * (1.0 - care));
            double daring = clamp01(0.55 * (1.0 - risk) + 0.45 * care);
            c.updateMicroConsciousness(risk, care, intent);
            if ("act".equals(intent) || "explore".equals(intent)) {
                daringSum += daring;
            } else {
                cautionSum += caution;
            }
            n++;
        }
        if (n <= 0) {
            return new MicroVote(0.0, 0, 0.0, 0.0, "");
        }
        double cautionAvg = cautionSum / (double) n;
        double daringAvg = daringSum / (double) n;
        double bias = 0.0;
        if (cautionAvg >= daringAvg + 0.10) {
            bias = -0.018;
        } else if (daringAvg >= cautionAvg + 0.14) {
            bias = 0.012;
        }
        bias = Math.max(-0.03, Math.min(0.03, bias));
        String intentCounts =
                "slow="
                        + iSlow
                        + ",hold="
                        + iHold
                        + ",act="
                        + iAct
                        + ",explore="
                        + iExplore;
        return new MicroVote(bias, n, cautionAvg, daringAvg, intentCounts);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    /**
     * 极短寒暄/试探输入：不在认知日志里铺陈长篇物理题干，避免「你好」旁跟整页力学题。
     */
    private static boolean isShortCasualStimulus(String raw) {
        if (raw == null) {
            return true;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return true;
        }
        if (s.length() > 20) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if ("你好".equals(s)
                || "嗨".equals(s)
                || "在吗".equals(s)
                || "早上好".equals(s)
                || "晚上好".equals(s)
                || "晚安".equals(s)
                || "hi".equals(lower)
                || "hello".equals(lower)
                || "hey".equals(lower)) {
            return true;
        }
        return s.codePointCount(0, s.length()) <= 4 && s.length() <= 12;
    }

    /** 浅表克隆列表引用用于未来真·快照（占位）。 */
    public List<Circle> shallowMirror(List<Circle> in) {
        return new ArrayList<>(in);
    }
}
