package com.lovingai.registry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从 {@code data/registry/dialogue-path-cases.tsv} 加载用例；缺文件时用类内嵌默认表（仍建议提交仓库内 TSV）。
 */
public final class DialoguePathCaseRegistry {

    private static final String EMBEDDED_DEFAULT =
            "path_primary\ttrue\ttrue\t注册表探针·本机主声\tlocal_mind\tauto\n"
                    + "path_parallel\ttrue\tfalse\t注册表探针·并联回声\tmodel_aux\tauto\n";

    private DialoguePathCaseRegistry() {}

    public static Path defaultPath(String baseDir) {
        return Path.of(baseDir, "registry", "dialogue-path-cases.tsv");
    }

    public static List<DialoguePathCase> load(String baseDir) throws Exception {
        Path p = defaultPath(baseDir);
        String raw;
        if (Files.isRegularFile(p)) {
            raw = Files.readString(p, StandardCharsets.UTF_8);
        } else {
            raw = EMBEDDED_DEFAULT;
        }
        return parse(raw);
    }

    static List<DialoguePathCase> parse(String raw) {
        List<DialoguePathCase> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String line : raw.split("\r?\n")) {
            if (line == null) {
                continue;
            }
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] c = t.split("\t", -1);
            if (c.length < 6) {
                continue;
            }
            String id = c[0].trim();
            boolean lmPrimary = truthy(c[1]);
            boolean useLlm = truthy(c[2]);
            String msg = c[3].trim();
            String expect = c[4].trim();
            String emode = c[5].trim();
            if (emode.isBlank()) {
                emode = "auto";
            }
            if (id.isBlank() || msg.isBlank()) {
                continue;
            }
            out.add(
                    new DialoguePathCase(
                            id, lmPrimary, useLlm, msg, expect, emode.toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    private static boolean truthy(String s) {
        if (s == null) {
            return false;
        }
        String v = s.trim().toLowerCase(Locale.ROOT);
        return "1".equals(v)
                || "true".equals(v)
                || "yes".equals(v)
                || "on".equals(v);
    }
}
