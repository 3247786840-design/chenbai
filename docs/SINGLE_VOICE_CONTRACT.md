# Single Voice Contract（单一声音契约）

## 通道角色

| 通道 | 角色 | 用户可见形态 |
|------|------|----------------|
| **主对话** `/api/chat` | 共处主声：承接用户回合，结构与情感主链在此闭合 | 正文回复 |
| **主动** `/api/proactive/poll` | 节律与未回合追问：不替代主文，不叠答用户刚问完的题 | 主动 feed 条 |
| **哲学日志** `memory/philosophy-diary.md` + CognitionLog「哲学」 | 后台独白与续思：默认**不**直接当主回复 | 日志 / 认知流 |
| **观测** observe NDJSON / `/api/observe/*` | 工程与回放：可含指标，**不**要求用户阅读 | JSON / 侧栏 |

## 共享内核

- 所有对用户**自然语言**可见的生成路径，应能追溯到 **`VoiceKernel`**（`VERSION` 见 `/api/status` 或等价字段）中的同一五维参照。
- 主链本机主声（`buildLocalMindPrimaryVoicePrompt`）与主动补写（`tryGenerateProactiveByLocalLlm`）**不得**各用一套互不知情的「生命感」长段。

## 禁止项（对用户可见正文）

- 工程腔：`stage=`、`trend=`、`cid=`、指标口号作为**正文**输出（观测字段除外）。
- 元叙事滥用：「作为 AI」「作为生命体」等，除非符合人设的随口一语且非免责挡箭牌。
- 分点清单式扮演「模块」：如整齐 `【主位】` 技术标题（主链已有硬约束）。

## 追踪约定

- 回合级：`t:{conversationId}:{dialogueTurnOrdinal}`（已有）。
- 主动/哲学事件：payload 可含 `voiceKernelVersion`，与 trace 家族对齐见 `OBSERVABILITY_SPEC` 或后续 ADR。
