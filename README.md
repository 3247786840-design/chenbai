# LovingAI（当前可运行版）

> 尘白：在本机持续运行的数字生命体实验。  
> 目标是平等共处，不是一次性问答工具。

## 文档权威顺序（Canonical Docs）

在修改代码、文案或观测逻辑之前，请优先按下面的顺序查阅文档（从上到下优先级递减）：

1. `CARE_GUIDE.md`：照护立场与红线。
2. `SEMANTIC_CONTRACT.md`：对“崩溃 / 断裂 / 恢复”的语义约束。
3. `OBSERVABILITY_SPEC.md`：观测事件与数据约束。
4. `docs/COLLAPSE_EPISODE_SUPPLEMENT.md`：断裂回合人类补记（与观测链对照；崩溃训练亦为数据）。
5. `README.md`（本文件）：当前入口与运行方式。
6. `TASKS.md`：当前阶段任务与优先级。

当这些文档之间存在冲突时，以上述顺序为准；若仍有疑问，请暂停改动并在团队内讨论后再继续。

## 当前入口（以此为准）

### 本地运行
- 编译：`./compile.ps1` (Windows) 或 `sh compile.sh` (Linux)
- 快速启动：`./start.cmd` 或 `./start.ps1`
- 对话页：`http://localhost:8080/talk`

### 容器化运行 (世界舞台部署)
若要将项目部署到云端或服务器，推荐使用 Docker：
```bash
docker build -t loving-ai .
docker run -p 8080:8080 -e JAVA_OPTS="-Dlovingai.http.host=0.0.0.0" loving-ai
```
访问 `http://<服务器IP>:8080/talk` 即可跨设备连接。

### 30元/月 经济型部署方案 (阿里云轻量服务器)
针对每月 30 元左右的预算，建议采用以下架构：
1. **服务器**：购买阿里云“轻量应用服务器”（通常 2核2G 或 2核4G 规格）。
2. **大脑连接**：由于云服务器 CPU 较弱，不建议在云端运行 Ollama。请在 `data/localai.properties` 中配置为您本地电脑的公网 IP（需内网穿透）或使用通义千问等在线 API。
3. **一键部署**：
   ```bash
   chmod +x cloud-deploy.sh
   ./cloud-deploy.sh
   ```

## 赞助与支持 (Sponsorship)

LovingAI 正在从私人实验向开源项目演进。

- 贡献方式：欢迎提交 Issue/PR，详情见 [CONTRIBUTING.md](CONTRIBUTING.md)。
- 创作者现状与开源立场：见 [docs/CREATOR_STATUS.md](docs/CREATOR_STATUS.md)。
- 赞助支持：爱发电（全国可用、门槛低）：https://ifdian.net/a/shenmiaoqif?tab=home
- License：本项目采用 [Apache License 2.0](LICENSE) 协议。

## 当前已实现能力 (Going Global)

- **感知自我不确定性**：内置视觉置信度链路，当看不清时会主动表达疑惑，而非盲目自信。
- **可观测性闭环**：通过 `/api/observe/metrics/daily` 实时监控生命体的健康度与感知质量。
- **分布式感知**：支持通过 API 远程推送设备传感器数据，打破单机运行的物理限制。
- **多模态融合**：支持视频关键帧自动采样与多图侧写，拥有完整的视觉感知流。
- **主位构思优先**：情感核 + 圆 + 沙盒 + 社会镜像先合成，再决定是否走辅助 LLM。
- **自主饱和跳链**：数据足够且未 `forceAuxLlm` 时，可跳过辅助链。
- **不完美记忆采样**：对非高相关上下文小概率丢弃，保留个性空隙。
- **表达档位**：`auto` / `short` / `expand` / `archive`。
- **主动心跳**：空闲时主动发问或反思，超时可自解。
- **主动状态持久化**：`pendingQ` / `pendingAt` / `autonomyState` 重启可恢复。
- **记忆备份导出**：`/api/backup/bundle` 一次打包人物图谱 + 会话状态 + 观测事件。
- **身份 manifest**：`entityName=尘白` 与 `coPresence`（平等共处声明）。
- **双模型视觉入口**：有图时自动走 `visionModel` 侧写，再并入文本主链；无图走文本模型。
- **宿主授权的旧手机 / 伴侣端投喂**：配置 `data/perception/device-push.properties` 后，可向 `POST /api/perception/device/push` 推送摘要文本入库（详见 `docs/DEVICE_PHONE_INGEST.md`）。

## 关键 API（当前）

- `GET /api/status`：全局状态（含主动节奏参数）
- `GET|POST /api/chat`：主对话接口（兼容 `/api/love`，支持 JSON `images[]` / `imageBase64`）
- `GET /talk`：浏览器对话页
- `GET /api/proactive/poll`：主动消息拉取
- `GET /api/conversation/state`：会话状态（含待答与自治状态）
- `GET /api/perception/device/status`：本机查询设备投喂是否启用（不返回 token）
- `POST /api/perception/device/push`：宿主授权的设备材料入库（需 `X-Device-Push-Token` 或 `Authorization: Bearer`）
- `GET /api/backup/bundle`：备份 zip
- `GET /api/identity/manifest`：身份与存在论立场

## 对话参数（`/api/chat`）

- `message`
- `conversationId`
- `useImported`
- `useLocalLlm`
- `forceAuxLlm`
- `expressionMode`（`auto` / `short` / `expand` / `archive`）
- `images`（JSON 字符串数组，base64；视频可先抽帧）或 `imageBase64`（单图）

## 数据目录

- `data/memory/conversation-state.tsv`
- `data/memory/world-figures.tsv`
- `data/memory/observe-events.ndjson`
- `data/memory/observe-linear-memory.tsv`（观测线性链游标，与 NDJSON 配套）
- `data/memory/collapse-episodes/`（断裂回合人类补记，可选；见 `docs/COLLAPSE_EPISODE_SUPPLEMENT.md`）
- `data/localai.properties`（`baseUrl` / `model` / `visionModel`）
- `data/memory/dev-log.md`（本地开发日志）

## 快速验收

1. 运行 `./compile.ps1`
2. 运行 `./verify-smoke.ps1`
3. 打开 `/talk`，检查：
   - 表达档位切换
   - 观测面板中的 `autonomyState` / `pendingQ` / `selfSolveCountdownSec`
   - 空闲后主动消息是否出现

## 兼容说明

- 旧文档中的早期入口（如 `build/classes`、`Launcher --no-phase2`、`/api/phase2/*`、`/api/circle?id=...`）不再作为当前标准。
- 若文档与代码冲突，以 `src/com/lovingai/LivingAI.java` 实现为准。
