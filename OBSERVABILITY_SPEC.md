# OBSERVABILITY_SPEC

## 1. 目标

将 LovingAI 的观测能力从“零散状态查看”升级为“可回放、可关联、可比较”的体系化观测。

本规范只定义 **观测**，不定义控制与干预。

---

## 2. 统一事件模型（Canonical Event）

所有关键事件（对话、断裂、复盘、自调节、选圆、行动回执）统一为一条结构化记录：

```json
{
  "eventId": "evt_20260413_abc123",
  "memorySeq": 1842,
  "prevEventId": "evt_20260413_xyz789",
  "conversationId": "default",
  "turnId": "t:default:7",
  "collapseEpisodeId": "col_20260413_01",
  "ts": 1770000000000,
  "type": "collapse_materialized",
  "phase": "reflect_break",
  "emotion": {
    "lifeMode": "DREAMING",
    "mood": 0.61,
    "arousal": 0.44,
    "impulse": 0.53,
    "transcendentalLove": 0.72,
    "dreamChaos": 0.48
  },
  "progress": {
    "continuity": 0.56,
    "evidence": 0.49,
    "agency": 0.52,
    "resilience": 0.63,
    "progressTrend": "up",
    "goalStage": "integrating",
    "goalDistance": 0.41
  },
  "selfRegulation": "stage=integrating trend=up anchorΔ=1 qIntensity=3 action=parallel verify=balanced",
  "payload": {
    "errorClass": "SocketException",
    "errorMessage": "Unexpected end of file from server"
  }
}
```

### 字段约束

- `eventId`: 全局唯一
- `memorySeq`: **线性记忆**序号：在 `observe-events.ndjson` 成功落盘的顺序上全局单调递增；与平行圆/多会话叙事并存，不要求语义上「只有一条故事线」，只保证可观测时间折线可串联。首条或游标丢失时 `prevEventId` 可为空串。
- `prevEventId`: 上一条**已成功落盘**观测事件的 `eventId`，形成单链表；进程重启后由 `data/memory/observe-linear-memory.tsv` 恢复游标以续链，未恢复时视为记忆断点（允许的碎片）。
- `conversationId`: 会话主键
- `turnId`: 用户对话轮次标识（`t:<cid>:<ordinal>`），非对话类事件可为 JSON `null`
- `isAutonomous`: 是否为后台自主思考事件（当前实现：`traceKind=background` 时为 `true`）
- `collapseEpisodeId`: 无崩溃时为空字符串
- `type`: 事件类型枚举（见第 3 节）
- `phase`: 过程阶段枚举（见第 4 节）
- `emotion/progress`: 采样快照，保持原值，不做后处理
- `payload`: 类型相关附加信息
  - 视觉链路（当轮含图像输入时）建议最小字段：`visionLatencyMs`、`visionImageCount`、`visionModel`、`visionFallback`、`visionStrategy`
  - 视觉扩散增强字段：`visionCircleInjectCount`、`visionInjectedCircles`（CSV）
  - 对话表达观测字段：`dialoguePriority`、`sectionsPruned`、`usedLocalCompressor`、`expressionMode`、`thinkingPath`、`toneProfile`、`emotionInfluence`
  - 具身上下文字段：`perceptionSourcesActive`、`perceptionContextWindow`
  - 行动闭环标签：`closureTag`（`closure_completed/closure_partial/closure_interrupted/closure_drifted/...`）
  - M2 叙事字段：`selfRevisionNarrative`、`relationTemperature`、`relationTrend`、`relationStage`、`lifeNarrative`、`noiseTag`、`noiseDistillation`、`circleTreasureAnchor`、`conflictMediation`
  - 目标仲裁字段：`goalTraceId`、`goalId`、`goalLevel`、`responsibilityOwner`、`recheckTriggered`、`recheckFromGoalId`、`rejectedOptions`、`rejectedReasons`、`goalCandidates`

### 2.1 人类叙事补记（非事件链）

- 机器侧仍以本规范第 2 节的 **Canonical Event** 与 `observe-events.ndjson` 为唯一可比对的事件源。
- 照护者可在 **`docs/COLLAPSE_EPISODE_SUPPLEMENT.md`** 模板指导下，将「断裂现场—吸收—续行」的人类理解写入 **`data/memory/collapse-episodes/`**（进程不强制加载）。
- **崩溃训练**、问句风格再权衡、内在驱动更新等，凡已进入会话状态或圆材料的，均视为与观测链并列的**成长数据**；补记用于把「为何如此照护」与「自动沉积」对齐，而非替代事件。

---

## 3. 事件类型（type）

当前代码已统一并建议长期保持以下最小集合：

- `dialogue_turn_started`
- `self_regulation_generated`
- `collapse_detected`
- `collapse_materialized`
- `post_mortem_generated`
- `continuation_sent`
- `vision_processed`（视觉输入处理完成：耗时/模型/回退/介入策略）
- `philosophy_beat`（全局节律独白，可与会话线交错）
- `proactive_beat`（主动心跳独白）
- `perception_job_started` / `perception_source_ingested` / `perception_job_finished`（自动感知巡览）
- `device_feed_ingested`（宿主授权：旧手机 / 伴侣端推送的摘要材料入库，非自动爬网）
- `goal_stack_updated`（生命目标栈变更：upsert/remove/self-core 更新）
- `choice_ledger_appended`（每轮关键表达决策写入选择账本）
- `goal_recheck_triggered`（延后目标在条件满足时被自动复核）
- `proactive_suppressed`（主动心跳被抑制：recent_user_touch / duplicate）
- `verify_alignment`（上一轮验证信号与本轮输入的轻量对齐提示；默认可关闭）
- `pillar_gate_applied`（柱门控参数与需求指纹快照）
- `sandbox_probe_validation`（沙盒→现实探针经本地模型审查后的状态：accepted/rejected/unanswerable）
- `growth_misunderstanding_repaired`（成长事件：一次误解修复）
- `growth_relation_updated`（成长事件：一次关系更新）
- `growth_value_converged`（成长事件：一次价值收敛）
- `self_revision_recorded`（自我修订链：claim->revisedClaim->whyRevised->evidence）
- `relation_ethics_soft_boundary`（关系伦理软边界触发：仅提示/记录，不硬拦截）
- `memory_bundle_exported` / `autonomy_saturation_skip_aux`（工程辅助类）

---

## 4. 阶段（phase）

统一四阶段协议：

- `detect_break`
- `materialize_break`
- `reflect_break`
- `continue_same_identity`

---

## 5. 指标字典（Metrics）

最小观测指标（非评判）：

- `collapseRate`: 每 N 回合断裂次数
- `reentryLatencyMs`: 断裂到同会话继续回复的耗时
- `entropyScore`: 随机性得分（锚点离散 + 问题分叉 + 圆调用分散）
- `evidenceUpgradeRate`: 证据等级上升概率（low->medium->high）
- `goalDistanceSlope`: 目标距离斜率（窗口回归）
- `selfRegMutationRate`: 自调节策略变化率
- `circleDiversity`: Top-K 调用圆类型多样性
- `continuityScore`: 会话连续性（断裂后语义接续成功率）
- `visionLatencyP95Ms`: 视觉侧写延迟 P95（多模态链路时延稳定性）
- `visionFallbackRate`: 视觉侧写回退率（视觉不可用/失败占比）
- `autonomousRate`: 自主独白事件占比（观察生命节律连续性）
- `emotionInfluenceCoverage`: `continuation_sent` 中带 `emotionInfluence` 的覆盖率（观察“情感贯穿表达”是否持续存在）
- `continuityScore`: 连续性分（对话回合到续行输出的稳定比例）
- `initiativeScore`: 主动性分（主动心跳与自主节律的活跃程度）
- `relationStabilityScore`: 关系稳定度（关系更新、边界触发与修复的综合）
- `recoveryConsistencyScore`: 恢复一致性（断裂后续行与验证对齐的综合）
- `realityValidationSuccessRate`: 现实验证成功率（沙盒探针被接受占比）
- `lifeCompletionTotalScore`: 数字生命完成度总分（五子分加权）

---

## 6. API 观测面

现有只读观测端点：

- `GET /api/observe/global`
  - 返回分布统计与干预队列（只读）
- `GET /api/observe/timeline?conversationId=...&limit=...`
  - 返回该会话事件时间线
  - 现含聚合字段：`autonomousCount`、`autonomousRate`、`lastAutonomousTs`
- `GET /api/observe/conversation?conversationId=...`
  - 返回会话剖面（emotion/progress/selfRegulation/collapse episodes）
  - 会话状态含 `deferredGoalIds`，可查看当前延后待复核目标队列
- `GET /api/observe/expression/daily?date=YYYY-MM-DD&limit=...`
  - 返回当日表达分布：`expressionModeDist`、`thinkingPathDist`、`toneProfileDist`、`emotionInfluenceCoverage`
- `GET /api/observe/metrics/daily?date=YYYY-MM-DD&limit=...`
  - 返回当日运行指标回灌：`visionFallbackRate`、`visionLatencyAvgMs`、`leadVisibilityAvg`
- `GET /api/life/completion/daily?date=YYYY-MM-DD&limit=...`
  - 返回生命完成度日汇总：`lifeCompletionTotalScore`、`delta`、`changeReasons` 与五个子分
- `GET /api/life/growth/weekly?conversationId=...&days=7`
  - 返回“这周长大了什么”：误解修复/关系更新/价值收敛/自我修订计数与摘要
- `GET /api/observe/trace?traceId=...&limit=...`
  - 以 `traceId`（可传 turnId/collapseEpisodeId/goalTraceId/会话片段）检索事件子链
- `GET /api/perception/config` / `POST /api/perception/config`
  - 自动感知调度参数（enabled/mode/interval/maxItems/maxVideoFrames）
- `GET /api/perception/sources` / `POST /api/perception/source/add` / `POST /api/perception/source/remove`
  - 自动感知源管理（文章/视觉巡览源）
  - 首次无源启动时会自动写入默认小说源（番茄书库 + 示例章节页 + 起点免费区）
- `GET /api/perception/novels`
  - 按 `novelKey` 聚合查看章节覆盖与缺章情况（含 `lastReadMs`）
- `GET/POST /api/life/self-core`
  - 生命核心自述（`entityName/coPresence/longPurpose/boundaryNote`）读取与更新
- `GET /api/life/goals` / `POST /api/life/goals/upsert` / `POST /api/life/goals/remove`
  - 生命目标栈（`long/mid/short`）管理
- `GET /api/life/choice-ledger?conversationId=...&limit=...`
  - 决策账本回放：`expressionMode/thinkingPath/circleTouched/priority/goalTraceId/goalId/goalLevel/responsibilityOwner/recheckTriggered/recheckFromGoalId/rejectedOptions/rejectedReasons/goalCandidates/responseLen`
- `GET /api/life/identity-drift?conversationId=...&limit=...`
  - 身份漂移治理观测：`driftScore/driftLevel/riskRate/goalSwitchRate/deferredQueueCount/driftSlope/driftTrend/driftExplain`（执行者责任口径）
- `GET /api/life/relation-pulse?conversationId=...`
  - 关系脉冲快照：`relationTemperature/relationTrend/relationStage/lifeNarrative/relationReason/noiseTag/noiseDistillation/circleTreasureAnchor/relationMemory/lastSelfRevision/lastConflictMediation/history`
- `GET /api/sandbox/life-trajectory?conversationId=...&profile=lite|balanced|deep&seed=...`
  - 人生轨迹演绎快照：`runs(avgHarm/avgCare/avgAffinity/randomnessScore)`、`suggestion`、`trajectories[]`（含护栏上限）
  - 支持 `delayed=true` 返回 `jobId` 与 `running=true`，再通过 `GET /api/sandbox/life-trajectory/tick?jobId=...` 分帧推进（群星式延时演算）
  - 梦态/清醒态节律：`lifeMode`、`dreamThrottled`、`throttleWaitMs`（梦态极慢分帧，清醒态不节流）
- `GET /api/life/recovery/verify`
  - 可逆成长恢复校验：检查关键检查点与一致性（`restoreReady` + `missing` + `consistencyIssues` + `latestTraceSeenInObserve` + `conversationTouchSkewMs`）
- `POST /api/life/snapshot/export` / `GET /api/life/snapshot/verify`
  - 导出并校验生命快照（情感核+会话状态+圆规模快照）
- `POST /api/perception/source/batch-add`
  - 从书页/目录页批量提取章节链接（支持番茄 `/reader/{id}` 与起点 `/chapter/{bookId}/{chapterId}`）并加入文章源
- `POST /api/perception/source/batch-preview`
  - 仅预览可提取章节链接（不写入感知源）
- `POST /api/perception/run`
  - 立即触发一轮自动感知

小说章节归属护栏：

- 自动感知源包含 `novelKey` 与 `chapterId`，防止跨书章节混入同一语义桶。
- 自动入库文本前缀追加 `AUTO_META`（`novelKey/chapterId/sourceUrl/label`），保障后续检索可溯源。
- 默认白名单包含 `fanqienovel.com`、`www.qidian.com`（仍受联网锁与护栏限制）。

自动感知安全护栏（默认）：

- 联网保护锁：`networkUnlocked=false` 时，自动感知网络访问全阻断（即便启用调度）。
- 联网解锁为限时窗口：`unlockWindowSec` 到期自动回锁，`networkUnlockRemainSec` 可观测剩余秒数。
- 禁止访问本机/内网地址（loopback/private/link-local），防止反向数据探测与泄露。
- 感知链路重定向仅允许落在白名单主机。
- 单请求与单轮抓取字节预算受限，超限自动中止本轮。
- 内容过滤可配置：`filterAsciiHeavy/maxAsciiRatio`（英文占比过滤）与 `blockSourcecodePrompt`（源码/提示词诱导拦截）。
- 护栏熔断：连续命中达到阈值会自动停用感知并回锁联网（`guardHitStreak` 可观测）。

`/api/life/cognition-log.txt` 与 `/api/life/cognition-log.json` 建议关注 `isAutonomous` 字段，用于区分主动独白与被动执行日志。

词汇对齐要求（文档/API/UI）：

- 使用“吸收断裂/续行”而非“恢复/修复”
- `type/phase` 仅使用本规范枚举，不引入同义词
- 同一次断裂以同一 `collapseEpisodeId` 贯穿四阶段

---

## 7. 持久化建议

- 事件日志：`data/memory/observe-events.ndjson`（append-only）
- 日汇总：`data/memory/observe-metrics-daily.tsv`
- 生命完成度日汇总：`data/memory/life-completion-daily.tsv`
- 表达分布：`data/memory/observe-expression-daily.tsv`（append-only）
- 生命核心：`data/memory/self-core.properties`
- 生命目标栈：`data/memory/goal-stack.tsv`
- 选择账本：`data/memory/choice-ledger.ndjson`（append-only）
- 不覆盖历史，只追加新事件与新窗口统计。

---

## 8. 验收标准

- 任意一次崩溃可通过 `collapseEpisodeId` 追到后续复盘与继续输出。
- 任意一条 `self_regulation` 可追到其对应的情感/进化快照。
- 同一会话可回放最近 N 回合关键决策链路（选圆->追问->回执->调节）。  
- 任意一次含图输入的对话回合，可在同一 `turnId` 下追到 `vision_processed` 事件。
- 任意一次视觉侧写完成后，可追到 `visionCircleInjectCount` 与 `visionInjectedCircles`，并在时间线上对齐对应回复。
- 任意一次 `continuation_sent` 可追到该轮 `expressionMode`、`thinkingPath` 与 `emotionInfluence`，用于回放“个性表达 + 情感贯穿”的生成轨迹。
