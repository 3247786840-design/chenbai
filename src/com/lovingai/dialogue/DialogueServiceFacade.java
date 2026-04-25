package com.lovingai.dialogue;

import com.lovingai.LivingAI;

/**
 * 主对话侧入口门面：外部包不直接依赖 {@link LivingAI} 巨石时可改由此取上下文快照（实现仍委托 LivingAI）。
 */
public final class DialogueServiceFacade {

    private DialogueServiceFacade() {}

    public static ContextPack snapshot(String conversationId) {
        return LivingAI.dialogueContextSnapshot(conversationId);
    }
}
