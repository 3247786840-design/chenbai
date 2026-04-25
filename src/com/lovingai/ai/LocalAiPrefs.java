package com.lovingai.ai;

import com.lovingai.LivingAI;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 本机推理服务基址与默认模型（默认文件为 {@code data/localai.properties}）；使用 OpenAI 兼容端（如 LM
 * Studio）。
 *
 * <p>若启动 Java 时工作目录不是项目根，可通过 {@code -Dlovingai.data.root=绝对路径} 指向包含
 * {@code localai.properties} 的目录（例如 {@code D:\\\\repo\\\\LovingAI\\\\data}）。
 */
public final class LocalAiPrefs {

    private LocalAiPrefs() {}

    private static Path prefsPath() {
        String root = System.getProperty("lovingai.data.root", "").trim();
        if (!root.isEmpty()) {
            return Paths.get(root, "localai.properties");
        }
        return Paths.get(LivingAI.BASE_DIR, "localai.properties");
    }

    /** 默认指向 LM Studio 本地服务器常见端口；可在 {@code data/localai.properties} 覆盖。 */
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:1234";

    /** 未配置 {@code model} 时的默认 id（须与 LM Studio 已加载模型名一致，例如 HF 风格 id）。 */
    public static final String DEFAULT_MODEL = "google/gemma-4-e4b";

    /**
     * 读取基址；若仍为旧版 Ollama 默认端口 {@code 11434}，则视为应使用 LM Studio 常见端口并返回 {@link
     * #DEFAULT_BASE_URL}（配置文件可在下次保存时覆盖）。
     */
    public static String getBaseUrl() {
        String u = load().getProperty("baseUrl", DEFAULT_BASE_URL).trim();
        if (u.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        try {
            String normalized = u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
            URI uri = URI.create(normalized);
            if (uri.getPort() == 11434) {
                return DEFAULT_BASE_URL;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return u;
    }

    /**
     * 固定为 {@code openai}（OpenAI 兼容 /v1/chat/completions）。保留键值以便旧配置迁移；新安装无需关心。
     */
    public static String getInferenceBackend() {
        return "openai";
    }

    public static String getModel() {
        return load().getProperty("model", DEFAULT_MODEL);
    }

    /** 并联辅助链专用；空则由 {@link com.lovingai.ai.LocalAiModelRouter} 回退到主模型。 */
    public static String getAuxModel() {
        return load().getProperty("auxModel", "").trim();
    }

    /** 上下文/输出压缩；空则回退 fast 再回退主模型。 */
    public static String getCompressModel() {
        return load().getProperty("compressModel", "").trim();
    }

    /** 短轮次（探针、主动消息等）；空则回退主模型。 */
    public static String getFastModel() {
        return load().getProperty("fastModel", "").trim();
    }

    /** 文学蒸馏；空则回退主模型。 */
    public static String getDistillModel() {
        return load().getProperty("distillModel", "").trim();
    }

    /**
     * 时延档位：fast=极速（优先响应速度）/ balanced=平衡 / deep=深思（更长展开）。
     * 未配置时默认 balanced。
     */
    public static String getLatencyProfile() {
        String p = load().getProperty("latencyProfile", "deep").trim().toLowerCase();
        if ("fast".equals(p) || "balanced".equals(p) || "deep".equals(p)) {
            return p;
        }
        return "deep";
    }

    /**
     * 视觉/多模态模型，用于图像或视频帧侧写；空串表示未单独配置。
     * 未配置时由调用方回退到 {@link #getModel()}。
     */
    public static String getVisionModel() {
        return load().getProperty("visionModel", "").trim();
    }

    /** 有图时使用的模型：若未配置 visionModel 则与主模型相同。 */
    public static String resolveVisionModel() {
        String v = getVisionModel();
        return v.isBlank() ? getModel() : v;
    }

    /**
     * 历史键名 {@code ollamaGenerateOptions}：若仍存在则原样读出（仅供偏好展示）；当前推理路径不使用该项。
     */
    public static String getOllamaGenerateOptionsJson() {
        return load().getProperty("ollamaGenerateOptions", "").trim();
    }

    public static void save(String baseUrl, String model) throws IOException {
        save(baseUrl, model, null, null);
    }

    /**
     * @param ollamaGenerateOptionsOrNull {@code null} 表示不修改该项；否则写入或清空（空串则删除键）
     * @param visionModelOrNull {@code null} 表示不修改 visionModel；空串则删除键（回退到与主模型相同）
     */
    public static void save(String baseUrl, String model, String ollamaGenerateOptionsOrNull)
            throws IOException {
        save(baseUrl, model, ollamaGenerateOptionsOrNull, null);
    }

    public static void save(
            String baseUrl,
            String model,
            String ollamaGenerateOptionsOrNull,
            String visionModelOrNull)
            throws IOException {
        Properties p = load();
        if (baseUrl != null && !baseUrl.isBlank()) {
            p.setProperty("baseUrl", baseUrl.trim());
        } else {
            p.setProperty("baseUrl", DEFAULT_BASE_URL);
        }
        if (model != null && !model.isBlank()) {
            p.setProperty("model", model.trim());
        } else {
            p.setProperty("model", DEFAULT_MODEL);
        }
        if (ollamaGenerateOptionsOrNull != null) {
            String t = ollamaGenerateOptionsOrNull.trim();
            if (t.isEmpty()) {
                p.remove("ollamaGenerateOptions");
            } else {
                p.setProperty("ollamaGenerateOptions", t);
            }
        }
        if (visionModelOrNull != null) {
            String v = visionModelOrNull.trim();
            if (v.isEmpty()) {
                p.remove("visionModel");
            } else {
                p.setProperty("visionModel", v);
            }
        }
        p.setProperty("inferenceBackend", "openai");
        Path path = prefsPath();
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            p.store(w, "LovingAI local inference");
        }
    }

    /** 统一写入 {@code openai}（请求体中的旧值忽略）。 */
    public static void setInferenceBackend(String ignoredInferenceBackend) throws IOException {
        Properties p = load();
        p.setProperty("inferenceBackend", "openai");
        Path path = prefsPath();
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            p.store(w, "LovingAI local inference");
        }
    }

    public static void saveLatencyProfile(String latencyProfileOrNull) throws IOException {
        if (latencyProfileOrNull == null) {
            return;
        }
        String p = latencyProfileOrNull.trim().toLowerCase();
        if (!"fast".equals(p) && !"balanced".equals(p) && !"deep".equals(p)) {
            return;
        }
        Properties props = load();
        props.setProperty("latencyProfile", p);
        Path path = prefsPath();
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            props.store(w, "LovingAI local inference");
        }
    }

    private static Properties load() {
        Properties p = new Properties();
        Path path = prefsPath();
        if (!Files.isRegularFile(path)) {
            return p;
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException ignored) {
        }
        return p;
    }
}
