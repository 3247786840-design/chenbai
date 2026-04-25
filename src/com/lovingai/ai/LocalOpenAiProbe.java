package com.lovingai.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 在本机常见端口上寻找 OpenAI 兼容 {@code /v1/models}（如 LM Studio Local Server），供自动适配基址。
 */
public final class LocalOpenAiProbe {

    private LocalOpenAiProbe() {}

    /** 系统属性 {@code lovingai.localai.autoProbe}，默认 {@code true}；设为 {@code false} 关闭自动探测。 */
    public static boolean autoProbeEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("lovingai.localai.autoProbe", "true").trim());
    }

    /**
     * 逗号分隔端口列表，例如 {@code 1234,8000}；未设置时使用内置顺序。
     */
    public static List<Integer> portsInOrder() {
        String raw = System.getProperty("lovingai.localai.probe.ports", "").trim();
        List<Integer> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String p : raw.split(",")) {
                try {
                    int n = Integer.parseInt(p.trim());
                    if (n > 0 && n < 65536) {
                        out.add(n);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        // 不含 8080：与本进程 LivingAI.HTTP_PORT 默认相同，该端口上 /v1/models 可能非 LLM 而误判。
        int[] def = {1234, 8000, 5000, 11434, 8765, 3000};
        for (int n : def) {
            out.add(n);
        }
        return out;
    }

    /**
     * 返回首个 {@link OllamaProxy#probeOpenAiV1Models(String)} 为真的本机基址（{@code http://127.0.0.1:端口}，无尾部
     * {@code /}）。
     */
    public static Optional<String> firstReachableBase() {
        for (int port : portsInOrder()) {
            String candidate = "http://127.0.0.1:" + port;
            if (OllamaProxy.probeOpenAiV1Models(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * 是否更像「端口未监听 / 拒绝连接 / 超时」而非 HTTP 业务错误（后者不应触发换端口）。
     */
    public static boolean looksLikeConnectionFailure(Throwable e) {
        if (e == null) {
            return false;
        }
        Throwable t = e;
        while (t != null) {
            if (t instanceof java.net.ConnectException) {
                return true;
            }
            if (t instanceof java.net.http.HttpConnectTimeoutException) {
                return true;
            }
            if (t instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            if (t instanceof java.nio.channels.UnresolvedAddressException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String ml = msg.toLowerCase(Locale.ROOT);
                if (ml.contains("connection refused")
                        || ml.contains("failed to connect")
                        || ml.contains("actively refused")
                        || ml.contains("connection reset")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }
}
