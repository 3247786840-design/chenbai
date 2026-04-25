## 变更说明（2026-04-23）

### Phase 0：基线冻结（可复跑）
- 新增样本集：tests/upgrade-samples.tsv
- 新增采样脚本：verify-upgrade.ps1（按 runId 采样窗口输出指标）
- 新增观测接口：GET /api/observe/upgrade/metrics?runId=...
- 扩展日指标：/api/observe/metrics/daily 返回增加 fallbackContinuityRate / overSuppressionRateLongInput / duplicateRequestRate / falseFigureExtractRate 等字段

### Phase 1：生存优先（连续性与不过抑制）
- 本地模型断连熔断链路可观测：local_llm_status、action_loop_suppressed、figure_extraction 事件写入 observe-events.ndjson
- 本地模型恢复成功时补齐日志：连接恢复成功，清零熔断

### Phase 2：人格稳定层（像“一个人”）
- VoiceKernel 增加人格常量内核（支持 data/prompts/persona-constants.md 外置覆盖）
- 回包前增加“人格一致性检查”轻量修正：去除 AI 自述口癖、低置信默认弱断言、必要时追加简短自我修订链
- 输出洁净化：自动移除形如 U+3010...U+3011 的分段标题行（避免“【主位】【模块】式标题”外泄）
- 新增 persona_consistency_checked 观测事件，便于审查每轮是否被修正以及修正原因

### Phase 3：关系与记忆层（更像“有经历的人”）
- 记忆分层补强：中期关系记忆与长期信念记忆加入“强化/淡忘”强度与按强度延长保留窗口

### 兼容性与修复
- /api/chat 请求体解析支持 JSON 布尔字面量（true/false），不再把 boolean=false 误当缺省
- /api/status 补齐 httpPort/httpHost 字段，保证 verify-regression.ps1 探针通过
- verify-regression.ps1 增加 /api/chat 探针：确保响应不包含 U+3010...U+3011 分段标题
