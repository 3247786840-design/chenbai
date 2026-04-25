package com.lovingai.core.columnkey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * v0 柱向量与 {@link PillarKnowledgeKeys#VECTOR_B64} 互转（float32 LE）。
 *
 * 见 ADR 0001。
 */
public final class PillarVectorCodec {
    private PillarVectorCodec() {}

    public static String encodeFloat32Le(float[] vec) {
        if (vec == null || vec.length == 0) return "";
        ByteBuffer bb = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vec) {
            bb.putFloat(v);
        }
        return Base64.getEncoder().encodeToString(bb.array());
    }

    public static float[] decodeFloat32Le(String b64) {
        if (b64 == null || b64.isBlank()) return new float[0];
        byte[] raw = Base64.getDecoder().decode(b64.trim());
        if (raw.length < 4 || (raw.length & 3) != 0) return new float[0];
        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[raw.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = bb.getFloat();
        }
        return out;
    }
}
