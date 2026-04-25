package com.lovingai.learn;

import com.lovingai.LivingAI;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 用户导入的文档 / 压缩包索引：仅存本机 data 目录，供检索与对话参考。
 * 长篇小说等可在 {@code distill/} 下保存蒸馏文本，供对话优先引用。
 */
public final class ImportedCorpus {

    public static final ImportedCorpus INSTANCE = new ImportedCorpus();

    private static final int MAX_TEXT_CHARS = 400_000;
    private static final int MAX_ZIP_ENTRIES = 400;
    private static final long MAX_ZIP_UNCOMPRESSED = 80L * 1024 * 1024;
    public static final int MAX_UPLOAD_BYTES = 52_000_000;
    /** 导入语料总配额（默认 100GB，可通过 -Dlovingai.imported.quotaGb 覆盖）。 */
    private static final long DEFAULT_STORAGE_QUOTA_BYTES = 100L * 1024L * 1024L * 1024L;
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]{2,}|[\u4e00-\u9fff]{2,}");

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, DocRecord> byId = new LinkedHashMap<>();

    private ImportedCorpus() {}

    public Path rootDir() {
        return Paths.get(LivingAI.BASE_DIR, "imported");
    }

    public void loadFromDisk() throws IOException {
        lock.writeLock().lock();
        try {
            byId.clear();
            Path textDir = rootDir().resolve("text");
            Path idx = rootDir().resolve("index.tsv");
            Files.createDirectories(textDir);
            if (!Files.isRegularFile(idx)) {
                return;
            }
            for (String line : Files.readAllLines(idx, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split("\t", -1);
                if (p.length < 5) continue;
                String id = p[0].trim();
                Path tp = textDir.resolve(id + ".txt");
                if (!Files.isRegularFile(tp)) continue;
                String body = Files.readString(tp, StandardCharsets.UTF_8);
                byId.put(
                        id,
                        new DocRecord(
                                id,
                                p[1],
                                p[2],
                                Long.parseLong(p[3]),
                                Instant.parse(p[4]),
                                truncate(body, MAX_TEXT_CHARS)));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return byId.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long storageQuotaBytes() {
        return resolveStorageQuotaBytes();
    }

    public long storageUsageBytes() throws IOException {
        lock.readLock().lock();
        try {
            return directorySizeBytes(rootDir());
        } finally {
            lock.readLock().unlock();
        }
    }

    public double storageUsageRatio() throws IOException {
        long quota = storageQuotaBytes();
        if (quota <= 0) return 0.0;
        long used = storageUsageBytes();
        return Math.max(0.0, Math.min(1.0, (double) used / (double) quota));
    }

    public List<DocRecord> list() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(byId.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<DocRecord> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        lock.readLock().lock();
        try {
            return Optional.ofNullable(byId.get(id.trim()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 按与 {@link #searchSnippets} 相同的打分对文档排序；无命中时退回最近导入的若干条。 */
    public List<DocRecord> rankDocsForQuery(String query, int max) {
        if (max <= 0) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            if (query == null || query.isBlank()) {
                return recentDocsFallback(max);
            }
            List<String> qTokens = tokenizeQuery(query);
            record Hit(DocRecord d, int score) {}
            List<Hit> hits = new ArrayList<>();
            for (DocRecord d : byId.values()) {
                int sc = score(d.text(), qTokens, query);
                if (sc > 0) {
                    hits.add(new Hit(d, sc));
                }
            }
            hits.sort(Comparator.comparingInt((Hit h) -> h.score).reversed());
            List<DocRecord> out = new ArrayList<>();
            for (Hit h : hits) {
                if (out.size() >= max) {
                    break;
                }
                out.add(h.d);
            }
            if (out.isEmpty()) {
                return recentDocsFallback(max);
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<DocRecord> recentDocsFallback(int max) {
        List<DocRecord> all = new ArrayList<>(byId.values());
        if (all.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, all.size() - max);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    /**
     * 从库内**随机**文档截取若干段，作「外向提问」锚点（与当前用户问句可弱相关或无关，由上层组问句）。
     *
     * @param count 段数上限（实际可能略少若正文过短）
     */
    public List<String> randomOutwardSnippets(
            ThreadLocalRandom rng, int count, int maxCharsEach) {
        if (rng == null || count <= 0 || maxCharsEach <= 8) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            if (byId.isEmpty()) {
                return List.of();
            }
            List<DocRecord> all = new ArrayList<>(byId.values());
            List<String> out = new ArrayList<>(Math.min(count, all.size()));
            for (int n = 0; n < count; n++) {
                DocRecord d = all.get(rng.nextInt(all.size()));
                String t = d.text();
                if (t == null || t.isBlank()) {
                    continue;
                }
                String norm = t.replace('\r', '\n');
                int take = Math.min(maxCharsEach, norm.length());
                if (take < 4) {
                    continue;
                }
                int start =
                        norm.length() <= take
                                ? 0
                                : rng.nextInt(norm.length() - take + 1);
                String slice = norm.substring(start, start + take).trim();
                int badNl = slice.indexOf('\n');
                if (badNl > 0 && badNl < slice.length() / 3) {
                    slice = slice.substring(badNl + 1).trim();
                }
                if (slice.length() > maxCharsEach) {
                    slice = slice.substring(0, maxCharsEach) + "…";
                }
                if (slice.isBlank()) {
                    continue;
                }
                out.add("〔随机锚·" + d.fileName() + "〕 " + slice);
                if (out.size() >= count) {
                    break;
                }
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    private Path distillPath(String id) {
        return rootDir().resolve("distill").resolve(id + ".txt");
    }

    /** 若该条目已有蒸馏稿则返回。 */
    public Optional<String> loadDistillation(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Path p = distillPath(id.trim());
        lock.readLock().lock();
        try {
            if (!Files.isRegularFile(p)) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 写入蒸馏稿（覆盖）。 */
    public void saveDistillation(String id, String text) throws IOException {
        if (id == null || id.isBlank()) {
            throw new IOException("id 为空");
        }
        String tid = id.trim();
        lock.readLock().lock();
        try {
            if (!byId.containsKey(tid)) {
                throw new IOException("未知文档 id");
            }
        } finally {
            lock.readLock().unlock();
        }
        byte[] dist = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        ensureQuotaForIncomingBytes(dist.length);
        Path p = distillPath(tid);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text == null ? "" : text, StandardCharsets.UTF_8);
    }

    /**
     * 对当前查询最相关的若干文档，若有蒸馏稿则各取一段，供对话与提示词前置（先于原文摘录）。
     */
    public List<String> distillHintsForQuery(String query, int maxDocs, int maxCharsPerHint) {
        if (maxDocs <= 0 || maxCharsPerHint <= 0) {
            return List.of();
        }
        List<DocRecord> ranked = rankDocsForQuery(query, Math.max(maxDocs * 3, 6));
        List<String> out = new ArrayList<>();
        for (DocRecord d : ranked) {
            if (out.size() >= maxDocs) {
                break;
            }
            Optional<String> dist = loadDistillation(d.id());
            if (dist.isEmpty()) {
                continue;
            }
            String piece = dist.get().replace('\r', '\n').trim();
            if (piece.length() > maxCharsPerHint) {
                piece = piece.substring(0, maxCharsPerHint) + "…";
            }
            out.add("【蒸馏·" + d.fileName() + "】 " + piece);
        }
        return out;
    }

    /** @return 导入条目的 id，失败抛异常 */
    public String ingestFile(String originalName, byte[] raw) throws IOException {
        if (raw.length > MAX_UPLOAD_BYTES) {
            throw new IOException("文件过大（上限约 50MB）");
        }
        String safeName = safeLeafName(originalName);
        Optional<String> text = DocumentTextExtractor.extract(safeName, raw);
        if (text.isEmpty()) {
            throw new IOException("无法从该文件抽取可读文本（可尝试 .txt/.md/.json/.html 等）");
        }
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String body = truncate(text.get(), MAX_TEXT_CHARS);
        ensureQuotaForIncomingBytes(body.getBytes(StandardCharsets.UTF_8).length + 256L);
        DocRecord rec =
                new DocRecord(id, safeName, guessKind(safeName), body.length(), Instant.now(), body);
        lock.writeLock().lock();
        try {
            byId.put(id, rec);
            persistLocked();
        } finally {
            lock.writeLock().unlock();
        }
        return id;
    }

    public int ingestZip(byte[] raw) throws IOException {
        if (raw.length > MAX_UPLOAD_BYTES) {
            throw new IOException("压缩包过大（上限约 50MB）");
        }
        int added = 0;
        long uncompressed = 0;
        long incomingBytesEstimate = 0L;
        List<DocRecord> batch = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(raw)))) {
            ZipEntry e;
            int n = 0;
            while ((e = zis.getNextEntry()) != null) {
                n++;
                if (n > MAX_ZIP_ENTRIES) {
                    throw new IOException("压缩包内文件过多（上限 " + MAX_ZIP_ENTRIES + "）");
                }
                if (e.isDirectory()) continue;
                String name = safeZipEntryName(e.getName());
                if (name == null) continue;
                byte[] fileBytes = readLimited(zis, MAX_UPLOAD_BYTES);
                uncompressed += fileBytes.length;
                if (uncompressed > MAX_ZIP_UNCOMPRESSED) {
                    throw new IOException("解压后总体积过大");
                }
                Optional<String> text = DocumentTextExtractor.extract(name, fileBytes);
                if (text.isEmpty()) continue;
                String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                String body = truncate(text.get(), MAX_TEXT_CHARS);
                batch.add(new DocRecord(id, name, guessKind(name), body.length(), Instant.now(), body));
                incomingBytesEstimate += body.getBytes(StandardCharsets.UTF_8).length + 256L;
                added++;
            }
        }
        if (batch.isEmpty()) {
            throw new IOException("压缩包内没有可抽取文本的文件");
        }
        ensureQuotaForIncomingBytes(incomingBytesEstimate);
        lock.writeLock().lock();
        try {
            for (DocRecord rec : batch) {
                byId.put(rec.id, rec);
            }
            persistLocked();
        } finally {
            lock.writeLock().unlock();
        }
        return added;
    }

    public void clearAll() throws IOException {
        lock.writeLock().lock();
        try {
            byId.clear();
            Path dir = rootDir();
            if (Files.isDirectory(dir)) {
                try (var s = Files.walk(dir)) {
                    s.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
            Files.createDirectories(rootDir().resolve("text"));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 存储压力下的再蒸馏：把部分原文压缩为短版，并同步写入 distill，
     * 以减少 data/imported 占用。返回释放字节数（best-effort）。
     */
    public long compactByDistillation(double targetUsageRatio, int maxDocs) throws IOException {
        if (maxDocs <= 0) return 0L;
        double target = Math.max(0.05, Math.min(0.95, targetUsageRatio));
        lock.writeLock().lock();
        try {
            Path dir = rootDir();
            long quota = resolveStorageQuotaBytes();
            long before = directorySizeBytes(dir);
            if (quota <= 0 || before <= 0) return 0L;
            if ((double) before / (double) quota <= target) return 0L;
            List<DocRecord> docs = new ArrayList<>(byId.values());
            docs.sort(Comparator.comparingLong((DocRecord d) -> d.chars()).reversed());
            int handled = 0;
            for (DocRecord d : docs) {
                if (handled >= maxDocs) break;
                if (d.text() == null || d.text().length() < 1400) continue;
                String distilled = distillForStorage(d.text());
                if (distilled.length() >= d.text().length()) continue;
                DocRecord nd =
                        new DocRecord(
                                d.id(),
                                d.fileName(),
                                d.kind(),
                                distilled.length(),
                                d.added(),
                                distilled);
                byId.put(d.id(), nd);
                Path dist = distillPath(d.id());
                Files.createDirectories(dist.getParent());
                Files.writeString(dist, distilled, StandardCharsets.UTF_8);
                handled++;
                long usedNow = directorySizeBytes(dir);
                if ((double) usedNow / (double) quota <= target) {
                    break;
                }
            }
            persistLocked();
            long after = directorySizeBytes(dir);
            return Math.max(0L, before - after);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 按查询词做简单重叠打分，返回若干段摘录。 */
    public List<String> searchSnippets(String query, int maxSnippets, int snippetLen) {
        if (query == null || query.isBlank() || maxSnippets <= 0) {
            return List.of();
        }
        List<String> qTokens = tokenizeQuery(query);
        lock.readLock().lock();
        try {
            record Hit(String id, String fileName, int score, String text) {}
            List<Hit> hits = new ArrayList<>();
            for (DocRecord d : byId.values()) {
                int sc = score(d.text(), qTokens, query);
                if (sc > 0) {
                    hits.add(new Hit(d.id(), d.fileName(), sc, d.text()));
                }
            }
            hits.sort(Comparator.comparingInt((Hit h) -> h.score).reversed());
            List<String> out = new ArrayList<>();
            for (Hit h : hits) {
                if (out.size() >= maxSnippets) break;
                String ex = extractSnippet(h.text, query, snippetLen);
                out.add("「" + h.fileName + "」 " + ex);
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    private static int score(String doc, List<String> tokens, String rawQuery) {
        String d = doc.toLowerCase(Locale.ROOT);
        int s = 0;
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            int c = 0;
            int from = 0;
            while (from < d.length()) {
                int i = d.indexOf(t, from);
                if (i < 0) break;
                c++;
                from = i + t.length();
                if (c >= 8) break;
            }
            s += c * 3;
        }
        String rq = rawQuery.trim().toLowerCase(Locale.ROOT);
        if (rq.length() >= 2 && d.contains(rq)) {
            s += 12;
        }
        return s;
    }

    private static List<String> tokenizeQuery(String query) {
        List<String> out = new ArrayList<>();
        var m = TOKEN.matcher(query.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group());
        }
        String q = query.trim();
        if (q.length() >= 2 && q.length() <= 24 && out.isEmpty()) {
            for (int i = 0; i + 2 <= q.length(); i++) {
                out.add(q.substring(i, i + 2));
            }
        }
        return out;
    }

    private static String extractSnippet(String doc, String query, int maxLen) {
        String d = doc.replace('\n', ' ');
        if (d.length() <= maxLen) return d;
        String low = d.toLowerCase(Locale.ROOT);
        String q = query.trim().toLowerCase(Locale.ROOT);
        int idx = q.length() >= 2 ? low.indexOf(q) : -1;
        if (idx < 0 && !TOKEN.matcher(q).find()) {
            idx = low.indexOf(q.substring(0, Math.min(2, q.length())));
        }
        if (idx < 0) idx = 0;
        int start = Math.max(0, idx - maxLen / 3);
        int end = Math.min(d.length(), start + maxLen);
        String s = d.substring(start, end);
        if (start > 0) s = "…" + s;
        if (end < d.length()) s = s + "…";
        return s;
    }

    private void persistLocked() throws IOException {
        Path dir = rootDir();
        Path textDir = dir.resolve("text");
        Files.createDirectories(textDir);
        Path idx = dir.resolve("index.tsv");
        StringBuilder sb = new StringBuilder();
        sb.append("# id\tfileName\tkind\tchars\taddedIso\n");
        for (DocRecord d : byId.values()) {
            Files.writeString(textDir.resolve(d.id() + ".txt"), d.text(), StandardCharsets.UTF_8);
            sb.append(d.id())
                    .append('\t')
                    .append(sanitizeTsvField(d.fileName()))
                    .append('\t')
                    .append(d.kind())
                    .append('\t')
                    .append(d.chars())
                    .append('\t')
                    .append(d.added())
                    .append('\n');
        }
        Files.writeString(idx, sb.toString(), StandardCharsets.UTF_8);
    }

    private void ensureQuotaForIncomingBytes(long incomingBytes) throws IOException {
        long quota = resolveStorageQuotaBytes();
        long used = directorySizeBytes(rootDir());
        long next = used + Math.max(0L, incomingBytes);
        if (next > quota) {
            throw new IOException(
                    "导入语料存储已达配额上限（quota="
                            + humanBytes(quota)
                            + ", used="
                            + humanBytes(used)
                            + ", incoming≈"
                            + humanBytes(incomingBytes)
                            + "）");
        }
    }

    private static long resolveStorageQuotaBytes() {
        String gb = System.getProperty("lovingai.imported.quotaGb");
        if (gb == null || gb.isBlank()) {
            return DEFAULT_STORAGE_QUOTA_BYTES;
        }
        try {
            long v = Long.parseLong(gb.trim());
            v = Math.max(1L, Math.min(10_000L, v));
            return v * 1024L * 1024L * 1024L;
        } catch (NumberFormatException ignored) {
            return DEFAULT_STORAGE_QUOTA_BYTES;
        }
    }

    private static long directorySizeBytes(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return 0L;
        try (var walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(
                            p -> {
                                try {
                                    return Files.size(p);
                                } catch (IOException ignored) {
                                    return 0L;
                                }
                            })
                    .sum();
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes <= 0L) return "0B";
        String[] units = new String[] {"B", "KB", "MB", "GB", "TB"};
        double v = (double) bytes;
        int idx = 0;
        while (v >= 1024.0 && idx < units.length - 1) {
            v /= 1024.0;
            idx++;
        }
        return String.format(Locale.US, "%.2f%s", v, units[idx]);
    }

    private static String sanitizeTsvField(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String guessKind(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "txt";
    }

    private static String safeLeafName(String name) {
        if (name == null || name.isBlank()) return "import.txt";
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        n = n.trim().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        if (n.isEmpty()) return "import.txt";
        return n.length() > 160 ? n.substring(0, 160) : n;
    }

    /** 防 Zip Slip：仅允许普通相对路径。 */
    private static String safeZipEntryName(String raw) {
        if (raw == null) return null;
        String p = raw.replace('\\', '/').trim();
        if (p.isEmpty() || p.startsWith("/") || p.startsWith("..") || p.contains("/../") || p.contains("//")) {
            return null;
        }
        int slash = p.lastIndexOf('/');
        String leaf = slash >= 0 ? p.substring(slash + 1) : p;
        return leaf.isEmpty() ? null : p;
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > max) {
                throw new IOException("单文件解压过大");
            }
            bo.write(buf, 0, n);
        }
        return bo.toByteArray();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n…(截断)";
    }

    private static String distillForStorage(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.replace('\r', '\n').trim();
        if (t.length() <= 1600) return t;
        int n = t.length();
        int headLen = Math.min(760, n);
        int midLen = Math.min(420, Math.max(0, n - headLen));
        int tailLen = Math.min(520, Math.max(0, n - headLen - midLen));
        int midStart = Math.max(0, (n - midLen) / 2);
        int tailStart = Math.max(midStart + midLen, n - tailLen);
        String head = t.substring(0, headLen).trim();
        String mid = t.substring(midStart, Math.min(n, midStart + midLen)).trim();
        String tail = t.substring(tailStart, n).trim();
        return ("【存储蒸馏摘要】\n"
                        + head
                        + "\n\n【中段锚】\n"
                        + mid
                        + "\n\n【尾段锚】\n"
                        + tail
                        + "\n\n（原文已因存储压力进行压缩保留，完整细节请重新导入源材料）")
                .trim();
    }

    public record DocRecord(
            String id, String fileName, String kind, long chars, Instant added, String text) {}
}
