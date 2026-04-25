package com.lovingai.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 向本机推理服务转发请求，使用 OpenAI 兼容 API（如 LM Studio 的 {@code /v1/chat/completions}、{@code
 * /v1/models}），避免把流量导向外网。类名历史遗留，与 Ollama 无依赖。
 */
public final class OllamaProxy {

    private static final Pattern OPENAI_MODEL_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private static final int LOCALAI_MAX_CONCURRENT_REQUESTS = resolveLocalAiMaxConcurrentRequests();
    private static final Semaphore LOCALAI_GATE = new Semaphore(LOCALAI_MAX_CONCURRENT_REQUESTS, true);

    private OllamaProxy() {}

    private static int resolveLocalAiMaxConcurrentRequests() {
        String p = System.getProperty("lovingai.localai.maxConcurrentRequests", "").trim();
        if (p.isEmpty()) {
            return 1;
        }
        try {
            int v = Integer.parseInt(p);
            if (v < 1) return 1;
            if (v > 8) return 8;
            return v;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static final class GatePermit implements AutoCloseable {
        final boolean acquired;

        GatePermit(boolean acquired) {
            this.acquired = acquired;
        }

        @Override
        public void close() {
            if (acquired) {
                LOCALAI_GATE.release();
            }
        }
    }

    private static GatePermit acquireGate(long waitSec) throws IOException, InterruptedException {
        LOCALAI_GATE.acquire();
        return new GatePermit(true);
    }

    public static void assertLocalBase(URI uri) {
        if (uri == null) throw new IllegalArgumentException("uri null");
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("http")) {
            throw new SecurityException("仅允许 http 本机推理端点");
        }
        String host = uri.getHost();
        if (host == null) throw new SecurityException("host null");
        String h = host.toLowerCase(Locale.ROOT);
        if (!h.equals("127.0.0.1") && !h.equals("localhost") && !h.equals("[::1]")) {
            throw new SecurityException("仅允许本机地址 (127.0.0.1 / localhost / ::1): " + host);
        }
    }

    /**
     * POST {@code /v1/chat/completions}。{@code optionsJson} 保留以兼容旧调用，OpenAI 兼容路径不使用该项。
     */
    public static String generate(
            String baseUrl, String model, String prompt, int timeoutSec, String optionsJson)
            throws IOException, InterruptedException {
        return generateOpenAiChatCompletions(baseUrl, model, prompt, timeoutSec);
    }

    public static String generate(String baseUrl, String model, String prompt, int timeoutSec)
            throws IOException, InterruptedException {
        return generate(baseUrl, model, prompt, timeoutSec, "");
    }

    /**
     * 多模态：用户消息可带 base64 图片（无 data: 前缀）；仅本机 {@code baseUrl}。
     */
    public static String chatWithVision(
            String baseUrl,
            String model,
            String userText,
            List<String> base64Images,
            int timeoutSec,
            String optionsJson)
            throws IOException, InterruptedException {
        if (base64Images == null || base64Images.isEmpty()) {
            return generate(baseUrl, model, userText == null ? "" : userText, timeoutSec, optionsJson);
        }
        return openAiChatWithVision(baseUrl, model, userText, base64Images, timeoutSec);
    }

    public static String chatWithVision(
            String baseUrl, String model, String userText, List<String> base64Images, int timeoutSec)
            throws IOException, InterruptedException {
        return chatWithVision(baseUrl, model, userText, base64Images, timeoutSec, "");
    }

    /** GET {@code /v1/models}，返回可用模型 id 列表（去重保序）。 */
    public static List<String> listModelTags(String baseUrl) throws IOException, InterruptedException {
        return listOpenAiModelIds(baseUrl);
    }

    /**
     * 快速探测本机 {@code /v1/models} 是否返回 2xx 且正文像 OpenAI 兼容 JSON（短超时，用于自动寻找 LM Studio
     * 等端口）。仅看 HTTP 状态会误判：本应用自身 HTTP 端口可能返回 HTML 或其它 200 正文。
     */
    public static boolean probeOpenAiV1Models(String baseUrl) {
        try {
            String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            URI root = URI.create(b);
            assertLocalBase(root);
            URI models = root.resolve("/v1/models");
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req =
                    HttpRequest.newBuilder(models)
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();
            HttpResponse<String> res =
                    client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                return false;
            }
            return isPlausibleOpenAiModelsJson(res.body());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 粗略校验 OpenAI {@code GET /v1/models} 常见 JSON 形态，排除 HTML、空体或非 list 接口误配。
     */
    static boolean isPlausibleOpenAiModelsJson(String body) {
        if (body == null) {
            return false;
        }
        String s = body.stripLeading();
        if (s.isEmpty() || s.charAt(0) != '{') {
            return false;
        }
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("<!doctype") || low.contains("<html") || low.contains("</html>")) {
            return false;
        }
        return s.contains("\"object\"") && s.contains("\"list\"") && s.contains("\"data\"");
    }

    private static List<String> listOpenAiModelIds(String baseUrl) throws IOException, InterruptedException {
        URI root = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        assertLocalBase(root);
        URI models = root.resolve("/v1/models");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req =
                HttpRequest.newBuilder(models)
                        .timeout(Duration.ofSeconds(120))
                        .GET()
                        .build();
        HttpResponse<String> res;
        try (GatePermit ignored = acquireGate(130L)) {
            res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("OpenAI-compatible /v1/models HTTP " + res.statusCode() + ": " + res.body());
        }
        String raw = res.body();
        if (!isPlausibleOpenAiModelsJson(raw)) {
            String hint = raw == null ? "" : raw.stripLeading();
            if (hint.length() > 240) {
                hint = hint.substring(0, 240) + "…";
            }
            throw new IOException(
                    "OpenAI-compatible /v1/models: 响应不是模型列表 JSON（请确认基址指向 LM Studio 等 OpenAI 兼容服务，而非本应用页面）。前缀: "
                            + hint);
        }
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = OPENAI_MODEL_ID.matcher(raw);
        while (m.find()) {
            String id = m.group(1);
            if (id != null
                    && !id.isBlank()
                    && !"list".equals(id)
                    && !"model".equals(id)
                    && !id.startsWith("chatcmpl")) {
                seen.add(id);
            }
        }
        return new ArrayList<>(seen);
    }

    private static String generateOpenAiChatCompletions(
            String baseUrl, String model, String prompt, int timeoutSec) throws IOException, InterruptedException {
        URI root = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        assertLocalBase(root);
        URI chat = root.resolve("/v1/chat/completions");
        double temp = openAiTemperature();
        Double topP = openAiTopPOrNull();
        int maxTok = openAiMaxTokens();
        StringBuilder body = new StringBuilder(256 + (prompt == null ? 0 : prompt.length()));
        body.append("{\"model\":\"")
                .append(escapeJson(model))
                .append("\",\"messages\":[{\"role\":\"user\",\"content\":\"")
                .append(escapeJson(prompt == null ? "" : prompt))
                .append("\"}],\"temperature\":")
                .append(temp);
        if (topP != null) {
            body.append(",\"top_p\":").append(topP);
        }
        body.append(",\"max_tokens\":").append(maxTok).append(",\"stream\":false}");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req =
                HttpRequest.newBuilder(chat)
                        .timeout(Duration.ofSeconds(Math.max(10, timeoutSec)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> res;
        try (GatePermit ignored = acquireGate((long) Math.max(15, timeoutSec) + 15L)) {
            res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("OpenAI-compatible /v1/chat/completions HTTP " + res.statusCode() + ": " + res.body());
        }
        String raw = res.body();
        String extracted = extractOpenAiChatCompletionContent(raw);
        return extracted.isEmpty() ? raw : extracted;
    }

    private static String openAiChatWithVision(
            String baseUrl,
            String model,
            String userText,
            List<String> base64Images,
            int timeoutSec)
            throws IOException, InterruptedException {
        URI root = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        assertLocalBase(root);
        URI chat = root.resolve("/v1/chat/completions");
        double temp = openAiTemperature();
        Double topP = openAiTopPOrNull();
        int maxTok = Math.max(1024, openAiMaxTokens() * 2);
        StringBuilder content = new StringBuilder(256 + base64Images.size() * 64);
        content.append("[{\"type\":\"text\",\"text\":\"").append(escapeJson(userText == null ? "" : userText)).append("\"}");
        for (String b64 : base64Images) {
            if (b64 == null || b64.isBlank()) {
                continue;
            }
            String url = "data:image/jpeg;base64," + b64.trim();
            content.append(",{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
                    .append(escapeJson(url))
                    .append("\"}}");
        }
        content.append("]");
        StringBuilder body = new StringBuilder(256 + content.length());
        body.append("{\"model\":\"")
                .append(escapeJson(model))
                .append("\",\"messages\":[{\"role\":\"user\",\"content\":")
                .append(content)
                .append("}],\"temperature\":")
                .append(temp);
        if (topP != null) {
            body.append(",\"top_p\":").append(topP);
        }
        body.append(",\"max_tokens\":").append(maxTok).append(",\"stream\":false}");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req =
                HttpRequest.newBuilder(chat)
                        .timeout(Duration.ofSeconds(Math.max(15, timeoutSec)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> res;
        try (GatePermit ignored = acquireGate((long) Math.max(20, timeoutSec) + 20L)) {
            res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException(
                    "OpenAI-compatible vision /v1/chat/completions HTTP " + res.statusCode() + ": " + res.body());
        }
        String raw = res.body();
        String extracted = extractOpenAiChatCompletionContent(raw);
        return extracted.isEmpty() ? raw : extracted;
    }

    /**
     * 提取 OpenAI-compatible {@code /v1/chat/completions} 的首个 {@code choices[0].message.content}。
     * 避免使用复杂正则导致超长响应时的灾难性回溯卡死。
     */
    static String extractOpenAiChatCompletionContent(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            int choicesKey = indexOfUnquoted(raw, "\"choices\"", 0);
            if (choicesKey < 0) {
                return "";
            }
            int colon = raw.indexOf(':', choicesKey + 9);
            if (colon < 0) {
                return "";
            }
            int lb = skipWs(raw, colon + 1);
            if (lb < 0 || lb >= raw.length() || raw.charAt(lb) != '[') {
                return "";
            }
            int firstObj = skipWs(raw, lb + 1);
            if (firstObj < 0 || firstObj >= raw.length() || raw.charAt(firstObj) != '{') {
                return "";
            }
            int endChoice = endIndexExclusiveBalanced(raw, firstObj, '{', '}');
            if (endChoice <= firstObj) {
                return "";
            }
            String choiceObj = raw.substring(firstObj, endChoice);
            int messageKey = indexOfUnquoted(choiceObj, "\"message\"", 0);
            if (messageKey < 0) {
                return "";
            }
            int colon2 = choiceObj.indexOf(':', messageKey + 9);
            if (colon2 < 0) {
                return "";
            }
            int msgObjStart = skipWs(choiceObj, colon2 + 1);
            if (msgObjStart < 0 || msgObjStart >= choiceObj.length() || choiceObj.charAt(msgObjStart) != '{') {
                return "";
            }
            int msgObjEnd = endIndexExclusiveBalanced(choiceObj, msgObjStart, '{', '}');
            if (msgObjEnd <= msgObjStart) {
                return "";
            }
            String msgObj = choiceObj.substring(msgObjStart, msgObjEnd);
            int contentKey = indexOfUnquoted(msgObj, "\"content\"", 0);
            if (contentKey < 0) {
                return "";
            }
            int colon3 = msgObj.indexOf(':', contentKey + 9);
            if (colon3 < 0) {
                return "";
            }
            int val = skipWs(msgObj, colon3 + 1);
            if (val < 0 || val >= msgObj.length() || msgObj.charAt(val) != '"') {
                return "";
            }
            return readJsonStringContent(msgObj, val);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int skipWs(String s, int from) {
        int i = from;
        while (i >= 0 && i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int indexOfUnquoted(String s, String needle, int from) {
        if (needle == null || needle.isEmpty()) {
            return -1;
        }
        boolean inString = false;
        boolean esc = false;
        for (int i = Math.max(0, from); i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inString) {
                if (esc) {
                    esc = false;
                } else if (ch == '\\') {
                    esc = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == needle.charAt(0) && s.startsWith(needle, i)) {
                return i;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
        }
        return -1;
    }

    private static int endIndexExclusiveBalanced(String s, int openIdx, char open, char close) {
        if (openIdx < 0 || openIdx >= s.length() || s.charAt(openIdx) != open) {
            return -1;
        }
        boolean inString = false;
        boolean esc = false;
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inString) {
                if (esc) {
                    esc = false;
                } else if (ch == '\\') {
                    esc = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private static String readJsonStringContent(String json, int openQuote) {
        if (openQuote < 0 || openQuote >= json.length() || json.charAt(openQuote) != '"') {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = openQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case '\\' -> b.append('\\');
                    case '"' -> b.append('"');
                    default -> {
                        b.append('\\');
                        b.append(n);
                    }
                }
            } else if (c == '"') {
                break;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static double openAiTemperature() {
        String override = System.getProperty("lovingai.localai.temperature", "").trim();
        if (!override.isEmpty()) {
            try {
                return clampDouble(Double.parseDouble(override), 0.1, 2.0);
            } catch (NumberFormatException ignored) {
                // fall through to latency profile
            }
        }
        String p = LocalAiPrefs.getLatencyProfile();
        if ("fast".equals(p)) {
            return 0.65;
        }
        if ("deep".equals(p)) {
            return 0.72;
        }
        return 0.70;
    }

    private static Double openAiTopPOrNull() {
        String override = System.getProperty("lovingai.localai.topP", "").trim();
        if (override.isEmpty()) {
            override = System.getProperty("lovingai.localai.top_p", "").trim();
        }
        if (override.isEmpty()) {
            return null;
        }
        try {
            return clampDouble(Double.parseDouble(override), 0.05, 1.0);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double clampDouble(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static int openAiMaxTokens() {
        String override = System.getProperty("lovingai.localai.maxTokens", "").trim();
        if (override.isEmpty()) {
            override = System.getProperty("lovingai.localai.max_tokens", "").trim();
        }
        if (!override.isEmpty()) {
            try {
                int v = Integer.parseInt(override);
                if (v < 64) return 64;
                if (v > 4096) return 4096;
                return v;
            } catch (NumberFormatException ignored) {
            }
        }
        String p = LocalAiPrefs.getLatencyProfile();
        if ("fast".equals(p)) {
            return 320;
        }
        if ("deep".equals(p)) {
            return 1024;
        }
        return 384;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJsonString(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                i++;
                switch (n) {
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case '\\' -> b.append('\\');
                    case '"' -> b.append('"');
                    default -> {
                        b.append('\\');
                        b.append(n);
                    }
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
