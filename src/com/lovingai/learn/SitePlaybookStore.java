package com.lovingai.learn;

import com.lovingai.LivingAI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 以自然语言保存「如何使用某站点 / 如何阅读某小说站」的说明，按主机名分文件存于 {@code data/web-playbooks/}。
 */
public final class SitePlaybookStore {

    public static final SitePlaybookStore INSTANCE = new SitePlaybookStore();

    private static final Pattern SAFE = Pattern.compile("[^a-z0-9._-]+");

    private SitePlaybookStore() {}

    public Path dir() {
        return Paths.get(LivingAI.BASE_DIR, "web-playbooks");
    }

    public Path fileForHost(String host) {
        String h = WebFetchAllowlist.normalizeHost(host);
        if (h.isEmpty()) {
            h = "unknown";
        }
        String safe = SAFE.matcher(h).replaceAll("_");
        if (safe.length() > 120) {
            safe = safe.substring(0, 120);
        }
        return dir().resolve(safe + ".txt");
    }

    public void save(String host, String instruction) throws IOException {
        if (instruction == null || instruction.isBlank()) {
            throw new IOException("说明为空");
        }
        Path p = fileForHost(host);
        Files.createDirectories(p.getParent());
        String header =
                "【主机】"
                        + WebFetchAllowlist.normalizeHost(host)
                        + "\n【更新时间】"
                        + Instant.now()
                        + "\n【自然语言说明】\n";
        Files.writeString(p, header + instruction.trim() + "\n", StandardCharsets.UTF_8);
    }

    public Optional<String> load(String host) throws IOException {
        Path p = fileForHost(host);
        if (!Files.isRegularFile(p)) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(p, StandardCharsets.UTF_8));
    }

    public String loadOrEmpty(String host) {
        try {
            return load(host).orElse("");
        } catch (IOException e) {
            return "";
        }
    }

    /** 已保存的 playbook 文件名列表（含 .txt），按字典序。 */
    public List<String> listPlaybookFileBasenames() throws IOException {
        Path d = dir();
        if (!Files.isDirectory(d)) {
            return List.of();
        }
        try (var stream = Files.list(d)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".txt"))
                    .sorted()
                    .toList();
        }
    }
}
