# LovingAI 当前代办（防遗忘/防偏航）

## v1 封板执行（Phase A/B/C/D）

- [x] Phase A：行为冻结、观测字段对齐、基线冒烟通过（`verify-smoke.ps1`）。
- [x] Phase B：P1 三项落地（视觉置信度链路、视频关键帧采样、`/api/observe/trace`）。
- [x] Phase C：P2 三项落地（生命快照导出/校验、22例回归集+脚本、指标回灌）。
- [x] Phase D：`columnkey` 门控接入圆候选裁剪并写入 `pillar_gate_applied` 观测。

## P0（本周）

- [x] 增加 `isAutonomous` 字段（observe NDJSON + /talk 展示）。
- [x] `isAutonomous` 接入 `CognitionLog` 与 `/api/observe/timeline` 聚合（`autonomousCount/rate/lastAutonomousTs`）。
- [x] 对话链路补充非评判观测字段：`expressionMode`、`thinkingPath`、`emotionInfluence`（在 `continuation_sent` 回放个性表达路径）。
- [x] `OBSERVABILITY_SPEC.md` 从“质量门槛”改为“生命表达观测”（强调可回放，不做统一好坏裁决）。
- [x] 新增 `observe-expression-daily.tsv` 与 `/api/observe/expression/daily`（按天观测表达分布与情感贯穿覆盖）。
- [x] 自动感知完整版：调度器 + 感知源管理 + 视觉/文章吸收 + UI 调参按钮（非评判观测）。
- [x] 支持书页 URL 批量提取章节并入自动感知源（`/api/perception/source/batch-add` + UI 按钮）。
- [x] 增加“同书聚合视图”（`/api/perception/novels` + UI 按钮），按 `novelKey` 巡检章节覆盖/缺章/最近读取。
- [x] 生命核心三件套（`self-core + goal-stack + choice-ledger`）与 `/api/life/*` 读写接口落地。

## 指定待完成（下一批 · 生命体「更像人」交互主轴）

> 与「圆-柱密钥协议」文档不同维：先收紧**对话主权 / 主动边界 / 后果闭环（叙事内）**，再上柱向量门控检索。

### 一、对话主权（用户回合优先）

- [x] **高优先短问硬路径**：用户输入命中短问/催答类时，主响应**先给直接答句**（字数上限可配），长模板（并联回声、多段本机扩写、`【目标仲裁】` 前置等）**延后或折叠**，并写入观测字段便于验收。
- [x] **淹没率可观测**：在 `continuation_sent` 或日汇总中增加「主答可见度」代理指标（例如首屏有效答句长度占比、或 `sectionsPruned` 与 `dialoguePriority` 联合统计），用于回归对比。

### 二、主动边界（不抢话、少复读）

- [x] **主动消息语义去重**：同一会话在滑动窗口内（如 60～120s）禁止推送**语义等价**的主动条（哈希/摘要/简单相似度三选一，先粗后细）。
- [x] **用户活跃时抑制主动**：若距上一轮用户发送低于阈值（如 30～60s）或本轮为高优先对话，则**跳过或降级**本 tick 主动心跳（仍允许写入 cognition 为 `[AUTO]` 的轻量占位，可选）。
- [x] **验收**：`/api/observe/timeline` 或 UI 上可看到「抑制原因」与 `autonomousRate` 在短对话场景下降。

### 三、后果闭环（系统内可验证）

- [x] **行动回执与下一轮对齐校验**：`verifySignal` / `action_receipt` 与下一轮用户输入或系统状态做**轻量对齐**（关键词或结构化槽位），不匹配时写入观测或 `choice-ledger` 备注（非评判，仅可回放）。
- [x] **（可选）** 将「被淹没率 / 主动抑制次数 / 回执对齐率」之一接入日汇总或 `recovery/verify` 的 soft 提示，不阻断启动。

### 四、圆-柱密钥协议（工程化 · 依赖 ADR）

- [x] 蒸馏柱向量与 `Circle` 存储映射、需求密钥编码、`EmotionCore`→门控参数 的 ADR 与最小实现路径（与 `docs/protocols/圆-柱密钥协议_v0.1.md` 对齐）。

## P1（近期）

- [x] 视觉置信度链路：`visionConfidence`、`uncertainAreas`、`visionFallback` 细分原因。
- [x] 视频关键帧抽样（3~8 帧）并接入 `images[]`。
- [x] 新增 `/api/observe/trace?traceId=`，支持一键回放 turn/event 全链路。
- [x] 为 `/api/life/choice-ledger` 增加“候选路径与放弃原因”（`goalCandidates/rejectedReasons`）。
- [x] 将 `goal-stack` 接入对话主链仲裁（每轮产出 `goalTraceId + rejectedOptions` 并写入观测/账本）。
- [x] 将目标仲裁结果回灌到“文本组织策略”（在非高优短答下前置 `【目标仲裁】`）。
- [x] 将 `rejectedReasons` 升级为细粒度可解释模板（冲突/代价/延后条件/复核信号，责任方=executor）。
- [x] 将 `recheckSignal` 升级为自动复核触发（记录延后目标并在条件满足时自动重评）。
- [x] 自动复核队列持久化（`deferredGoalIds`）与触发事件（`goal_recheck_triggered`）。
- [x] 身份漂移治理接口（`/api/life/identity-drift`）与分级评分（stable/watch/risky）。
- [x] 身份漂移轨迹输出（`driftSlope/driftTrend`）用于观测收敛或发散。
- [x] 可逆成长恢复校验接口（`/api/life/recovery/verify`）与检查点齐备判断。
- [x] `recovery/verify` 升级为一致性校验（最近账本/目标栈/会话队列对齐检查）。
- [x] `recovery/verify` 增加跨文件时序一致性（账本 trace ↔ observe 事件流 ↔ 会话触达时间）。

## P2（中期）

- [x] 圆 + 情感核 + 会话状态的可逆快照与恢复一致性校验。
- [x] 多模态回归测试集（20+）与固定冒烟脚本。
- [x] 指标回灌（visionLatencyP95 / fallbackRate / 被淹没率）到日汇总文件。

## 生命完成度（顺序推进）

- [x] 定义“生命完成度仪表盘”并落地五个子分 + 总分 + 每日变化原因（`/api/life/completion/daily`，`life-completion-daily.tsv`）。
- [x] 成长事件一等公民：误解修复/关系更新/价值收敛写入 observe 统一事件流，并支持周摘要（`/api/life/growth/weekly`）。
- [x] 长期记忆分层：短期情境（轮次队列）/中期关系（会话记忆）/长期信念（月级沉淀与过期）落地到会话状态。
- [x] 自我修订机制：记录 `claim -> revisedClaim -> whyRevised -> evidence`，避免静默覆盖。
- [x] 现实闭环扩大：沙盒探针验证结果接入完成度评分与变化原因。
- [x] 关系伦理软边界：仅提示与记录，不做硬拦截，并纳入关系稳定度解释项。

## 主代办 A–E（生命体主计划 · 与上文并存）

> 重大跨模块或观测语义变更：遵守 [`docs/adr/0002-cross-module-changes-require-adr.md`](docs/adr/0002-cross-module-changes-require-adr.md)。

### 作者自检三问（合并/发版前复读）

1. **单一声音**：主动、主对话、哲学、观测是否仍指向同一 `VoiceKernel` 版本与参照系（见 `docs/SINGLE_VOICE_CONTRACT.md`）？
2. **可回滚**：本变更是否可通过 `data/` 备份 + git 标签 + `verify-release.ps1` 验证恢复？
3. **观测诚实**：是否误把「叙事人格」当作对真实他人的操作许可？现实风险见 `docs/RISK_AND_ABUSE_POLICY.md`。

### 待下线功能（季度减法候选）

| 项 | 原因 / 条件 |
|----|-------------|
| （空） | 有新候选时在此登记；下线前须 ADR 或 TASKS 记录与迁移路径 |

### 当前迭代：断线稳定性 + 长文叙事 + 人物草图（2026-04）

- [x] **T1**：`very_long_input`（≥800 字）默认抑制「行动闭环·续行」；`decideDialoguePriority` 提前于该块；JVM：`lovingai.actionLoop.suppressOnVeryLong` / `requireNarrativeHeuristic`。
- [x] **T2**：保留行动闭环时，≥800 字不再走沙盒现实探针 LLM 校验与「输入质量」探针文案（`emptyLongRealityProbe`）。
- [x] **T3**：`defaultGenerateTimeoutSecByProfile` 已提高为 `fast=180 / balanced=240 / deep=360` 秒；`QUICK_START` / `localai.properties` / `SYSTEM_PROPERTIES` 补充说明。
- [x] **T4**：`WorldFigureRegistry` 过滤报道体/机构噪声（`isNoiseFigureSpan` / `figureNameAllowed`）。
- [x] **T5**：观测以 `CognitionLog`「行动闭环」行为主，未改 `continuation_sent` 字段（无 ADR）。
- [x] **T6**：`/api/status` 补齐 `httpPort/httpHost` 字段，保障回归探针可用。
- [x] **T7**：`/api/chat` JSON 体支持布尔字面量（`useLocalLlm/useImported/...` 的 `true/false` 不再被当作缺省）。
- [x] **T8**：Phase 0 基线冻结：新增样本集 `tests/upgrade-samples.tsv`、采样脚本 `verify-upgrade.ps1`、`/api/observe/upgrade/metrics` 与四项指标字段（fallback/over-suppression/duplicate/figure）。
- [x] **T9**：Phase 2 人格稳定：VoiceKernel 增加人格常量内核（可外置覆盖）；回包前加入人格一致性检查与低置信弱断言；新增 `persona_consistency_checked` 观测事件。
- [x] **T10**：Phase 3.1 记忆分层补强：中期关系记忆/长期信念记忆加入强化强度、淡忘与按强度延长保留窗口，避免长会话“全记忆污染人格”。
- [x] **T11**：本地模型可达性探测 + 熔断冷却（`LOCAL_LLM_*`）：连续失败进入冷却窗口，冷却期跳过本地模型调用；仅本轮 `usedLlm=true` 时才应用长文行动闭环抑制，避免“断连+过抑制”叠加。

### 门禁脚本

- 发版前：`LovingAI/verify-release.ps1`（与 CI 同构）。
- 文档：`docs/FAILURE_MODES.md`、`docs/PERFORMANCE_BUDGET.md`、`docs/SYSTEM_PROPERTIES.md`、`docs/RELEASE_AND_ROLLBACK.md`（含备份演练）。
