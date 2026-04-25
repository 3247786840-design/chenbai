# JVM 开关矩阵（`lovingai.*`）

启动参数 `-Dkey=value`。未列出项以源码 `System.getProperty` 为准。

| 属性 | 默认 / 空行为 | 用途 |
|------|----------------|------|
| `lovingai.data.root` | 空：使用当前工作目录下数据路径 | 覆盖数据根目录（`LocalAiPrefs`） |
| `lovingai.autonomy.saturation.threshold` | 内置默认 | 饱和省略阈值；接近 `0.99` 可减弱省略 |
| `lovingai.auxLlm.threads` | 池大小由代码决定 | 并联辅助线程池规模 |
| `lovingai.actionLoop.suppressOnVeryLong` | `true` | `false`：≥800 字仍追加「行动闭环·续行」块 |
| `lovingai.actionLoop.requireNarrativeHeuristic` | `false` | `true`：仅当启发式判定为叙事粘贴时才抑制行动闭环 |
| `lovingai.dialogue.stripAuxOnVeryLong` | `false` | `true`：≥800 字时仍剥除【并联回声】【自主发散·思考线】等声部（旧默认行为） |
| `lovingai.dialogue.veryLongLocalCompress` | `false` | `true`：长文高优先时仍用本机模型做「输出再压缩」 |
| `lovingai.proactive.selfSolveTimeoutSec` | 95（秒→内部 ms） | 主动自解思考上限（20–900 内 clamp） |
| `lovingai.proactive.selfSolve` | `false` | `true` 启用挂起问句自解推送 |
| `lovingai.observe.verifyAlignment` | `false` | 调试：对齐校验启发式 |
| `lovingai.dialogue.highPriorityMaxChars` | 内置 | 高优先短答字数上限 |
| `lovingai.offlineAutonomy.mode` | `deep` | 离线自主模式字符串 |
| `lovingai.imported.quotaGb` | 大默认配额 | 导入语料磁盘配额 |
| `lovingai.localai.autoProbe` | `true` | 是否自动探测本机 OpenAI 端口 |
| `lovingai.localai.probe.ports` | 空 | 逗号分隔端口列表 |
| `lovingai.prompt.fiveDimsPath` | 空：用内嵌或 `data/prompts/five-dims-kernel.md` | 五维内核 Markdown 覆盖路径 |
| `lovingai.prompt.personaConstantsPath` | 空：用内嵌或 `data/prompts/persona-constants.md` | 人格常量 Markdown 覆盖路径 |
| `lovingai.api.key` | 空：不校验 | 若设置则要求请求头 `X-API-Key`（或 query `apiKey`）匹配 |

## 运行时口径备注（非 JVM 开关）

- 本地生成超时档位（代码默认）：`fast=180s`、`balanced=240s`、`deep=360s`（见 `defaultGenerateTimeoutSecByProfile`）。
- 本地模型可达性由探测 + 熔断冷却控制（`LOCAL_LLM_*` 运行时状态变量），目前为代码内建策略，非独立 JVM 开关。

## 用户偏好文件（非 JVM）

| 路径 | 说明 |
|------|------|
| `data/preferences.json` | `UserPreferences`：节律、哲学调度、声部合并、失败降级等 |

回滚策略见 [`RELEASE_AND_ROLLBACK.md`](RELEASE_AND_ROLLBACK.md)。
