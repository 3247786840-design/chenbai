package com.lovingai.prefs;

import com.lovingai.LivingAI;
import com.lovingai.util.SimplifiedJsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 读写 {@code data/preferences.json}。 */
public final class PreferenceStore {

    private static volatile UserPreferences current = UserPreferences.defaults();

    private PreferenceStore() {}

    public static UserPreferences current() {
        return current;
    }

    public static synchronized void load() throws IOException {
        Path p = Paths.get(LivingAI.BASE_DIR, "preferences.json");
        if (!Files.isRegularFile(p)) {
            current = UserPreferences.defaults();
            save();
            return;
        }
        String j = Files.readString(p, StandardCharsets.UTF_8);
        current = parseLoose(j);
    }

    public static synchronized void save() throws IOException {
        Path p = Paths.get(LivingAI.BASE_DIR, "preferences.json");
        Files.createDirectories(p.getParent());
        Files.writeString(p, toJson(current), StandardCharsets.UTF_8);
    }

    public static synchronized void replace(UserPreferences next) throws IOException {
        current = next == null ? UserPreferences.defaults() : next;
        save();
    }

    public static String toJson(UserPreferences u) {
        return "{"
                + "\"proactiveEnabled\":"
                + u.proactiveEnabled()
                + ",\"philosophySchedulerEnabled\":"
                + u.philosophySchedulerEnabled()
                + ",\"voiceMergeMode\":\""
                + SimplifiedJsonParser.escapeJson(u.voiceMergeMode())
                + "\",\"failureAutoDemote\":"
                + u.failureAutoDemote()
                + "}";
    }

    public static String jsonObjectCurrent() {
        return toJson(current);
    }

    public static UserPreferences parseLoose(String j) {
        if (j == null || j.isBlank()) {
            return UserPreferences.defaults();
        }
        boolean pro = !j.contains("\"proactiveEnabled\":false");
        boolean phil = !j.contains("\"philosophySchedulerEnabled\":false");
        String mode = "structure_first";
        if (j.contains("\"voiceMergeMode\":\"voice_primary\"")) {
            mode = "voice_primary";
        } else if (j.contains("\"voiceMergeMode\":\"lead_then_structure\"")) {
            mode = "lead_then_structure";
        }
        boolean dem = !j.contains("\"failureAutoDemote\":false");
        return new UserPreferences(pro, phil, mode, dem);
    }
}
