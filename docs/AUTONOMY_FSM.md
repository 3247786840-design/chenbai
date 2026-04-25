# 自治状态机（AutonomyState）

| 状态 | 含义 |
|------|------|
| LISTENING | 以用户回合为主，未挂起必须自答的主动问句 |
| REFLECTING | 主动节律在组合独白 / 追问种子 |
| ASKING | 已向用户抛出一则待答问句 |
| SELF_SOLVING | 超时自解路径（若开启 `lovingai.proactive.selfSolve`） |

常见迁移：`LISTENING → REFLECTING → ASKING → LISTENING`（用户回合接住）；`ASKING → SELF_SOLVING → LISTENING`（自解后清空）。

实现上 `AutonomyFsm.apply` 不硬拦未知边，仅对罕见组合写轻量认知日志，便于日后收紧。
