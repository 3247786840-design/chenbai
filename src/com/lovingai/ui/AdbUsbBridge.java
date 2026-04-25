package com.lovingai.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 调用本机 {@code adb}（USB 调试），用于在可视化界面中安装伴侣 APK、拉取记录文件。
 * 生命体主程序仍在 PC；手机端仅作数据缓冲。
 */
public final class AdbUsbBridge {

    private AdbUsbBridge() {}

    public static String run(List<String> command, long timeoutMs) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("adb 超时（>" + timeoutMs + "ms）");
        }
        int code = p.exitValue();
        return "exit=" + code + "\n" + out.toString().trim();
    }

    public static String version(String adbExecutable) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(adbExecutable);
        cmd.add("version");
        return run(cmd, 15_000);
    }

    public static String devices(String adbExecutable) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(adbExecutable);
        cmd.add("devices");
        cmd.add("-l");
        return run(cmd, 15_000);
    }

    public static String install(String adbExecutable, String apkPath) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(adbExecutable);
        cmd.add("install");
        cmd.add("-r");
        cmd.add("-d");
        cmd.add(apkPath);
        return run(cmd, 300_000);
    }

    /** 将远端文件或目录拉取到本地目录（adb pull 语义）。 */
    public static String pull(String adbExecutable, String remotePath, String localDirOrFile)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(adbExecutable);
        cmd.add("pull");
        cmd.add(remotePath);
        cmd.add(localDirOrFile);
        return run(cmd, 300_000);
    }
}
