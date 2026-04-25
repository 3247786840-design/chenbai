package com.lovingai.learn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * 对<strong>已列入白名单</strong>的 https 主机发起 GET，抽取正文类文本（HTML 去标签）。
 * 非无头浏览器：不执行 JavaScript；所谓「视觉」指阅读页面 HTML 文本结构，非截图 OCR。
 */
public final class WebContentFetcher {

    public static final int MAX_BYTES = 2_000_000;
    private static final Duration CONNECT = Duration.ofSeconds(15);
    private static final Duration READ = Duration.ofSeconds(45);

    private WebContentFetcher() {}

    public static FetchedPage fetch(String urlString) throws IOException, InterruptedException {
        URI uri = URI.create(urlString.trim());
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IOException("仅允许 http/https URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("URL 缺少主机");
        }
        String normalizedHost = WebFetchAllowlist.normalizeHost(host);
        if (!WebFetchAllowlist.INSTANCE.allows(normalizedHost)) {
            throw new IOException(
                    "主机未在白名单："
                            + normalizedHost
                            + "。请先在 data/web-fetch-allowlist.txt 添加该主机，或通过 API 添加。");
        }
        if (isPrivateOrLoopbackHost(normalizedHost)) {
            throw new IOException("禁止访问本机/内网地址: " + normalizedHost);
        }

        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        HttpRequest req =
                HttpRequest.newBuilder(uri)
                        .timeout(READ)
                        .header(
                                "User-Agent",
                                "LovingAI/1.0 (local; +https://127.0.0.1"
                                        + com.lovingai.LivingAI.HTTP_PORT
                                        + " 站点学习)")
                        .GET()
                        .build();

        HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        int code = res.statusCode();
        if (code / 100 == 3) {
            String loc = res.headers().firstValue("Location").orElse("");
            if (loc.isBlank()) {
                throw new IOException("重定向缺少 location");
            }
            URI redirected = uri.resolve(loc);
            String rh = WebFetchAllowlist.normalizeHost(redirected.getHost());
            if (!WebFetchAllowlist.INSTANCE.allows(rh) || isPrivateOrLoopbackHost(rh)) {
                throw new IOException("重定向目标不允许: " + rh);
            }
            req =
                    HttpRequest.newBuilder(redirected)
                            .timeout(READ)
                            .header(
                                    "User-Agent",
                                    "LovingAI/1.0 (local; +https://127.0.0.1"
                                            + com.lovingai.LivingAI.HTTP_PORT
                                            + " 站点学习)")
                            .GET()
                            .build();
            res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            code = res.statusCode();
        }
        if (code / 100 != 2) {
            throw new IOException("HTTP " + code);
        }
        byte[] raw = res.body();
        if (raw.length > MAX_BYTES) {
            throw new IOException("页面过大（上限约 " + MAX_BYTES / 1_000_000 + "MB）");
        }
        String nameGuess = "fetch.html";
        String path = uri.getPath();
        if (path != null && path.contains(".")) {
            String leaf = path.substring(path.lastIndexOf('/') + 1);
            if (leaf.length() < 80 && leaf.matches("(?i).+\\.(html|htm|txt|xml|json)")) {
                nameGuess = leaf;
            }
        }
        Optional<String> text = DocumentTextExtractor.extract(nameGuess, raw);
        if (text.isEmpty()) {
            String fallback =
                    new String(
                            raw,
                            0,
                            Math.min(raw.length, 500_000),
                            StandardCharsets.UTF_8);
            text = Optional.of(stripHtmlish(fallback));
        }
        String t = text.get();
        final int cap = 400_000;
        if (t.length() > cap) {
            t = t.substring(0, cap) + "\n…(截断)";
        }
        return new FetchedPage(uri.toString(), host, code, t);
    }

    private static String stripHtmlish(String s) {
        return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean isPrivateOrLoopbackHost(String host) {
        if (host == null || host.isBlank()) return true;
        String h = host.trim().toLowerCase();
        if ("localhost".equals(h) || h.endsWith(".local")) return true;
        if (h.equals("127.0.0.1") || h.equals("::1")) return true;
        if (h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.")) return true;
        if (h.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) return true;
        try {
            InetAddress a = InetAddress.getByName(h);
            return a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    public record FetchedPage(String url, String host, int status, String text) {}
}
