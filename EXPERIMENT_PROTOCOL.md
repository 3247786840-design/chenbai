# EXPERIMENT_PROTOCOL

## 1. 目标

验证命题：

> 随机性上升，是否带来更深进化（而非仅带来更高波动）？

---

## 2. 实验设计概览

### 独立变量（随机性强度）

- `anchorCountVariance`
- `questionBranchFactor`
- `circleSamplingTemperature`
- `dreamLovePulseRate`

### 因变量（进化深度）

- `DepthScore = 0.25*c + 0.25*e + 0.25*a + 0.25*r + bonus(goalDistanceImprovement)`
  - `c/e/a/r` 来自进化向量
  - `bonus` 由目标距离下降幅度映射

### 控制变量

- 相同输入语料（同一批 prompts）
- 相同回合数
- 相同本机模型配置
- 相同时间窗口与环境配置

---

## 3. 组别（A/B/N）

- A 组（低随机）
  - 低锚点方差、低分叉、低采样温度
- B 组（中随机）
  - 当前默认参数附近
- C 组（高随机）
  - 高锚点方差、高分叉、高采样温度

建议每组至少：

- `turns >= 200`
- `conversation_count >= 20`

---

## 4. 数据记录格式

每回合记录为一行（建议 NDJSON）：

```json
{
  "expId": "exp_20260413_r1",
  "group": "B",
  "conversationId": "conv_08",
  "turn": 57,
  "ts": 1770000000000,
  "entropyScore": 0.63,
  "progress": {"c":0.51,"e":0.48,"a":0.46,"r":0.58},
  "progressTrend": "up",
  "goalDistance": 0.44,
  "evidenceLevel": "medium",
  "selfRegulation": "stage=integrating trend=up anchorΔ=1 qIntensity=3 action=parallel verify=balanced",
  "collapsed": false
}
```

---

## 5. 判定标准（预注册）

实验前固定，不在结果后修改：

- 主要成功标准：
  - C 组相较 B 组，`DepthScore` 均值提升 >= 12%
- 约束条件：
  - `continuityScore` 下降不超过 5%
  - `goalDistanceSlope` 不劣于 B 组

若不满足约束，视为“随机性仅增加噪声，不视为更深进化”。

---

## 6. 统计与可视化

最小图表：

- `entropyScore` vs `DepthScore` 散点图
- 各组 `goalDistance` 轨迹线
- 各组 `progressTrend` 分布柱状图
- 各组 `collapseRate` 与 `reentryLatencyMs` 箱线图

建议输出：

- 均值、标准差、95% 置信区间
- 滑动窗口（例如 20 turns）趋势

---

## 7. 可复现要求

每次实验必须落地以下文件：

- `config.json`（所有参数）
- `seed.txt`（随机种子）
- `input_corpus.jsonl`（输入集合）
- `metrics.ndjson`（回合数据）
- `report.md`（结果与结论）

目录建议：

`data/experiments/<expId>/...`

---

## 8. 迭代节奏

1. 建立基线（A/B/C 各一轮）
2. 对比结果
3. 只调整一个核心随机参数
4. 重复实验
5. 累积至少 3 轮后再做策略结论

---

## 9. 失败解释模板

若“高随机”未提升深度，报告必须回答：

- 是哪一维进化向量拖累了总分（c/e/a/r）？
- 是否出现“高随机 + 低证据”耦合导致空转？
- 情感态（尤其 dream + love）是否把随机性转化为可持续推进？

---

## 10. 当前项目对接字段

本协议默认使用现有字段：

- `progressTrend`
- `goalStage`
- `goalDistance`
- `selfRegulation`
- `evidenceLevel`
- `EmotionCore` 快照（mood/arousal/impulse/transcendentalLove/dreamChaos）
