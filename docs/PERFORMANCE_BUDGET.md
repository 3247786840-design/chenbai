# 性能预算（延迟与超时）

汇总主链与并联路径的 **数量级**，便于调参与回归对比。具体常量以源码为准。

## 主生成（`OllamaProxy.generate` / OpenAI 兼容）

| 档位 (`localai.latencyProfile`) | 默认 `timeoutSec`（主路径） |
|----------------------------------|----------------------------|
| fast | 116 |
| balanced（默认） | 132 |
| deep | 180 |

长输入会叠加额外等待（`LONG_INPUT_EXTRA_TIMEOUT_SEC` = 18s 量级，并联轮次为半额叠加）。

## 并联辅助 LLM（三轮 HTTP）

| 轮次 | HTTP 客户端超时（秒） |
|------|------------------------|
| Round1 | 240 |
| Round2 | 180 |
| Round3 | 150 |

Future 等待时间取「HTTP 上限」与「按 profile/crashLevel 计算的 legacy 下限」的较大值。

## 主动自解思考

| 项 | 默认 |
|----|------|
| `proactiveSelfSolveTimeoutMs` | 95_000 ms（可用 JVM 属性覆盖秒数） |

## 其它

- 对外 HTTP 客户端 `connectTimeout`：多处 12s（探测、抓取等）。
- 哲学/主动调度：受会话偏好与冷却约束，不单独列硬超时表。
