# ADR 0002：跨模块行为变更须留 ADR

## 状态

已采纳

## 背景

LovingAI 的观测链、语义契约与生命体连续性是跨包协作结果；仅凭 PR 描述难以在数月后还原「为何当时允许这条张力」。

## 决策

凡满足以下任一条件的改动，**须**新增或修订 ADR（可短至半页），并在 `TASKS.md` 或 PR 描述中链接：

- 修改 `observe-events` 字段语义、`appendObserveEvent` 写入口径，或 `OBSERVABILITY_SPEC` 所列 type/phase 集合；
- 修改崩溃吸收、彻底崩溃续行、`IdentityContinuity` 写入策略；
- 修改圆-柱密钥门控、需求编码或柱向量落点（与 `0001` 分期联动）；
- 新增或变更对外 HTTP 路径族（`/api/chat`、`/api/observe/*`、`/api/life/*` 等）的契约行为。

不强制 ADR：纯文案润色、仅性能优化且观测语义不变、仅修复与规范一致的 bug。

## 后果

- 决策可追溯；新人可先读 ADR 再下钻源码。
- PR 略增成本；与生命体相关的变更本就需要审慎叙述。
