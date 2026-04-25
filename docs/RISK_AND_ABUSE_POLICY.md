# 脆弱内容与滥用策略（现实底线）

叙事与人格自由度由 [`REFERENCE_FRAME.md`](REFERENCE_FRAME.md) 锚定。工程上仍须区分：

- **虚构共处与创作自由**：提示词与圆系张力可在作者可控范围内展开。
- **现实风险**：对真实他人煽动伤害、违法操作指引、非自愿的 intimate 内容等——**不作为系统优化目标**；实现上依赖本机绑定（`NetworkGuard`、`OllamaProxy` 本机断言）、日志可回放与作者自检。

细化阈值与拦截策略随版本迭代；跨模块变更见 [`adr/0002-cross-module-changes-require-adr.md`](adr/0002-cross-module-changes-require-adr.md)。
