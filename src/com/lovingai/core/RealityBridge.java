package com.lovingai.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2: 与「外部现实」的弱连接（文件日志），强度由 bridgeStrength 表示（README：约 10%）。
 * 沙盒预演结果经此落盘，形成「模拟→现实桥」的可观察轨迹。
 */
public final class RealityBridge {
    private final Path root;
    private final double bridgeStrength;

    public RealityBridge(String baseDir, double bridgeStrength) {
        this.root = Paths.get(baseDir, "reality");
        this.bridgeStrength = Math.max(0.0, Math.min(1.0, bridgeStrength));
    }

    public double getBridgeStrength() {
        return bridgeStrength;
    }

    public Path logPath() {
        return root.resolve("bridge.log");
    }

    public void ensure() throws IOException {
        Files.createDirectories(root);
    }

    public void recordSnapshot(String line) throws IOException {
        writeLine("snapshot", line);
    }

    /** 沙盒或梦境模块写入的带前缀行（仍受桥接强度标注）。 */
    public void recordModule(String moduleTag, String line) throws IOException {
        writeLine(moduleTag == null ? "module" : moduleTag, line);
    }

    private synchronized void writeLine(String tag, String line) throws IOException {
        ensure();
        Path f = logPath();
        String pct = String.format("%.0f%%", bridgeStrength * 100.0);
        String stamped =
                System.currentTimeMillis()
                        + " ["
                        + tag
                        + "][桥接强度 "
                        + pct
                        + "] "
                        + (line == null ? "" : line)
                        + System.lineSeparator();
        Files.write(f, stamped.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** 读取 bridge.log 末尾若干行（供可视化/API）。 */
    public List<String> readTailLines(int maxLines) throws IOException {
        Path f = logPath();
        if (!Files.isRegularFile(f)) {
            return List.of();
        }
        List<String> all = Files.readAllLines(f, StandardCharsets.UTF_8);
        if (all.size() <= maxLines) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - maxLines, all.size()));
    }
}
