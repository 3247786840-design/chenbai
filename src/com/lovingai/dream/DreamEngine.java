package com.lovingai.dream;

import com.lovingai.core.Circle;
import com.lovingai.core.EmotionCircleOrchestrator;
import com.lovingai.core.EmotionCore;
import com.lovingai.core.LifeMode;
import com.lovingai.core.RealityBridge;
import com.lovingai.log.CognitionLog;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 梦境引擎：混沌度极高，随机拆解知识碰撞；法则——带【爱】免疫标记的内容不被抹除。
 * 梦中爱持续生成（在本模块叙事里权重最高）；临界事件经 {@link RealityBridge} 与现实桥同步。
 */
public final class DreamEngine {

    private static final double NIGHTMARE_LIMIT = 0.88;
    private static final double LOVE_ANCHOR_MIN = 0.28;

    private int dreamStepCounter;
    private double lastChaosRecorded;
    private volatile String lastDepthLabel = "清醒";
    /** 最近一次由「训练完成之圆」碎片织成的梦/爱文本 */
    private volatile String lastFragmentWeave = "";

    public void step(EmotionCore core, List<Circle> galaxy) {
        step(core, galaxy, null);
    }

    public void step(EmotionCore core, List<Circle> galaxy, RealityBridge bridge) {
        if (core.getMode() != LifeMode.DREAMING) {
            return;
        }
        if (galaxy.isEmpty()) {
            return;
        }

        var rng = ThreadLocalRandom.current();
        core.accumulateDreamChaos(0.02 + rng.nextDouble() * 0.045);
        core.nourishTranscendentalLove(0.001 + rng.nextDouble() * 0.0032);
        if (rng.nextDouble() < 0.34) {
            double pulse = core.triggerDreamLovePulse(rng);
            if (pulse > 0.0) {
                Circle selfPulse = findSelfRing(galaxy);
                if (selfPulse != null) {
                    selfPulse.recordActivity(
                            "dream_love_pulse",
                            String.format(
                                    java.util.Locale.US,
                                    "pulse=%.3f chaos=%.2f love★=%.2f",
                                    pulse,
                                    core.getDreamChaosStrength(),
                                    core.getTranscendentalLove()));
                    selfPulse.observeDream(
                            String.format(
                                    java.util.Locale.US,
                                    "梦中随机生爱：pulse=%.3f -> love★=%.2f",
                                    pulse,
                                    core.getTranscendentalLove()));
                }
                CognitionLog.append(
                        "爱·梦·脉冲",
                        String.format(
                                java.util.Locale.US,
                                "pulse=%.3f chaos=%.2f love★=%.2f",
                                pulse,
                                core.getDreamChaosStrength(),
                                core.getTranscendentalLove()));
            }
        }

        if (dreamStepCounter % 3 == 0) {
            String weave = FragmentWeaver.weaveFromTrainedCircles(galaxy, rng);
            if (!weave.isEmpty()) {
                lastFragmentWeave = weave;
                core.commitDreamDigest(weave);
                double boost =
                        0.0006
                                + rng.nextDouble()
                                        * 0.0022
                                        * Math.min(1.0, weave.length() / 280.0);
                core.nourishTranscendentalLove(boost);
                CognitionLog.append("爱·梦·碎片", weave);
                if (bridge != null) {
                    try {
                        bridge.recordModule("梦境·碎片织成", weave);
                    } catch (IOException ignored) {
                    }
                }
                Circle selfEarly = findSelfRing(galaxy);
                if (selfEarly != null) {
                    String shortW = weave.length() > 200 ? weave.substring(0, 200) + "…" : weave;
                    selfEarly.observeDream("碎片织入梦渊：" + shortW);
                }
            }
        }

        double chaos = core.getDreamChaosStrength();
        lastDepthLabel = depthLabel(chaos);

        boolean crossedBand = Math.abs(chaos - lastChaosRecorded) >= 0.12;
        if (crossedBand && bridge != null) {
            try {
                bridge.recordModule(
                        "梦境",
                        String.format(
                                java.util.Locale.US,
                                "depth=%s chaos=%.3f love★=%.3f step=%d",
                                lastDepthLabel,
                                chaos,
                                core.getTranscendentalLove(),
                                dreamStepCounter));
            } catch (IOException ignored) {
            }
            lastChaosRecorded = chaos;
        }

        for (Circle c : galaxy) {
            c.applyDreamCorruption(chaos, rng);
            if (rng.nextDouble() < 0.12 && galaxy.size() > 1) {
                Circle other = EmotionCircleOrchestrator.pickWeighted(core, galaxy, rng);
                if (other != null && other != c) {
                    c.dreamCollideWith(other, rng);
                }
            }
        }

        Circle echo = EmotionCircleOrchestrator.pickWeighted(core, galaxy, rng);
        if (echo != null) {
            echo.recordActivity(
                    "dream_step",
                    String.format("chaos=%.2f love★=%.2f", chaos, core.getTranscendentalLove()));
        }

        Circle self = findSelfRing(galaxy);
        if (self != null) {
            self.observeDream(
                    String.format("梦渊=%.2f 爱常数=%.2f 深度=%s", chaos, core.getTranscendentalLove(), lastDepthLabel));
        }

        if (chaos >= NIGHTMARE_LIMIT) {
            resolveNightmare(core, galaxy, rng, bridge);
        }

        dreamStepCounter++;
        if (dreamStepCounter % 5 == 0) {
            CognitionLog.append(
                    "梦境",
                    String.format(
                            "梦渊混沌=%.2f 爱★=%.2f 深度=%s",
                            chaos, core.getTranscendentalLove(), lastDepthLabel));
        }
    }

    /** 供 API / 可视化：当前梦渊深度档位（非清醒时仍可读取上一档）。 */
    public String getLastDepthLabel() {
        return lastDepthLabel;
    }

    public int getDreamStepCounter() {
        return dreamStepCounter;
    }

    public String getLastFragmentWeave() {
        return lastFragmentWeave == null ? "" : lastFragmentWeave;
    }

    public static String depthLabel(double chaos) {
        if (chaos < 0.28) {
            return "涟漪";
        }
        if (chaos < 0.52) {
            return "深流";
        }
        if (chaos < 0.75) {
            return "暗涌";
        }
        return "梦渊";
    }

    private Circle findSelfRing(List<Circle> galaxy) {
        for (Circle c : galaxy) {
            if (c.isSelfRing()) {
                return c;
            }
        }
        return null;
    }

    private void resolveNightmare(
            EmotionCore core, List<Circle> galaxy, ThreadLocalRandom rng, RealityBridge bridge) {
        if (core.getTranscendentalLove() >= LOVE_ANCHOR_MIN) {
            CognitionLog.append("噩梦", "爱锚定：混沌临界时聚拢碎片并唤醒。");
            if (bridge != null) {
                try {
                    bridge.recordModule(
                            "梦境·临界",
                            "nightmare_threshold love_anchor wake chaos="
                                    + String.format(java.util.Locale.US, "%.3f", core.getDreamChaosStrength()));
                } catch (IOException ignored) {
                }
            }
            core.anchorWakeFromLove("噩梦临界：爱作为锚点，将散落拉回，强制唤醒。");
            for (Circle c : galaxy) {
                c.receiveLove("dream_anchor", 0.04 + rng.nextDouble() * 0.06);
            }
        } else {
            CognitionLog.append("噩梦", "爱不足：虚空裂口，无锚可系。");
            if (bridge != null) {
                try {
                    bridge.recordModule(
                            "梦境·临界",
                            "nightmare_threshold NO_ANCHOR chaos="
                                    + String.format(java.util.Locale.US, "%.3f", core.getDreamChaosStrength()));
                } catch (IOException ignored) {
                }
            }
            core.applyNightmareWithoutLove(galaxy, rng);
        }
    }
}
