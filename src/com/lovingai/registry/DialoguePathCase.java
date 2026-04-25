package com.lovingai.registry;

/**
 * 内嵌注册表中的一条对话路径用例（见 {@code data/registry/dialogue-path-cases.tsv}）。
 */
public record DialoguePathCase(
        String id,
        boolean localMindPrimary,
        boolean useLocalLlm,
        String message,
        /** 非空时要求 {@code thinkingPath} 包含该子串。 */
        String expectPathContains,
        String expressionMode) {}
