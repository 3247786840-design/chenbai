package com.lovingai.core.columnkey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 需求密钥 v0：整体性保留在规范化文本中；持久化/观测用稳定指纹。
 *
 * 见 ADR 0001。
 */
public final class DemandKeyEncoder {
    private DemandKeyEncoder() {}

    public record DemandKeyV0(String normalizedText, String sha256Hex) {}

    public static DemandKeyV0 encode(String rawUserMessage) {
        String norm = normalize(rawUserMessage);
        return new DemandKeyV0(norm, sha256Hex(norm));
    }

    static String normalize(String raw) {
        if (raw == null) return "";
        String t = raw.replace('\uFEFF', ' ').trim();
        t = t.replaceAll("\\s+", " ");
        StringBuilder sb = new StringBuilder(t.length());
        boolean prevSpace = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\r' || c == '\n') {
                c = ' ';
            }
            if (c == ' ') {
                if (!prevSpace) sb.append(' ');
                prevSpace = true;
                continue;
            }
            prevSpace = false;
            if (c <=127 && Character.isLetter(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    static String sha256Hex(String normalizedUtf8) {
        if (normalizedUtf8 == null) normalizedUtf8 = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(normalizedUtf8.getBytes(StandardCharsets.UTF_8));
            StringBuilder h = new StringBuilder(d.length * 2);
            for (byte b : d) {
                h.append(String.format(Locale.ROOT, "%02x", b));
            }
            return h.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
