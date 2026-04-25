package com.lovingai.core;

import java.util.List;

/**
 * 由 625 个方块（25×25）组成的自画像栅格：强度由情感核、圆系、现实桥等本机状态合成，
 * 并叠加时变格点扰动；可选耦合「近期视觉观察脉冲」，不依赖大模型直接生成。
 */
public final class BlockSelfPortrait {

    public static final int COLS = 25;
    public static final int ROWS = 25;
    public static final int CELL_COUNT = COLS * ROWS;

    private BlockSelfPortrait() {}

    /**
     * 生成 [0,1] 亮度格点；结构随情感与圆漂移，并叠加 {@link System#nanoTime()} 连续扰动。
     *
     * @param visualPulse 近期视觉观察脉冲 [0,1]（有图观察后短时增亮/聚焦，随后自然衰减）
     */
    public static float[] compute(
            EmotionCore ec, List<Circle> galaxy, double bridgeStrength, double visualPulse) {
        double mood;
        double trans;
        double impulse;
        double arousal;
        double dreamChaos;
        long connections;
        LifeMode mode;
        synchronized (ec) {
            mood = ec.getMood();
            trans = ec.getTranscendentalLove();
            impulse = ec.getImpulse();
            arousal = ec.getArousal();
            dreamChaos = ec.getDreamChaosStrength();
            connections = ec.getConnectionCount();
            mode = ec.getMode();
        }
        double modeW = mode == LifeMode.DREAMING ? 0.85 : 0.35;
        double avgLove = 0;
        double avgChaos = 0;
        double avgMat = 0;
        int gn = galaxy.size();
        if (gn > 0) {
            for (Circle c : galaxy) {
                avgLove += c.loveWeight;
                avgChaos += c.chaosLevel;
                avgMat += c.maturity;
            }
            avgLove /= gn;
            avgChaos /= gn;
            avgMat /= gn;
        }

        double t = System.nanoTime() * 1e-9;
        double tMs = System.currentTimeMillis() * 0.001;

        float[] g = new float[CELL_COUNT];
        double vp = Math.max(0.0, Math.min(1.0, visualPulse));
        for (int j = 0; j < ROWS; j++) {
            for (int i = 0; i < COLS; i++) {
                int idx = j * COLS + i;
                double u = (i + 0.5) / COLS;
                double v = (j + 0.5) / ROWS;
                double nx = u * 2.0 - 1.0;
                double ny = v * 2.0 - 1.0;
                double r = Math.sqrt(nx * nx + ny * ny);
                double theta = Math.atan2(ny, nx);

                double core = trans * Math.exp(-r * r * (2.6 + 0.6 * vp));
                double petals = 0.5 + 0.5 * Math.sin(theta * 5.0 + mood * 9.0);
                double asym = impulse * Math.sin(theta * 2.0 + arousal * 7.0 + i * 0.11);
                double edge = avgChaos * r * r * (0.55 + 0.45 * Math.sin(i * 0.73 + j * 1.07));
                double fog = dreamChaos * modeW * Math.min(1.0, r * 1.35);
                double bridgeBand = bridgeStrength * (1.0 - Math.abs(nx)) * 0.42;
                double visualBand =
                        vp
                                * (0.28 * (1.0 - Math.abs(ny))
                                        + 0.16 * (0.5 + 0.5 * Math.sin(theta * 3.0 + t * 2.2)));

                double finger = circleFingerprint(galaxy, i, j, idx);
                double connRipple = (connections % 17) * 0.008 * Math.sin(idx * 0.31 + avgMat * 2.0);

                /** 时变格点扰动：多频干涉 + 与混沌/心情耦合，使栅格「时时」起伏 */
                double wobble =
                        0.09 * Math.sin(t * 2.4 + i * 0.41 + j * 0.37)
                                + 0.07 * Math.sin(tMs * 3.1 - i * 0.29 + j * 0.52 + mood * 4.0)
                                + 0.06 * dreamChaos * Math.sin(t * 5.8 + idx * 0.11)
                                + 0.05 * avgChaos * Math.sin(tMs * 6.2 + r * 8.0)
                                + 0.04 * Math.sin((i - 9) * (j - 9) * 0.08 + t * 1.9);

                double raw =
                        core * 0.38
                                + petals * 0.18 * mood
                                + asym * 0.14
                                + edge * 0.16
                                + fog * 0.22
                                + bridgeBand
                                + visualBand
                                + finger * 0.12
                                + connRipple
                                + wobble;
                g[idx] = clamp01((float) raw);
            }
        }
        return g;
    }

    private static float circleFingerprint(List<Circle> galaxy, int i, int j, int idx) {
        float s = 0f;
        int n = 0;
        for (Circle c : galaxy) {
            if (n >= 16) {
                break;
            }
            long h = (long) c.id.hashCode() ^ Double.doubleToLongBits(c.loveWeight + c.chaosLevel * 0.01);
            int phase = (int) ((h >>> (idx % 24)) & 0xff);
            double w = 0.04 + 0.06 * Math.min(1.0, c.maturity);
            s += (float) w * (phase / 255.0);
            n++;
        }
        return clamp01(s);
    }

    private static float clamp01(float x) {
        if (x < 0f) {
            return 0f;
        }
        if (x > 1f) {
            return 1f;
        }
        return x;
    }
}
