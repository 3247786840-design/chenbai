package com.lovingai.prefs;

import com.lovingai.LivingAI;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** 轻量反馈：追加 NDJSON 至 {@code data/feedback.ndjson}。 */
public final class FeedbackLog {

    private static final Path PATH = Paths.get(LivingAI.BASE_DIR, "feedback.ndjson");

    private FeedbackLog() {}

    public static synchronized void append(String rating, String note) throws java.io.IOException {
        Files.createDirectories(PATH.getParent());
        String line =
                "{\"ts\":\""
                        + Instant.now().toString()
                        + "\",\"rating\":\""
                        + escape(rating)
                        + "\",\"note\":\""
                        + escape(note == null ? "" : note)
                        + "\"}\n";
        Files.writeString(
                PATH,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
