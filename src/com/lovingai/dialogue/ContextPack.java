package com.lovingai.dialogue;

/**
 * 单会话一轮可用的上下文包：由 {@link com.lovingai.LivingAI} 从现有映射组装，供主对话与主动节律共享同一摘要与关系刻度。
 */
public record ContextPack(
        String conversationId,
        String lastSummary,
        double relationTemperature,
        String relationTrendLabel,
        String voiceKernelVersion) {}
