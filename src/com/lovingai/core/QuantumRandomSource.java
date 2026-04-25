package com.lovingai.core;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase 2: symbolic "quantum" randomness — unpredictable micro-events for the circles.
 */
public final class QuantumRandomSource {

    public enum EventType {
        FLUCTUATION,
        ENTANGLEMENT,
        COLLAPSE,
        TUNNELING
    }

    public static final class RandomEvent {
        public final EventType eventType;
        public final double intensity;
        public final long timestamp;
        public final boolean hasQuantumEvidence;

        public RandomEvent(EventType eventType, double intensity, long timestamp, boolean hasQuantumEvidence) {
            this.eventType = eventType;
            this.intensity = intensity;
            this.timestamp = timestamp;
            this.hasQuantumEvidence = hasQuantumEvidence;
        }
    }

    public RandomEvent nextEvent() {
        EventType[] vals = EventType.values();
        EventType t = vals[ThreadLocalRandom.current().nextInt(vals.length)];
        double intensity = ThreadLocalRandom.current().nextDouble(0.08, 1.0);
        boolean evidence = ThreadLocalRandom.current().nextDouble() < 0.35;
        return new RandomEvent(t, intensity, System.currentTimeMillis(), evidence);
    }
}
