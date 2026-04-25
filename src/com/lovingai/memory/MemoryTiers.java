package com.lovingai.memory;

import com.lovingai.LivingAI;

/**
 * 记忆分层门面：工作（短上下文）/ 会话摘要 / 长期身份链提示 —— 统一入口，避免业务层直接摸散落的 Map。
 */
public final class MemoryTiers {

    private MemoryTiers() {}

    /** 会话级摘要（用于 ContextPack、主链）。 */
    public static String sessionSummary(String conversationId) {
        return LivingAI.memoryTierSessionSummary(conversationId);
    }

    /** 工作记忆：最近短句队列拼成一行预览。 */
    public static String workingPreview(String conversationId) {
        return LivingAI.memoryTierWorkingPreview(conversationId);
    }

    /** 长期：身份连续链尾部提示（失败返回空串）。 */
    public static String longIdentityHint() {
        try {
            return IdentityContinuity.continuitySnapshotHint();
        } catch (Exception e) {
            return "";
        }
    }
}
