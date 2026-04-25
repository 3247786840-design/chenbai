package com.lovingai.core.columnkey;

/**
 * {@code Circle.soulMemory} 键前缀：柱向量 v0 与叙事键隔离。
 *
 * 见仓库 {@code docs/adr/0001-round-column-key-protocol.md}（ADR 0001）。
 */
public final class PillarKnowledgeKeys {
    public static final String PREFIX = "pillar.v0.";
    public static final String VECTOR_B64 = PREFIX + "vector.b64";
    public static final String DIM_LABELS = PREFIX + "dimLabels";
    public static final String SEALED_MASK = PREFIX + "sealedMask";
    public static final String SCHEMA_VERSION = PREFIX + "schemaVersion";

    private PillarKnowledgeKeys() {}
}
