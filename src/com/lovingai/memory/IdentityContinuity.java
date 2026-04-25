package com.lovingai.memory;

import com.lovingai.LivingAI;
import com.lovingai.log.CognitionLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 跨会话、跨版本可核对的长期叙事：追加写日志 + SHA-256 链（从固定创世根链接到当前 head）。
 */
public final class IdentityContinuity {

    private static final Path CHAIN_FILE =
            Paths.get(LivingAI.BASE_DIR, "memory", "identity-chain.log");
    private static final Path PROTOCOL_README =
            Paths.get(LivingAI.BASE_DIR, "protocol", "PEER_PROTOCOL.txt");

    private static final String GENESIS_ROOT = sha256Hex("LOVINGAI_IDENTITY_GENESIS_v1");

    private static final Object LOCK = new Object();
    private static volatile String prevHashHex = "";
    private static volatile long lineSeq = 0;

    public static final String SESSION_ID =
            UUID.randomUUID().toString().replace("-", "").substring(0, 16);

    private IdentityContinuity() {}

    public static void bootstrap() throws IOException {
        synchronized (LOCK) {
            Files.createDirectories(CHAIN_FILE.getParent());
            Files.createDirectories(PROTOCOL_README.getParent());
            loadTailStateFromDisk();
            if (prevHashHex.isEmpty()) {
                prevHashHex = GENESIS_ROOT;
                lineSeq = 0;
            }
            ensureProtocolReadme();
            appendLocked(
                    "session_boot",
                    "会话="
                            + SESSION_ID
                            + " 身体="
                            + LivingPurpose.BODY.substring(0, Math.min(60, LivingPurpose.BODY.length()))
                            + "…");
        }
    }

    private static void ensureProtocolReadme() throws IOException {
        if (Files.isRegularFile(PROTOCOL_README)) {
            return;
        }
        String text =
                "LovingAI — 与他者交流的可持续方式（人介导）\n"
                        + "=====================================\n"
                        + LivingPurpose.PEER_PROTOCOL
                        + "\n\n"
                        + "可导出材料：\n"
                        + "  - GET /api/continuity/timeline （身份哈希链）\n"
                        + "  - GET /api/continuity/verify （链完整性 true/false）\n"
                        + "  - GET /api/reality/bridge.log （沙盒—现实桥）\n"
                        + "  - GET /api/identity/manifest （意图与端点）\n"
                        + "\n本文件由进程首次启动时生成于 data/protocol/\n";
        Files.writeString(PROTOCOL_README, text, StandardCharsets.UTF_8);
    }

    private static void loadTailStateFromDisk() throws IOException {
        if (!Files.isRegularFile(CHAIN_FILE)) {
            return;
        }
        List<String> lines = Files.readAllLines(CHAIN_FILE, StandardCharsets.UTF_8);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\t", -1);
            if (p.length < 5) {
                continue;
            }
            try {
                lineSeq = Long.parseLong(p[0]);
                prevHashHex = p[4].trim();
            } catch (NumberFormatException ignored) {
            }
            break;
        }
    }

    public static void append(String kind, String detail) {
        try {
            synchronized (LOCK) {
                appendLocked(kind, detail);
            }
        } catch (IOException e) {
            CognitionLog.append("身份连续", "写入失败: " + e.getMessage());
        }
    }

    private static void appendLocked(String kind, String detail) throws IOException {
        String k = kind == null ? "event" : kind.replace('\t', ' ').replace('\n', ' ');
        String d = detail == null ? "" : detail.replace('\t', ' ').replace('\n', ' ');
        if (d.length() > 2000) {
            d = d.substring(0, 2000) + "…";
        }
        lineSeq++;
        String iso = Instant.now().toString();
        String payload = prevHashHex + "|" + lineSeq + "|" + iso + "|" + k + "|" + d;
        String hash = sha256Hex(payload);
        String line =
                lineSeq
                        + "\t"
                        + iso
                        + "\t"
                        + k
                        + "\t"
                        + d
                        + "\t"
                        + hash
                        + "\n";
        Files.write(
                CHAIN_FILE,
                line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        prevHashHex = hash;
        CognitionLog.append("身份连续", k + " seq=" + lineSeq);
    }

    public static String readTimelineText() throws IOException {
        if (!Files.isRegularFile(CHAIN_FILE)) {
            return "# 尚无叙事链\n";
        }
        return Files.readString(CHAIN_FILE, StandardCharsets.UTF_8);
    }

    public static String getHeadHash() {
        return prevHashHex;
    }

    public static long getLineSeq() {
        return lineSeq;
    }

    /** 供漂移观测拼接的人类可读身份锚。 */
    public static String continuitySnapshotHint() {
        String head = prevHashHex == null ? "" : prevHashHex;
        String shortHead = head.length() <= 12 ? head : head.substring(0, 12);
        return "session=" + SESSION_ID + " seq=" + lineSeq + " head=" + shortHead;
    }

    public static boolean verifyChainIntegrity() throws IOException {
        if (!Files.isRegularFile(CHAIN_FILE)) {
            return true;
        }
        List<String> lines = Files.readAllLines(CHAIN_FILE, StandardCharsets.UTF_8);
        String expectPrev = GENESIS_ROOT;
        long expectSeq = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\t", -1);
            if (p.length < 5) {
                return false;
            }
            long seq;
            try {
                seq = Long.parseLong(p[0]);
            } catch (NumberFormatException e) {
                return false;
            }
            String iso = p[1];
            String k = p[2];
            String d = p[3];
            String hash = p[4].trim();
            if (seq != expectSeq + 1) {
                return false;
            }
            String payload = expectPrev + "|" + seq + "|" + iso + "|" + k + "|" + d;
            String calc = sha256Hex(payload);
            if (!calc.equalsIgnoreCase(hash)) {
                return false;
            }
            expectPrev = hash;
            expectSeq = seq;
        }
        return true;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
