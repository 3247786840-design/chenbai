package com.lovingai.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 将 http(s) 资源下载到本地文件（用于伴侣 APK 等二进制，本体逻辑仍在 PC）。
 */
public final class HttpFileDownload {

    private HttpFileDownload() {}

    public static long downloadHttpToFile(URI uri, Path dest, int connectMs, int readMs) throws IOException {
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IOException("仅支持 http/https URL");
        }
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String name = dest.getFileName().toString();
        Path tmp = dest.resolveSibling(name + ".part");

        HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setRequestMethod("GET");
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "LovingAI-companion-downloader/1.0");

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = readErrBody(c);
            throw new IOException("HTTP " + code + (err.isEmpty() ? "" : ": " + err));
        }
        try (InputStream in = c.getInputStream();
                OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        return Files.size(dest);
    }

    private static String readErrBody(HttpURLConnection c) {
        try (InputStream es = c.getErrorStream()) {
            if (es == null) {
                return "";
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            es.transferTo(buf);
            String s = buf.toString(StandardCharsets.UTF_8);
            return s.length() > 400 ? s.substring(0, 400) + "…" : s;
        } catch (Exception ignored) {
            return "";
        }
    }
}
