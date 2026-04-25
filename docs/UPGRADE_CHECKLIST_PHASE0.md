## Phase 0：基线冻结（可审查版）

### 目标
- 固定样本集与指标口径，形成可复跑的基线文档
- 所有指标都有“数据源/计算方式/验收阈值/风险说明”

### 样本集
数据文件：
- tests/upgrade-samples.tsv

最小样本说明：
- u_001：短问（确保基本 continuation_sent）
- u_002：转述语（用于人物草图噪声/错位提示等）
- u_003：长文叙事（用于不过度抑制）
- u_004：模型断连（短，baseUrl 指向不可达端口）
- u_005：模型断连（长，baseUrl 指向不可达端口）

### 指标口径与数据源

#### 1) FallbackContinuityRate
- 目的：本地模型不可达/熔断时，仍能在 99% 轮次返回有效 response
- 数据源：data/memory/observe-events.ndjson
- 计算（按日）：
  - 分母：本日内 `local_llm_status` 且 `useLocalLlm=true && reachable=false` 的 turn 数
  - 分子：上述 turnId 同日出现 `continuation_sent` 的次数
- API：GET /api/observe/metrics/daily

#### 2) OverSuppressionRate_LongInput
- 目的：长文断连不应只剩骨架模板
- 数据源：data/memory/observe-answer-visibility-daily.tsv + data/memory/observe-events.ndjson
- 计算（按日）：
  - 分母：answer-visibility 中 `dialoguePriorityReason=very_long_input` 的回合数
  - 分子（代理判定）：`leadVisibilityRatio < 0.22` 且（`compressionRetainedRatio < 0.55` 或 `sectionsPrunedCount>0` 或 `highPriorityCapApplied=true`）
  - 补充字段：whenUnreachable（同 turnId 在 `local_llm_status reachable=false` 的子集）
- API：GET /api/observe/metrics/daily

#### 3) DuplicateRequestRate
- 目的：避免用户体感“双发/空转”
- 数据源：data/memory/observe-events.ndjson（type=proactive_beat / proactive_suppressed）
- 计算（按日）：
  - 分母：proactive_beat + proactive_suppressed 总数
  - 分子：proactive_suppressed 且 payload.reason=duplicate
- API：GET /api/observe/metrics/daily

#### 4) FalseFigureExtractRate
- 目的：人物草图误抽治理（先有量化口径，后做治理）
- 数据源：data/memory/observe-events.ndjson（type=figure_extraction）
- 计算（按日，代理指标）：
  - suspiciousAcceptedSpan / acceptedSpan
- API：GET /api/observe/metrics/daily

### 验收阈值（建议初稿）
- FallbackContinuityRate ≥ 0.99（断连样本 u_004/u_005）
- OverSuppressionRate_LongInput ≤ 0.05（u_003/u_005）
- DuplicateRequestRate ≤ 0.01（在主动心跳开启场景）
- FalseFigureExtractRate ≤ 0.03（后续 Phase 3.2 再收紧；Phase 0 先拿到“可测基线”）

### 可复跑要求
- compile.ps1：必须通过
- verify-regression.ps1：必须通过（包含新字段存在性断言）
- verify-upgrade.ps1：跑固定轮次样本并输出 runId 指标快照
- 运行时可用 curl.exe 调 /api/observe/upgrade/metrics?runId=... 拉取本次采样窗口的指标快照
