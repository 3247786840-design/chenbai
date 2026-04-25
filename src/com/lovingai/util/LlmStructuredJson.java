package com.lovingai.util;

/**
 * 从本地小模型（如 4B）的文本输出中取出可解析的 JSON 对象/数组：剥离废话前缀、Markdown 围栏，
 * 按括号平衡截取首个 {@code {...}} / {@code [...]}，并对常见“截断”做保守补全。
 *
 * <p>典型期望形态示例：
 *
 * <pre>
 * {"tool_name": "search", "parameters": {"query": "weather"}}
 * </pre>
 */
public final class LlmStructuredJson {

    private LlmStructuredJson() {}

    /**
     * 归一化为单个 JSON 对象字符串；无法得到可信对象时返回空串（调用方应回退或重试）。
     */
    public static String normalizeToObject(String raw) {
        return normalizeToValue(raw, '{', '}');
    }

    /** 归一化为单个 JSON 数组字符串。 */
    public static String normalizeToArray(String raw) {
        return normalizeToValue(raw, '[', ']');
    }

    private static String normalizeToValue(String raw, char open, char close) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = unwrapMarkdownFence(raw.trim());
        s = stripLeadingNoise(s);
        int start = s.indexOf(open);
        if (start < 0) {
            return "";
        }
        int end = endIndexExclusiveBalanced(s, start, open, close);
        if (end > start) {
            String slice = s.substring(start, end);
            return looksStructurallyClosed(slice) ? slice : tryCompleteUnbalanced(slice);
        }
        String tail = s.substring(start);
        return tryCompleteUnbalanced(tail);
    }

    /**
     * 从宽松模型输出中取顶层字符串字段（先 {@link #normalizeToObject}，再按 key 解析）。
     */
    public static String getTopLevelString(String raw, String key) {
        String obj = normalizeToObject(raw);
        if (obj.isEmpty()) {
            return "";
        }
        return JsonBodyExtract.getString(obj, key);
    }

    /**
     * 取嵌套路径上的字符串：每一层（除最后一层）的值须为 JSON 对象。例如 {@code ("parameters", "query")}。
     */
    public static String getStringByPath(String raw, String... keys) {
        if (keys == null || keys.length == 0) {
            return "";
        }
        String j = normalizeToObject(raw);
        if (j.isEmpty()) {
            return "";
        }
        for (int i = 0; i < keys.length - 1; i++) {
            j = JsonBodyExtract.getObjectLiteral(j, keys[i]);
            if (j.isEmpty()) {
                return "";
            }
        }
        return JsonBodyExtract.getString(j, keys[keys.length - 1]);
    }

    static String unwrapMarkdownFence(String raw) {
        String t = raw.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int nl = t.indexOf('\n');
        if (nl < 0) {
            return t;
        }
        t = t.substring(nl + 1);
        int fence = t.lastIndexOf("```");
        if (fence >= 0) {
            t = t.substring(0, fence);
        }
        return t.trim();
    }

    /** 去掉常见中文前言，定位第一个 {@code {}[]}。 */
    static String stripLeadingNoise(String s) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '{' || ch == '[') {
                return s.substring(i);
            }
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            int nextBrace = indexOfUnquoted(s, '{', i);
            int nextBracket = indexOfUnquoted(s, '[', i);
            int pick = -1;
            if (nextBrace >= 0 && nextBracket >= 0) {
                pick = Math.min(nextBrace, nextBracket);
            } else {
                pick = Math.max(nextBrace, nextBracket);
            }
            if (pick >= 0) {
                return s.substring(pick);
            }
            return s;
        }
        return s;
    }

    private static int indexOfUnquoted(String s, char needle, int from) {
        boolean inString = false;
        boolean esc = false;
        for (int i = from; i < s.length(); i++) {
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
            } else if (ch == needle) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从 {@code openIdx} 处的开括号起，返回与之平衡的第一个结束下标（不含）；失败返回 -1。
     */
    static int endIndexExclusiveBalanced(String s, int openIdx, char open, char close) {
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

    /** 与 {@link com.lovingai.LivingAI#isLikelyValidJsonValue} 同构的轻量校验（无嵌套库）。 */
    static boolean looksStructurallyClosed(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String s = text.trim();
        char first = s.charAt(0);
        if (first != '{' && first != '[' && first != '"' && first != 't' && first != 'f' && first != 'n'
                && first != '-'
                && (first < '0' || first > '9')) {
            return false;
        }
        boolean inString = false;
        boolean escaping = false;
        int obj = 0;
        int arr = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (ch == '\\') {
                    escaping = true;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                obj++;
            } else if (ch == '}') {
                obj--;
            } else if (ch == '[') {
                arr++;
            } else if (ch == ']') {
                arr--;
            }
            if (obj < 0 || arr < 0) {
                return false;
            }
        }
        return !inString && obj == 0 && arr == 0;
    }

    /**
     * 在字符串未闭合或括号未闭合时，保守追加 {@code "}、{@code ]}、{@code }}，使多数“写到一半”的输出仍可被解析。
     */
    static String tryCompleteUnbalanced(String partial) {
        if (partial == null || partial.isBlank()) {
            return "";
        }
        String s = partial.trim();
        if (looksStructurallyClosed(s)) {
            return s;
        }
        boolean inString = false;
        boolean esc = false;
        int obj = 0;
        int arr = 0;
        for (int i = 0; i < s.length(); i++) {
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
            } else if (ch == '{') {
                obj++;
            } else if (ch == '}') {
                obj--;
            } else if (ch == '[') {
                arr++;
            } else if (ch == ']') {
                arr--;
            }
        }
        StringBuilder b = new StringBuilder(s);
        if (inString) {
            b.append('"');
        }
        while (arr > 0) {
            b.append(']');
            arr--;
        }
        while (obj > 0) {
            b.append('}');
            obj--;
        }
        String fixed = b.toString();
        return looksStructurallyClosed(fixed) ? fixed : "";
    }
}
