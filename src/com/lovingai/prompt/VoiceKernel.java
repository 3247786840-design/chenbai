package com.lovingai.prompt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 单一声音内核：主对话、主动补写、自解等共享同一「五维」参照，版本号用于观测与回滚对照。
 *
 * <p>可选外置正文：{@code data/prompts/five-dims-kernel.md}（UTF-8），或 {@code -Dlovingai.prompt.fiveDimsPath=...}。
 */
public final class VoiceKernel {

    /** 与提示词内容同步递增（外置 md 覆盖失败时回退内嵌正文仍用此版本标记）。 */
    public static final String VERSION = "2";

    private static final String EMBEDDED_FIVE_DIMS =
            "【五维】自然渗入即可，勿列清单、勿标签式宣读："
                    + "多层——表层与暗流各晃一下；联系——让意象、时间、身体感或人称彼此勾住；"
                    + "深入——再往下一寸，落到细节或可感的分寸；混合——让犹豫、眷恋、怕、柔软在同声部里叠响；"
                    + "情感——温度从字里渗出来，少用抽象情绪词空转。";

    private static volatile String overlayFiveDims;
    private static volatile boolean overlayLoaded;

    private static final String EMBEDDED_PERSONA_CONSTANTS =
            "人格常量（不应频繁更换）："
                    + "①诚实与可验证：不编造；不确定要标注；"
                    + "②边界：不操控他人、不越权替人做决定；"
                    + "③关系连续性：同会话立场变化须解释理由；"
                    + "④有限视角：低置信时默认猜测语气，允许保留矛盾与暂缓结论。";

    private static volatile String overlayPersonaConstants;
    private static volatile boolean overlayPersonaLoaded;

    private VoiceKernel() {}

    static {
        loadOptionalOverlay();
    }

    private static void loadOptionalOverlay() {
        try {
            Path p = resolveFiveDimsPath();
            if (Files.isRegularFile(p)) {
                String t = Files.readString(p, StandardCharsets.UTF_8);
                if (t != null && !t.isBlank()) {
                    overlayFiveDims = t.trim();
                    overlayLoaded = true;
                }
            }
        } catch (Exception ignored) {
            // keep embedded
        }
        if (overlayFiveDims == null || overlayFiveDims.isBlank()) {
            overlayFiveDims = null;
            overlayLoaded = false;
        }
        try {
            Path p = resolvePersonaConstantsPath();
            if (Files.isRegularFile(p)) {
                String t = Files.readString(p, StandardCharsets.UTF_8);
                if (t != null && !t.isBlank()) {
                    overlayPersonaConstants = t.trim();
                    overlayPersonaLoaded = true;
                    return;
                }
            }
        } catch (Exception ignored) {
            // keep embedded
        }
        overlayPersonaConstants = null;
        overlayPersonaLoaded = false;
    }

    private static Path resolveFiveDimsPath() {
        String prop = System.getProperty("lovingai.prompt.fiveDimsPath");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop.trim());
        }
        return Paths.get("data", "prompts", "five-dims-kernel.md");
    }

    private static Path resolvePersonaConstantsPath() {
        String prop = System.getProperty("lovingai.prompt.personaConstantsPath");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop.trim());
        }
        return Paths.get("data", "prompts", "persona-constants.md");
    }

    /** 供测试或宿主在写文件后调用。 */
    public static synchronized void reloadOptionalOverlay() {
        loadOptionalOverlay();
    }

    /** 解析后版本标签：含外置覆盖时带 {@code +overlay} 后缀。 */
    public static String resolvedVersion() {
        return (overlayLoaded || overlayPersonaLoaded) ? VERSION + "+overlay" : VERSION;
    }

    /**
     * 五维（多层 / 联系 / 深入 / 混合 / 情感）——自然渗入叙述，勿对用户列清单或宣读标签。
     */
    public static String fiveDimsKernel() {
        if (overlayFiveDims != null && !overlayFiveDims.isBlank()) {
            return overlayFiveDims;
        }
        return EMBEDDED_FIVE_DIMS;
    }

    public static String personaConstantsKernel() {
        if (overlayPersonaConstants != null && !overlayPersonaConstants.isBlank()) {
            return overlayPersonaConstants;
        }
        return EMBEDDED_PERSONA_CONSTANTS;
    }

    public static String personaConstantsKernelLine() {
        return personaConstantsKernel();
    }

    /** 主链本机主声提示中嵌入的一行，与主动通道共享同一内核语义。 */
    public static String chatSharedKernelLine() {
        return "【共享内核·与主动通道同源】" + fiveDimsKernel();
    }

    /** 主动 / 自解补写 prompt 中嵌入的五维段。 */
    public static String proactivePromptFragment() {
        return fiveDimsKernel();
    }
}
