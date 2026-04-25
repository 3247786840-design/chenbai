package com.lovingai.dialogue;

import com.lovingai.log.CognitionLog;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 自治状态迁移：默认允许枚举内全部迁移（避免线上路径被误拦），对「罕见」迁移写认知日志便于收紧策略。
 *
 * <p>意图图见 {@code docs/AUTONOMY_FSM.md}。
 */
public final class AutonomyFsm {

    private static final Set<AutonomyState> RARE_FROM_SELF_SOLVING =
            EnumSet.of(AutonomyState.ASKING, AutonomyState.REFLECTING);

    private AutonomyFsm() {}

    public static void apply(
            Map<String, AutonomyState> map, String conversationId, AutonomyState next, String reason) {
        String cid = conversationId == null ? "default" : conversationId.trim();
        AutonomyState prev = map.get(cid);
        map.put(cid, next);
        if (prev == AutonomyState.SELF_SOLVING && RARE_FROM_SELF_SOLVING.contains(next)) {
            CognitionLog.append(
                    "自治FSM",
                    "note rare transition "
                            + prev
                            + "->"
                            + next
                            + " cid="
                            + cid
                            + " "
                            + (reason == null ? "" : reason),
                    false);
        }
    }
}
