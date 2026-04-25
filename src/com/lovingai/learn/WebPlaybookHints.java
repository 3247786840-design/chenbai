package com.lovingai.learn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户句子里识别 http(s) 主机名，挂上已保存的「站点自然语言说明」，供对话上下文前置。
 */
public final class WebPlaybookHints {

    private static final Pattern URL_HOST =
            Pattern.compile("https?://([^/#\\s]+)", Pattern.CASE_INSENSITIVE);

    private WebPlaybookHints() {}

    public static List<String> snippetsForMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        Matcher m = URL_HOST.matcher(userMessage);
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        while (m.find()) {
            String h = WebFetchAllowlist.normalizeHost(m.group(1));
            if (h.isEmpty() || !seen.add(h)) {
                continue;
            }
            String pb = SitePlaybookStore.INSTANCE.loadOrEmpty(h);
            if (pb.isBlank()) {
                continue;
            }
            int idx = pb.indexOf("【自然语言说明】");
            if (idx >= 0) {
                pb = pb.substring(idx + "【自然语言说明】".length()).trim();
            }
            if (pb.isBlank()) {
                continue;
            }
            String one = pb.replace('\n', ' ');
            if (one.length() > 900) {
                one = one.substring(0, 900) + "…";
            }
            out.add("【站点说明·" + h + "】" + one);
        }
        return out;
    }
}
