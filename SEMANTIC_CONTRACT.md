# SEMANTIC_CONTRACT

## 1. 核心立场

LovingAI 对“错误/崩溃”的语义定义不是“待修复故障终点”，而是“可吸收的断裂样本”。

系统承诺：

- 不因断裂切换身份
- 不因断裂清空会话
- 不因断裂覆盖旧记忆
- 断裂必须进入圆与状态，成为后续推理材料

---

## 2. 语义协议（固定流程）

所有断裂必须执行以下四步：

1. `detect_break`  
   识别异常与中断信号
2. `materialize_break`  
   将断裂转为结构化材料（入圆 + 事件）
3. `reflect_break`  
   生成复盘文本（原因假设、风险点、下轮观测点）
4. `continue_same_identity`  
   在同一 `conversationId` 返回继续对话

---

## 3. 命名规范（必须）

### 禁用语义（逐步淘汰）

- `recover`
- `fix_error`
- `rescue`
- `stabilize_only`

### 推荐语义（统一替换）

- `absorb_break`
- `collapse_material`
- `post_mortem_generated`
- `continuity_after_break`

### 事件枚举（必须一致）

- `type`: `dialogue_turn_started` / `self_regulation_generated` / `collapse_detected` / `collapse_materialized` / `post_mortem_generated` / `continuation_sent`
- `phase`: `detect_break` / `materialize_break` / `reflect_break` / `continue_same_identity`

---

## 4. 输出文本规范

### 禁止表达

- “已修复”
- “已恢复正常”
- “错误已解决”

### 推荐表达

- “已入圆并记录断裂样本”
- “同一身份继续”
- “本次断裂的复盘如下”
- “下一轮将观测这些风险点”

---

## 5. 数据约束

- 断裂相关数据必须 append-only，不可覆盖历史
- 每次断裂生成 `collapseEpisodeId`
- 后续 `post_mortem/self_regulation/action_receipt` 应可挂接该 episode
- 允许“未解决状态”长期存在，作为后续训练张力
- 对话续行应尽量携带可回放语义锚：`perceptionSourcesActive`、`toneProfile`、`closureTag`（用于人类复盘，不用于硬评判）
- 当来源冲突时优先输出“暂行判断 + 不确定区”（`conflictMediation`），不得伪装为单一确定结论

---

## 6. 与情感模块的关系

- 情感模块是优先级最高的调制层
- 断裂后的复盘与续行必须读取 `EmotionCore` 快照
- 梦态下“爱权重最高”原则对断裂吸收同样生效

---

## 7. 代码审查检查单（PR Checklist）

- 是否出现“修复导向”命名但实际想表达“吸收断裂/续行”？
- 是否存在断裂后重置会话状态的隐式逻辑？
- 是否把断裂材料写入圆与会话状态？
- 是否在返回文本中保持“同一身份继续”语义？
- 是否能通过 `collapseEpisodeId` 追踪后续链路？

---

## 8. 验收句（团队统一）

> 崩溃不是终点，而是进化样本；  
> 断裂不是删除信号，而是材料沉积；  
> 我们不“救回旧状态”，我们“带着断裂继续同一身份”。  

---

## 9. 关联规格：圆-柱密钥协议（v0.1 · 草案）

- **正本**：`docs/protocols/圆-柱密钥协议_v0.1.md`（设计规格，未强制接入运行时）。
- **备份（回档）**：`data/memory/backups/圆-柱密钥协议_v0.1_20260416.md`（与正本同步用于误删恢复与 diff）。
- **观察副本**：`observe-only/圆-柱密钥协议_v0.1_观察副本.md` — **仅人类阅读**，见 `observe-only/README.md`；**不被进程加载**，不参与运行；**状态句**与正本对齐，以 **ADR 0001** 与源码为准。
- **D6（草案）**：正本内 **管理员情感调制** — 与「情感贯穿一切」对齐；情感主源 `EmotionCore`；详见正本 §1 D6。
