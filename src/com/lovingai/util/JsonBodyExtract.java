package com.lovingai.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 JSON 请求体中做最小提取（无外部 JSON 库）。仅用于 /api/chat 等受控端点。
 */
public final class JsonBodyExtract {

    private JsonBodyExtract() {}

    public static String getString(String json, String key) {
        if (json == null || key == null || key.isBlank()) {
            return "";
        }
        String k = "\"" + key + "\"";
        int at = json.indexOf(k);
        if (at < 0) {
            return "";
        }
        int colon = json.indexOf(':', at + k.length());
        if (colon < 0) {
            return "";
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return "";
        }
        return readJsonStringContent(json, i);
    }

    /** 顶层 {@code "key": true/false} 的布尔字面量；非布尔或缺失时返回空串。 */
    public static String getBooleanLiteral(String json, String key) {
        if (json == null || key == null || key.isBlank()) {
            return "";
        }
        String k = "\"" + key + "\"";
        int at = json.indexOf(k);
        if (at < 0) {
            return "";
        }
        int colon = json.indexOf(':', at + k.length());
        if (colon < 0) {
            return "";
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i + 4 <= json.length() && json.startsWith("true", i)) {
            return "true";
        }
        if (i + 5 <= json.length() && json.startsWith("false", i)) {
            return "false";
        }
        return "";
    }

    /**
     * 顶层 {@code "key": { ... }} 的对象字面量子串（不含外层引号）；非对象或缺失时返回空串。
     * 与 {@link LlmStructuredJson#getStringByPath(String, String...)} 搭配用于嵌套字段。
     */
    public static String getObjectLiteral(String json, String key) {
        if (json == null || key == null || key.isBlank()) {
            return "";
        }
        String k = "\"" + key + "\"";
        int at = json.indexOf(k);
        if (at < 0) {
            return "";
        }
        int colon = json.indexOf(':', at + k.length());
        if (colon < 0) {
            return "";
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '{') {
            return "";
        }
        int end = LlmStructuredJson.endIndexExclusiveBalanced(json, i, '{', '}');
        if (end <= i) {
            return LlmStructuredJson.tryCompleteUnbalanced(json.substring(i));
        }
        return json.substring(i, end);
    }

    /**
     * 模型输出可能带废话/Markdown/残缺 JSON；先归一化再取 {@link #getString(String, String)}。
     */
    public static String getStringFromLooseObject(String raw, String key) {
        return LlmStructuredJson.getTopLevelString(raw, key);
    }

    /** 解析 {@code "key": [ "a", "b" ]} 中的顶层字符串元素（不支持嵌套结构）。 */
    public static List<String> getStringArray(String json, String key) {
        List<String> out = new ArrayList<>();
        if (json == null || key == null || key.isBlank()) {
            return out;
        }
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) {
            return out;
        }
        int lb = json.indexOf('[', keyIdx);
        if (lb < 0) {
            return out;
        }
        int endBracket = findMatchingBracket(json, lb);
        if (endBracket < 0) {
            return out;
        }
        int i = lb + 1;
        while (i < endBracket) {
            while (i < endBracket && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= endBracket) {
                break;
            }
            if (json.charAt(i) == '"') {
                out.add(readJsonStringContent(json, i));
                i = skipJsonString(json, i);
                while (i < endBracket && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                if (i < endBracket && json.charAt(i) == ',') {
                    i++;
                }
            } else {
                i++;
            }
        }
        return out;
    }

    private static int findMatchingBracket(String json, int openBracket) {
        int depth = 0;
        for (int i = openBracket; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 从 {@code data:image/...;base64,XXXX} 或纯 base64 取载荷。 */
    public static String stripDataUrlBase64(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.startsWith("data:")) {
            int comma = t.indexOf(',');
            if (comma >= 0) {
                return t.substring(comma + 1).trim();
            }
        }
        return t;
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

    private static int skipJsonString(String json, int openQuote) {
        if (openQuote < 0 || openQuote >= json.length()) {
            return json.length();
        }
        for (int i = openQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                i++;
                continue;
            }
            if (c == '"') {
                return i + 1;
            }
        }
        return json.length();
    }
}
