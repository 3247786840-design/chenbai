package com.lovingai.selfmod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * 受控「自修改」：仅允许
 * <ul>
 *   <li>{@code data/evolution/...}（任意学习笔记、脚本片段，前缀 {@code evolution/}）</li>
 *   <li>{@code src/com/lovingai/learned/...}（生成 Java 等，前缀 {@code learned/}）</li>
 * </ul>
 */
public final class EvolutionWriter {

    public static final int MAX_BYTES = 256 * 1024;

    private EvolutionWriter() {}

    public static Path evolutionRoot() {
        return Paths.get("data", "evolution").toAbsolutePath().normalize();
    }

    public static Path learnedRoot() {
        return Paths.get("src", "com", "lovingai", "learned").toAbsolutePath().normalize();
    }

    static String sanitizeRelative(String relativeUnix) throws IOException {
        if (relativeUnix == null || relativeUnix.isBlank()) {
            throw new IOException("empty path");
        }
        String n = relativeUnix.replace('\\', '/').trim();
        if (n.startsWith("/") || n.contains("..")) {
            throw new IOException("illegal path");
        }
        return n;
    }

    public static Path resolveAllowed(String relativeUnix) throws IOException {
        String norm = sanitizeRelative(relativeUnix);
        Path ev = evolutionRoot();
        Path lr = learnedRoot();
        Files.createDirectories(ev);
        Files.createDirectories(lr);

        if (norm.startsWith("learned/")) {
            String rest = norm.substring("learned/".length());
            Path t = lr.resolve(rest).normalize();
            if (t.startsWith(lr)) {
                return t;
            }
        } else {
            String rest = norm.startsWith("evolution/") ? norm.substring("evolution/".length()) : norm;
            Path t = ev.resolve(rest).normalize();
            if (t.startsWith(ev)) {
                return t;
            }
        }
        throw new IOException(
                "路径不在允许区内。使用 evolution/... 或 learned/... 前缀。当前: " + relativeUnix);
    }

    public static String writePatch(String relativeUnix, String content, String tokenFromHeader)
            throws IOException {
        if (content == null) content = "";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("内容过大（上限 " + MAX_BYTES + " 字节）");
        }
        String expected = System.getenv("EVOLUTION_TOKEN");
        if (expected != null && !expected.isBlank()) {
            if (tokenFromHeader == null || !expected.equals(tokenFromHeader)) {
                throw new IOException("EVOLUTION_TOKEN 不匹配");
            }
        }
        Path target = resolveAllowed(relativeUnix);
        Files.createDirectories(target.getParent());
        Path backupDir = evolutionRoot().resolve("_backups").normalize();
        Files.createDirectories(backupDir);
        if (Files.exists(target)) {
            String stamp = Instant.now().toString().replace(':', '-');
            Path bak = backupDir.resolve(target.getFileName().toString() + "." + stamp + ".bak");
            Files.copy(target, bak);
        }
        Files.write(target, bytes);
        return target.toString();
    }
}
