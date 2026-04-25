package com.lovingai.core.columnkey;

import com.lovingai.core.EmotionCore;

/**
 * D6：以 {@link EmotionCore} 为权威情感源推导门控参数（保守、可解释）。
 *
 * 见 ADR 0001。
 */
public final class EmotionCoreColumnGate {
    private static final double DEFAULT_HISTORY = 0.15;
    private static final int BASE_PARALLEL = 6;
    private static final int NARROW_PARALLEL = 3;
    private static final double BASE_FLOOR = 0.12;
    private static final double SOFT_FLOOR = 0.10;

    private EmotionCoreColumnGate() {}

    public static ColumnGateParams from(EmotionCore core) {
        if (core == null) {
            return new ColumnGateParams(1.0, BASE_PARALLEL, BASE_FLOOR, DEFAULT_HISTORY);
        }
        double impulse = core.getImpulse();
        double arousal = core.getArousal();
        double love = core.getTranscendentalLove();
        double chaos = core.getDreamChaosStrength();

        int parallel = BASE_PARALLEL;
        if (impulse > 0.55 || arousal > 0.55) {
            parallel = NARROW_PARALLEL;
        }

        double floor = BASE_FLOOR;
        if (love > 0.35) {
            floor = SOFT_FLOOR;
        }

        double jitter = 1.0;
        if (chaos > 0.45) {
            jitter = 0.92;
        }

        return new ColumnGateParams(jitter, parallel, floor, DEFAULT_HISTORY);
    }
}
