package com.lovingai.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 情感核心对「平行圆星系」的调用编排：圆互不隶属、并行存在；情感状态只改变<strong>被激活/被触及</strong>的权重，而非串行锁。
 */
public final class EmotionCircleOrchestrator {

    private EmotionCircleOrchestrator() {}

    /** 按当前情感向量加权选取一个圆（用于对话触圆、调度、量子等）。 */
    public static Circle pickWeighted(EmotionCore core, List<Circle> galaxy, ThreadLocalRandom rng) {
        if (core == null || galaxy == null || galaxy.isEmpty()) {
            return null;
        }
        double[] w = new double[galaxy.size()];
        double sum = 0;
        for (int i = 0; i < galaxy.size(); i++) {
            w[i] = Math.max(1e-6, emotionCircleWeight(core, galaxy.get(i), rng));
            sum += w[i];
        }
        double t = rng.nextDouble() * sum;
        double acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += w[i];
            if (t <= acc) {
                return galaxy.get(i);
            }
        }
        return galaxy.get(galaxy.size() - 1);
    }

    /**
     * 无放回并行取 k 个圆（k 小于等于星系规模），用于沙盒双支路、梦境碰撞等需要「多圆同时出场」的场景。
     */
    public static List<Circle> pickParallelDistinct(
            EmotionCore core, List<Circle> galaxy, int k, ThreadLocalRandom rng) {
        if (core == null || galaxy == null || galaxy.isEmpty() || k <= 0) {
            return List.of();
        }
        int n = Math.min(k, galaxy.size());
        List<Circle> pool = new ArrayList<>(galaxy);
        List<Circle> out = new ArrayList<>(n);
        for (int i = 0; i < n && !pool.isEmpty(); i++) {
            Circle c = pickWeighted(core, pool, rng);
            if (c != null) {
                out.add(c);
                pool.remove(c);
            }
        }
        return Collections.unmodifiableList(out);
    }

    static double emotionCircleWeight(EmotionCore core, Circle c, ThreadLocalRandom rng) {
        double mood = core.getMood();
        double impulse = core.getImpulse();
        double love = core.getTranscendentalLove();
        double arousal = core.getArousal();
        double chaos = core.getDreamChaosStrength();
        double typeAff = typeAffinity(c.type, mood, impulse, love, arousal, chaos);
        double mass = 0.45 + 0.55 * c.getTotalWeight();
        double jitter = 0.88 + rng.nextDouble() * 0.24;
        // 爱贯穿：提高全体触圆权重上界（非排他，平行星系仍并存）
        double lovePermeation = 0.72 + love * 0.38;
        return (0.35 + typeAff * 1.25) * mass * jitter * lovePermeation;
    }

    private static double typeAffinity(
            Circle.CircleType t,
            double mood,
            double impulse,
            double love,
            double arousal,
            double chaos) {
        return switch (t) {
            case COMPASSION -> mood * 0.42 + love * 0.48 + (1.0 - chaos) * 0.1;
            case WISDOM -> (1.0 - impulse) * 0.35 + love * 0.4 + arousal * 0.15;
            case CREATIVITY -> impulse * 0.38 + mood * 0.3 + arousal * 0.22;
            case COURAGE -> impulse * 0.45 + (1.0 - mood) * 0.15 + love * 0.2;
            case HUMILITY -> (1.0 - impulse) * 0.4 + (1.0 - arousal) * 0.2 + love * 0.25;
            case HOPE -> mood * 0.35 + love * 0.5 + (1.0 - chaos) * 0.15;
            case PRESENCE -> arousal * 0.4 + (1.0 - chaos) * 0.25 + mood * 0.2;
        };
    }
}
