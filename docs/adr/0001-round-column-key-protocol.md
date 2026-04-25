# ADR 0001：圆-柱密钥门控（v0 工程映射）

## 状态

**Accepted（v0 已接线）** — 与 `docs/protocols/圆-柱密钥协议_v0.1.md` 对齐；已在主链完成候选裁剪接线与观测字段，后续继续按分期深化。

## 上下文

协议 v0.1 约定：蒸馏柱（动静态二象）、需求密钥整体性、加权相似度门控、历史低占比、多圆并行门控，以及 **D6** 下以 `EmotionCore` 为权威情感源调制门控参数。工程缺口包括：`Circle` 内柱向量存哪、需求密钥如何可复现编码、与现有「圆优先 / 目标仲裁」的先后顺序。

## 决策

### 1. 柱向量 ↔ `Circle` 存储映射（v0）

- **不落新二进制格式**：复用现有 `Circle.loveKnowledge`（`Map<String, Double>` 不适合存向量），因此 **v0 将柱摘要与元数据写入 `Circle.soulMemory`（字符串值）**，数值侧用 **紧凑文本编码**（见下）。
- **键名空间**（前缀避免与叙事键冲突）：`pillar.v0.*`，由 `com.lovingai.core.columnkey.PillarKnowledgeKeys` 集中定义。
- **建议键**：
  - `pillar.v0.vector.b64`：列向量 float32 的 Base64（小端 `ByteBuffer`，维顺序固定）；维数 ≤ 256（v0）。
  - `pillar.v0.dimLabels`：可选，逗号分隔标签，与向量维顺序一致。
  - `pillar.v0.sealedMask`：可选，`0/1` 串或与 `dimLabels` 等长的封存标记（**D1 静态刻度**）。
  - `pillar.v0.schemaVersion`：字面量 `0`。
- **圆 ID**：仍用 `Circle.id`；柱是「每圆一条（或多条 typed）记录」，v0 只约定 **单条主柱**；多柱扩展为 `pillar.v0.slot.<name>.vector.b64`。

### 2. 需求密钥编码（v0 · 可复现）

- **整体性**：密钥对象在运行时表现为 **规范化 UTF-8 字节序列的指纹 + 审计用规范化文本**，比对阶段再投影为向量；**不在存储层拆条丢失语义**。
- **v0 规则**（`DemandKeyEncoder`）：
  1. Trim、折叠空白为单空格、小写（仅 ASCII区段；中文保持原样）。
  2. 可选：去掉零宽字符与重复标点（实现已做最小清洗）。
  3. **指纹**：`SHA-256` 十六进制小写字符串（64 字符），写入观测/账本时作为 `demandKeyFp`。
  4. **历史混合（D4）**：在相似度组合时 `score = (1-λ)*sim_now + λ*sim_hist`，`λ = columnGateParams.historyBlendRatio()`，默认 **0.15**，可配置（后续接 `System.getProperty` / 配置文件，不在本 ADR 锁死）。

### 3. 加权相似度（v0）

- 维度对齐后：`sim = Σ w_i * a_i * b_i`（未学习权重时 **w均匀**，或后续接「数据类型 → 权重」手工表）。
- **低耦合兜底**：若全体候选 `sim < minSimilarityFloor`，仍取 **argmax**，并打观测标记 `pillar_gate_low_coupling`（接线时实现）。

### 4. `EmotionCore` → 门控参数（D6）

-单一入口：`EmotionCoreColumnGate.from(emotionCore)` → `ColumnGateParams`（**可记录、可单测**）。
- **v0 曲线（保守、单调）**：
  - `maxParallelCircles`：基础6；`impulse` 或 `arousal` 高则收窄至 **3**（`impulse>0.55 || arousal>0.55`）。
  - `minSimilarityFloor`：基础 **0.12**；高 `transcendentalLove`（>0.35）略降至 **0.10** 以允许更柔的备选。
  - `weightJitterScale`：基础 **1.0**；`dreamChaos` 高（>0.45）降至 **0.92**，避免门控抖动被混沌放大。
  - `historyBlendRatio`：默认 **0.15**（与协议建议区间一致）。

### 5. 与现有主链的先后关系

- **当前**：柱密钥门控已作为 **前置候选裁剪**（缩小「圆优先抽样」的圆集合）接入 `LivingAI`，并保持 **不替换** `goal-stack` 仲裁；仲裁仍对裁剪后的候选生效。
- **守则**：后续优化必须保持“边界可回滚、观测可追踪”，避免静默漂移。

## 后果

- **正**：观测与账本可逐步挂上 `demandKeyFp`、`ColumnGateParams` 快照，满足审计与 D6 回放。
- **负**：`soulMemory` 字符串变长；需定期观测单圆体积。
- **风险缓解**：Phase 2 接线前仅写工具类与文档，不改 `LivingAI` 默认行为。

## 落地分期

| 阶段 | 内容 |
|------|------|
| **0（本提交）** | ADR + `columnkey` 包：`PillarKnowledgeKeys`、`DemandKeyEncoder`、`ColumnGateParams`、`EmotionCoreColumnGate` |
| **1** | 圆候选裁剪钩子 + `observe-events` 字段 `pillar_gate_*` |
| **2** | 权重表 / 可选本地嵌入模型；`pillar.v0.vector.b64` 真实写入路径 |
| **3** | 与 `recovery/verify` 的柱完整性 soft 检查 |

## 相关链接

- `docs/protocols/圆-柱密钥协议_v0.1.md`
