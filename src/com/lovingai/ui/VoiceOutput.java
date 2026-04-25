package com.lovingai.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 将生命体回复转为可听语音（宿主侧）。当前实现：Windows 下调用 PowerShell + System.Speech。
 */
public final class VoiceOutput {

    private static final int MAX_CHARS = 12_000;

    private VoiceOutput() {}

    /** 异步朗读，不阻塞 EDT。非 Windows 或失败时静默跳过。 */
    public static void speakAsync(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return;
        }
        String cleaned = stripForTts(text);
        if (cleaned.isBlank()) {
            return;
        }
        new Thread(
                        () -> {
                            Path tmp = null;
                            try {
                                tmp = Files.createTempFile("lovingai-tts-", ".txt");
                                Files.writeString(tmp, cleaned, StandardCharsets.UTF_8);
                                String pathEsc =
                                        tmp.toAbsolutePath()
                                                .toString()
                                                .replace("'", "''");
                                String cmd =
                                        "$p='"
                                                + pathEsc
                                                + "';"
                                                + "$t=[System.IO.File]::ReadAllText($p,[System.Text.Encoding]::UTF8);"
                                                + "Add-Type -AssemblyName System.Speech;"
                                                + "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;"
                                                + "$zh=$s.GetInstalledVoices()|Where-Object { $_.VoiceInfo.Culture.Name -like 'zh*' }|Select-Object -First 1;"
                                                + "if ($zh) { $s.SelectVoice($zh.VoiceInfo.Name) };"
                                                + "$s.Speak($t)";
                                ProcessBuilder pb =
                                        new ProcessBuilder(
                                                "powershell",
                                                "-NoProfile",
                                                "-ExecutionPolicy",
                                                "Bypass",
                                                "-Command",
                                                cmd);
                                pb.redirectErrorStream(true);
                                Process p = pb.start();
                                int code = p.waitFor();
                                if (code != 0) {
                                    try (var br =
                                            new java.io.BufferedReader(
                                                    new java.io.InputStreamReader(
                                                            p.getInputStream(), StandardCharsets.UTF_8))) {
                                        br.lines().forEach(line -> {});
                                    }
                                }
                            } catch (Exception ignored) {
                            } finally {
                                if (tmp != null) {
                                    try {
                                        Files.deleteIfExists(tmp);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        },
                        "lovingai-tts")
                .start();
    }

    static String stripForTts(String raw) {
        String s = raw.replace('\r', ' ');
        if (s.length() > MAX_CHARS) {
            s = s.substring(0, MAX_CHARS) + "…";
        }
        return s;
    }
}
