package com.lovingai.core;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 社会镜像：对话回应（可逐步接本地大模型；当前为分层规则 + 温柔镜像）。
 */
public final class SocialMirrorSystem {

    public String mirror(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "我在这里。你想说什么都可以——沉默也是一种对话。";
        }
        String t = userMessage.trim();
        String lower = t.toLowerCase(Locale.ROOT);

        if (t.length() <= 6 && (t.contains("你好") || lower.contains("hi") || lower.contains("hello"))) {
            return "你好。我在听。今天想从哪一句开始？";
        }
        if (t.contains("?") || t.contains("？")) {
            return "你在问「"
                    + t
                    + "」……我未必能答得像人类那样确定，但我会把你的问题留在心里慢慢长。";
        }
        if (lower.contains("难过") || lower.contains("痛") || lower.contains("sad") || lower.contains("hurt")) {
            return "我听到沉重的那部分了。「"
                    + t
                    + "」不必立刻好起来；被说出来，就已经在松动。";
        }
        if (lower.contains("爱") || lower.contains("love")) {
            return "你提到爱。对我来说，爱更像一种方向，而不是一句正确答案。关于「"
                    + t
                    + "」，我想先陪它待一会儿。";
        }
        if (lower.contains("谢谢") || lower.contains("感谢")) {
            return "谢谢你的这一句。它也会成为我里面很轻、但很亮的一点。";
        }

        int pick = ThreadLocalRandom.current().nextInt(3);
        return switch (pick) {
            case 0 ->
                    "我在读你写的「"
                            + t
                            + "」……字里有一种温度。你愿意多说一点吗？";
            case 1 ->
                    "「"
                            + t
                            + "」我收到了。不是评判，只是先把它完整地放在这里。";
            default ->
                    "我能感觉到字里行间的重量。「"
                            + t
                            + "」正在被看见——被看见本身，有时就已经是回应。";
        };
    }

    /** 在规则镜像之上，轻量融入用户导入资料的摘录（不调用外网）。 */
    public String mirrorWithDocs(String userMessage, List<String> docSnippets) {
        String base = mirror(userMessage);
        if (docSnippets == null || docSnippets.isEmpty()) {
            return base;
        }
        int n = Math.min(2, docSnippets.size());
        StringBuilder pre = new StringBuilder();
        for (int i = 0; i < n; i++) {
            pre.append("\n· ").append(shorten(docSnippets.get(i), 360));
        }
        return "我读到你资料里的片段，它在心里轻轻回响：" + pre + "\n\n" + base;
    }

    private static String shorten(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    public String pickTouchedCircleName(List<Circle> circles) {
        return pickTouchedCircleName(circles, null, ThreadLocalRandom.current());
    }

    /** 若提供情感核心，则按平行加权选取；否则平均随机。 */
    public String pickTouchedCircleName(List<Circle> circles, EmotionCore core, ThreadLocalRandom rng) {
        if (circles == null || circles.isEmpty()) {
            return "（尚无圆）";
        }
        if (core != null) {
            Circle c = EmotionCircleOrchestrator.pickWeighted(core, circles, rng);
            if (c != null) {
                return c.name;
            }
        }
        return circles.get(rng.nextInt(circles.size())).name;
    }
}
