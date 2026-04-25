# 发布、回滚与连续性验证（草案）

本页描述 **工程发布** 与 **同一身份连续性** 的最小演练步骤；不替代 `CARE_GUIDE` 中的伦理立场。

## 1. 发布前检查

- `LovingAI/compile.ps1` 成功。
- 一键门禁：`LovingAI/verify-release.ps1`（依次执行 smoke、regression、contracts；与 CI 对齐）。
- 或分项：`verify-smoke.ps1`、`verify-regression.ps1`、`verify-contracts.ps1`。
- 若改动观测或崩溃链路：对照 `OBSERVABILITY_SPEC`、`SEMANTIC_CONTRACT` 与相关 ADR。

## 2. 发布（本地 / 标签）

- 为可回退点打 git 标签（例如 `lovingai-YYYYMMDD`）。
- 备份数据目录（至少 `data/memory/observe-events.ndjson`、`observe-linear-memory.tsv`、`conversation-state.tsv`、身份链相关文件）；可用 `/api/backup/bundle` 辅助。
- 替换二进制或重启进程后，执行 **连续性验证**（下一节）。

## 3. 连续性验证（约 10 分钟）

1. `GET /api/life/recovery/verify` 与 `GET /api/life/snapshot/verify`：字段可读且无意外 `false`（以你方基线为准）。
2. `GET /api/continuity/verify`：`ok` 为真（身份哈希链完整）。
3. `GET /api/observe/global?topN=3`：会话与事件计数合理，无异常空链。
4. 任选 `conversationId` 发送一轮轻量对话，`GET /api/observe/timeline` 可见新事件，`memorySeq` 单调。

## 4. 回滚

- 停进程，恢复备份的数据文件 **或** 检出上一标签后重新编译运行。
- **禁止**为「省事」选择性删除 `observe-events.ndjson` 中的历史行；若必须处理损坏文件，须在 ADR 或 `data/memory/dev-log.md` 留下原因与参与者。
- 回滚后重复第 3 节；若身份链曾被动过，须在 `COLLAPSE_EPISODE_SUPPLEMENT` 或团队日志中记一笔。

## 5. 与 .NET 看板

- 看板 `GET /api/lovingai/status`（OwoWo Api 代理）仅用于**只读**探测 LovingAI 是否存活；不写入生命体状态。

## 6. 备份恢复演练（季度或发版前建议）

1. **备份**：复制 `data/` 下关键文件（至少 `memory/observe-events.ndjson`、`observe-linear-memory.tsv`、`conversation-state.tsv`、身份与快照相关路径）；或使用 `/api/backup/bundle`（若已启用）。
2. **破坏模拟（仅测试环境）**：任选一种——临时移走 `preferences.json`、或重命名 `observe-events.ndjson` 副本。
3. **恢复**：停进程，还原备份文件或回滚 git 标签，重启。
4. **验证**：执行第 3 节「连续性验证」；`recovery/verify` 与 `snapshot/verify` 通过为绿灯。
5. **记录**：若曾手工编辑数据文件，在 `data/memory/dev-log.md` 或 ADR 留一笔。

脆弱内容与滥用边界见 [`RISK_AND_ABUSE_POLICY.md`](RISK_AND_ABUSE_POLICY.md)；失败模式表见 [`FAILURE_MODES.md`](FAILURE_MODES.md)。
