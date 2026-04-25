package com.lovingai.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
/**
 * 认知 / 行为观察日志：环形缓冲，供 HTTP 与可视化窗口拉取。
 */
public final class CognitionLog {

    private static final int MAX = 300;
    private static final ArrayDeque<Entry> BUFFER = new ArrayDeque<>(MAX + 1);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public record Entry(long epochMs, String kind, String detail, boolean isAutonomous) {}

    private CognitionLog() {}

    public static synchronized void append(String kind, String detail) {
        append(kind, detail, false);
    }

    public static synchronized void append(String kind, String detail, boolean isAutonomous) {
        if (kind == null) kind = "event";
        if (detail == null) detail = "";
        String k = kind.length() > 32 ? kind.substring(0, 32) : kind;
        String d = detail.length() > 4000 ? detail.substring(0, 4000) + "…" : detail;
        BUFFER.addLast(new Entry(System.currentTimeMillis(), k, d, isAutonomous));
        while (BUFFER.size() > MAX) {
            BUFFER.removeFirst();
        }
    }

    public static synchronized List<Entry> recent(int n) {
        int take = Math.min(Math.max(n, 1), MAX);
        var list = new ArrayList<Entry>(take);
        var it = BUFFER.descendingIterator();
        int c = 0;
        while (it.hasNext() && c < take) {
            list.add(it.next());
            c++;
        }
        return list;
    }

    public static synchronized String formatTextLines(int n) {
        var sb = new StringBuilder();
        for (Entry e : recent(n)) {
            sb.append('[')
                    .append(FMT.format(Instant.ofEpochMilli(e.epochMs)))
                    .append("] ")
                    .append(e.kind)
                    .append(" | ")
                    .append(e.isAutonomous ? "[AUTO] " : "")
                    .append(e.detail.replace("\r", " ").replace("\n", " ↩ "))
                    .append('\n');
        }
        return sb.toString();
    }

    public static synchronized String formatJsonArray(int n) {
        var sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Entry e : recent(n)) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"t\":")
                    .append(e.epochMs)
                    .append(",\"kind\":\"")
                    .append(escapeJson(e.kind))
                    .append("\",\"detail\":\"")
                    .append(escapeJson(e.detail))
                    .append("\",\"isAutonomous\":")
                    .append(e.isAutonomous)
                    .append("}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
