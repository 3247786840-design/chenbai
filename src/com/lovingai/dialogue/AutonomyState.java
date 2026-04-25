package com.lovingai.dialogue;

/** 会话自治节奏（主动追问 / 自解 / 聆听）——与 {@link AutonomyFsm} 配合使用。 */
public enum AutonomyState {
    LISTENING,
    REFLECTING,
    ASKING,
    SELF_SOLVING
}
