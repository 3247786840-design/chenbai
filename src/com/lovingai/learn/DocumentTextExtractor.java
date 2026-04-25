package com.lovingai.learn;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** 从常见文本类文件中抽取 UTF-8 文本（体积上限由调用方控制）。 */
public final class DocumentTextExtractor {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final int MAX_TRY_BYTES = 12_000_000;

    private DocumentTextExtractor() {}

    public static Optional<String> extract(String originalFileName, byte[] raw) {
        if (raw == null || raw.length == 0) {
            return Optional.empty();
        }
        int cap = Math.min(raw.length, MAX_TRY_BYTES);
        String lowerName = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".zip") || lowerName.endsWith(".jar") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".gif") || lowerName.endsWith(".exe") || lowerName.endsWith(".dll") || lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) {
            return Optional.empty();
        }
        Charset cs = sniffCharset(raw, cap);
        String s = new String(raw, 0, cap, cs);
        if (lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerName.endsWith(".xml")) {
            s = HTML_TAG.matcher(s).replaceAll(" ");
            s = collapseWs(s);
        }
        s = collapseWs(s);
        if (s.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(s);
    }

    private static String collapseWs(String s) {
        return s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    /** 简单 BOM / 缺省 UTF-8。 */
    private static Charset sniffCharset(byte[] raw, int len) {
        if (len >= 3 && raw[0] == (byte) 0xef && raw[1] == (byte) 0xbb && raw[2] == (byte) 0xbf) {
            return StandardCharsets.UTF_8;
        }
        if (len >= 2 && raw[0] == (byte) 0xff && raw[1] == (byte) 0xfe) {
            return java.nio.charset.Charset.forName("UTF-16LE");
        }
        if (len >= 2 && raw[0] == (byte) 0xfe && raw[1] == (byte) 0xff) {
            return java.nio.charset.Charset.forName("UTF-16BE");
        }
        return StandardCharsets.UTF_8;
    }
}
