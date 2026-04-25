package com.lovingai.security;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * 限制「出网」风险：本进程 HTTP 仅监听回环；若将来增加客户端，只允许访问本机。
 */
public final class NetworkGuard {

    private NetworkGuard() {}

    public static InetAddress resolveListenAddress() {
        String host = System.getProperty("lovingai.http.host", "127.0.0.1").trim();
        try {
            return InetAddress.getByName(host);
        } catch (Exception e) {
            return InetAddress.getLoopbackAddress();
        }
    }

    public static void assertOutboundAllowed(String host) {
        if (host == null) throw new SecurityException("host null");
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
            return;
        }
        throw new SecurityException("出站访问被拒绝（仅允许本机）: " + host);
    }

    /**
     * 简单的 API Key 鉴权框架。
     * 为云端部署和 SaaS 模式预留的技术底座，防止资源被滥用。
     * 若系统属性 lovingai.api.key 为空，则跳过校验（保持本地开发便捷性）。
     */
    public static boolean isApiKeyValid(String key) {
        String expected = System.getProperty("lovingai.api.key", "").trim();
        if (expected.isEmpty()) return true; // 未设置则不校验
        return expected.equals(key);
    }

    public static void assertUriLocal(URI uri) {
        if (uri == null) throw new SecurityException("uri null");
        String h = uri.getHost();
        assertOutboundAllowed(h);
    }

    /** 校验远端是否为回环（服务端视角） */
    public static boolean isLoopbackClient(InetSocketAddress remote) {
        return remote != null
                && remote.getAddress() != null
                && remote.getAddress().isLoopbackAddress();
    }
}
