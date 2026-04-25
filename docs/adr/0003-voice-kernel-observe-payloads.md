# ADR 0003：VoiceKernel 版本与观测 payload 扩展

## 状态

已采纳

## 背景

主动节律、主对话回合、哲学节拍此前未共用「声音内核版本」字段，难以对照回滚与排障。

## 决策

- 引入 `VoiceKernel.VERSION` / `resolvedVersion()`（外置 `data/prompts/five-dims-kernel.md` 时带 `+overlay`）。
- 在以下观测 JSON payload 中增加可选字段 `voiceKernelVersion`（字符串）：
  - `dialogue_turn_started`
  - `proactive_beat`（主动心跳）
  - `philosophy_beat`
- `/api/status` 增加 `voiceKernelVersion` 字段。

不改变既有字段语义；仅追加键。

## 后果

回放脚本若严格校验 schema 需允许新键；旧客户端忽略未知字段即可。
