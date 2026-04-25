package com.lovingai.verify;

import com.lovingai.registry.DialoguePathCase;
import com.lovingai.registry.DialoguePathCaseRegistry;
import com.lovingai.util.JsonBodyExtract;
import com.lovingai.util.SimplifiedJsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 进程内对话路径自检：对本机 {@code /api/chat} 发起回环请求（不在仓库外依赖脚本）。
 */
public final class DialoguePathVerifier {

    private DialoguePathVerifier() {}

    public static String buildRegistryJson(String baseDir) {
        try {
            List<DialoguePathCase> cases = DialoguePathCaseRegistry.load(baseDir);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"path\":\"registry/dialogue-path-cases.tsv\",\"count\":")
                    .append(cases.size())
                    .append(",\"items\":[");
            boolean first = true;
            for (DialoguePathCase c : cases) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"id\":\"")
                        .append(SimplifiedJsonParser.escapeJson(c.id()))
                        .append("\",\"localMindPrimary\":")
                        .append(c.localMindPrimary())
                        .append(",\"useLocalLlm\":")
                        .append(c.useLocalLlm())
                        .append(",\"expectPathContains\":\"")
                        .append(SimplifiedJsonParser.escapeJson(c.expectPathContains()))
                        .append("\",\"expressionMode\":\"")
                        .append(SimplifiedJsonParser.escapeJson(c.expressionMode()))
                        .append("\"}");
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + SimplifiedJsonParser.escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * @param execute 为 false 时仅返回用例数量与元数据，不调本机模型。
     */
    public static String run(String baseDir, int httpPort, boolean execute) {
        try {
            List<DialoguePathCase> cases = DialoguePathCaseRegistry.load(baseDir);
            if (!execute) {
                return "{\"ok\":true,\"executed\":false,\"caseCount\":"
                        + cases.size()
                        + ",\"httpPort\":"
                        + httpPort
                        + "}";
            }
            if (cases.isEmpty()) {
                return "{\"ok\":false,\"error\":\"registry empty\",\"executed\":true}";
            }
            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
            String uri = "http://127.0.0.1:" + httpPort + "/api/chat";
            ExecutorService pool = Executors.newFixedThreadPool(1);
            try {
                String result =
                        CompletableFuture.supplyAsync(
                                        () -> runCases(client, uri, cases),
                                        pool)
                                .get(20, TimeUnit.MINUTES);
                return result;
            } finally {
                pool.shutdownNow();
            }
        } catch (Exception e) {
            return "{\"ok\":false,\"executed\":"
                    + execute
                    + ",\"error\":\""
                    + SimplifiedJsonParser.escapeJson(e.getMessage())
                    + "\"}";
        }
    }

    private static String runCases(HttpClient client, String uri, List<DialoguePathCase> cases) {
        List<String> items = new ArrayList<>();
        boolean allOk = true;
        for (DialoguePathCase c : cases) {
            String one = runOneCase(client, uri, c);
            items.add(one);
            if (!one.contains("\"ok\":true")) {
                allOk = false;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":")
                .append(allOk)
                .append(",\"executed\":true,\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(items.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String runOneCase(HttpClient client, String uri, DialoguePathCase c) {
        String conv = "registry-" + c.id();
        String body = buildChatJson(c, conv);
        try {
            HttpRequest req =
                    HttpRequest.newBuilder()
                            .uri(URI.create(uri))
                            .timeout(Duration.ofSeconds(240))
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
            HttpResponse<String> resp =
                    client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String json = resp.body();
            if (resp.statusCode() / 100 != 2) {
                return miniCase(
                        c.id(),
                        false,
                        "",
                        "http " + resp.statusCode() + " " + shortErr(json));
            }
            String path = JsonBodyExtract.getString(json, "thinkingPath");
            if (path == null || path.isBlank()) {
                return miniCase(c.id(), false, "", "missing thinkingPath (need debugOutput)");
            }
            String expect = c.expectPathContains() == null ? "" : c.expectPathContains().trim();
            if (!expect.isEmpty()
                    && !path.toLowerCase(Locale.ROOT).contains(expect.toLowerCase(Locale.ROOT))) {
                return miniCase(
                        c.id(),
                        false,
                        path,
                        "thinkingPath mismatch want contains " + expect);
            }
            return miniCase(c.id(), true, path, "");
        } catch (Exception e) {
            return miniCase(c.id(), false, "", e.getMessage() == null ? "error" : e.getMessage());
        }
    }

    private static String shortErr(String json) {
        if (json == null) {
            return "";
        }
        String s = json.replace('\n', ' ');
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private static String miniCase(String id, boolean ok, String thinkingPath, String error) {
        return "{\"id\":\""
                + SimplifiedJsonParser.escapeJson(id)
                + "\",\"ok\":"
                + ok
                + ",\"thinkingPath\":\""
                + SimplifiedJsonParser.escapeJson(thinkingPath == null ? "" : thinkingPath)
                + "\",\"error\":\""
                + SimplifiedJsonParser.escapeJson(error == null ? "" : error)
                + "\"}";
    }

    private static String buildChatJson(DialoguePathCase c, String conversationId) {
        return "{"
                + "\"message\":\""
                + SimplifiedJsonParser.escapeJson(c.message())
                + "\",\"conversationId\":\""
                + SimplifiedJsonParser.escapeJson(conversationId)
                + "\",\"useLocalLlm\":\""
                + c.useLocalLlm()
                + "\",\"localMindPrimary\":\""
                + c.localMindPrimary()
                + "\",\"useImported\":\"false\",\"expressionMode\":\""
                + SimplifiedJsonParser.escapeJson(c.expressionMode())
                + "\",\"debugOutput\":\"true\"}";
    }
}
