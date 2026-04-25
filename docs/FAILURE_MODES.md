# 失败模式清单（运维与观测）

本页汇总 **可预期** 的失败形态、典型症状、缓解与是否需 ADR。与 [`OBSERVABILITY_SPEC.md`](../OBSERVABILITY_SPEC.md) 配合使用。

| 模式 | 症状 / 代理信号 | 缓解 | 备注 |
|------|------------------|------|------|
| 本机 LLM 不可用或慢 | `/api/status` 中推理端异常、对话超时、`continuation_sent` 缺失或极短 | 检查 `data/localai.properties`、LM Studio/Ollama 监听、延迟档位 `fast/balanced/deep` | 参见 [`PERFORMANCE_BUDGET.md`](PERFORMANCE_BUDGET.md) |
| 并联辅助轮超时 | 观测中出现多轮 `TimeoutException` 路径、aux 降级 | 长输入会加长等待；可调档位或降低 `crashLevel` 相关路径 | `AUX_LLM_HTTP_TIMEOUT_*` 为 HTTP 层硬上限 |
| 磁盘满或不可写 | 启动失败、`observe-events.ndjson` 追加失败 | 清日志、扩盘；勿删观测行「省事」 | 见 [`RELEASE_AND_ROLLBACK.md`](RELEASE_AND_ROLLBACK.md) |
| 观测文件损坏 | `recovery/verify` 或解析 NDJSON 报错 | 从备份恢复；若裁剪历史须留记录 | 与身份链相关时记入 `COLLAPSE_EPISODE_SUPPLEMENT` |
| HTTP 端口占用 | 8080 已被占用，冒烟跳过运行时探测 | 停冲突进程或改端口配置 | `verify-smoke.ps1` 在占用时仅编译 |
| 偏好文件损坏 | `data/preferences.json` 解析失败回退默认 | 修复 JSON 或删除后重建 | [`UserPreferences`](../src/com/lovingai/prefs/UserPreferences.java) 默认值 |
| 反馈日志写入失败 | `/api/feedback` 返回错误 | 检查 `data/` 权限与磁盘 | `FeedbackLog` |

**日汇总**：可复用 `observe-expression-daily` / 指标回灌路径对超时率、降级次数做趋势对比（非阻断）。
