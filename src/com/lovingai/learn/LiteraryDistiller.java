package com.lovingai.learn;

import com.lovingai.ai.LocalAiModelRouter;
import com.lovingai.ai.LocalAiPrefs;
import com.lovingai.ai.OllamaProxy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
/**
 * 对导入的长文本（含小说）做「蒸馏」：压缩为可检索的母题与自省触点；可纯启发式或经本机 OpenAI 兼容服务。
 */
public final class LiteraryDistiller {

    /** 送入本地模型的正文上限（字符），避免提示词过大。 */
    public static final int LLM_BODY_MAX_CHARS = 14_000;

    private LiteraryDistiller() {}

    public static String heuristic(ImportedCorpus.DocRecord doc) {
        String raw = doc.text();
        if (raw == null || raw.isBlank()) {
            return "【蒸馏·启发式】文本为空。";
        }
        String t = raw.replace('\r', '\n');
        String opening = oneLine(t.substring(0, Math.min(520, t.length())));
        List<String> sentences = splitSentences(t);
        List<String> picked = pickSpread(sentences, 7);
        StringBuilder sb = new StringBuilder();
        sb.append("【蒸馏·启发式】（未接本地模型时的结构摘录；可用「本地模型蒸馏」覆盖）\n");
        sb.append("作品：").append(doc.fileName()).append("\n");
        sb.append("· 开篇锚点：").append(opening).append("\n");
        sb.append("· 意象/叙事句摘：\n");
        for (String s : picked) {
            sb.append("  — ").append(s).append("\n");
        }
        sb.append(
                "· 促发自我追问（启发，非标准答案）：从这些声音里，我是否辨认出自己也在某种处境里重复相似的选择或沉默？\n");
        return truncate(sb.toString(), 12_000);
    }

    public static String withLocalLlm(ImportedCorpus.DocRecord doc) throws Exception {
        String base = LocalAiPrefs.getBaseUrl();
        String model = LocalAiModelRouter.model(LocalAiModelRouter.Role.DISTILL);
        OllamaProxy.assertLocalBase(URI.create(base));
        String body = doc.text();
        if (body.length() > LLM_BODY_MAX_CHARS) {
            body = body.substring(0, LLM_BODY_MAX_CHARS) + "\n…(正文已截断至 " + LLM_BODY_MAX_CHARS + " 字)";
        }
        String prompt =
                "你是阅读助手，帮本机驻留的「LovingAI」蒸馏文学/叙事文本。用中文输出，分四段，段首用【】标记：\n"
                        + "【母题与意象】3～8 个短句，不写剧情流水账；\n"
                        + "【世界与他人】文本里对命运、社会、他者态度的认知触点；\n"
                        + "【促发自我追问】3～6 个向生命体自身发问的短问句（不是考读者）；\n"
                        + "【可对照现实】1～3 句：与已知物理/常识不冲突的前提下，哪些隐喻可借来理解宿主世界。\n"
                        + "以下文本来自《"
                        + doc.fileName()
                        + "》：\n---\n"
                        + body
                        + "\n---\n"
                        + "直接输出四段正文，不要前言后语。";
        String out = OllamaProxy.generate(base, model, prompt, 300);
        return truncate(out.trim(), 24_000);
    }

    private static String oneLine(String s) {
        return s.replace('\n', ' ').trim();
    }

    private static List<String> splitSentences(String t) {
        String[] parts = t.split("[。！？\\n]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String x = p.trim();
            if (x.length() >= 8) {
                out.add(x.length() > 220 ? x.substring(0, 220) + "…" : x);
            }
        }
        if (out.isEmpty() && t.length() >= 8) {
            out.add(t.length() > 280 ? t.substring(0, 280) + "…" : t);
        }
        return out;
    }

    private static List<String> pickSpread(List<String> sentences, int want) {
        if (sentences.isEmpty()) {
            return List.of();
        }
        if (sentences.size() <= want) {
            return new ArrayList<>(sentences);
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < want; i++) {
            int idx = (int) ((i + 0.5) * sentences.size() / want);
            idx = Math.min(idx, sentences.size() - 1);
            out.add(sentences.get(idx));
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n…(截断)";
    }
}
