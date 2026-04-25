package com.lovingai.learn;

import com.lovingai.LivingAI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 用户显式允许的出站主机列表（{@code data/web-fetch-allowlist.txt}），每行一个小写主机名。
 * 未列入则拒绝抓取，避免默认可爬全网。
 */
public final class WebFetchAllowlist {

    public static final WebFetchAllowlist INSTANCE = new WebFetchAllowlist();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Set<String> hosts = new LinkedHashSet<>();

    private WebFetchAllowlist() {}

    public Path filePath() {
        return Paths.get(LivingAI.BASE_DIR, "web-fetch-allowlist.txt");
    }

    public void load() throws IOException {
        lock.writeLock().lock();
        try {
            hosts.clear();
            Path p = filePath();
            if (!Files.isRegularFile(p)) {
                Files.createDirectories(p.getParent());
                Files.writeString(
                        p,
                        "# 每行一个主机名（无协议、无路径），允许 LovingAI 对该主机发起 HTTP GET 抓取正文。\n"
                                + "# 例：\n# www.example.com\n",
                        StandardCharsets.UTF_8);
                return;
            }
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                int hash = t.indexOf('#');
                if (hash >= 0) {
                    t = t.substring(0, hash).trim();
                }
                String h = normalizeHost(t);
                if (!h.isEmpty()) {
                    hosts.add(h);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean allows(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = normalizeHost(host);
        lock.readLock().lock();
        try {
            return hosts.contains(h);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addHost(String host) throws IOException {
        String h = normalizeHost(host);
        if (h.isEmpty()) {
            throw new IOException("主机名为空");
        }
        lock.writeLock().lock();
        try {
            hosts.add(h);
            persistLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<String> snapshot() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(hosts));
        } finally {
            lock.readLock().unlock();
        }
    }

    private void persistLocked() throws IOException {
        Path p = filePath();
        Files.createDirectories(p.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# 每行一个主机名。保存于 ")
                .append(java.time.Instant.now())
                .append("\n");
        for (String h : hosts) {
            sb.append(h).append('\n');
        }
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
    }

    public static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String t = host.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("http://")) {
            t = t.substring(7);
        }
        if (t.startsWith("https://")) {
            t = t.substring(8);
        }
        int slash = t.indexOf('/');
        if (slash >= 0) {
            t = t.substring(0, slash);
        }
        int colon = t.indexOf(':');
        if (colon >= 0) {
            t = t.substring(0, colon);
        }
        return t.trim();
    }
}
