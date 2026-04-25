# 旧手机 → LovingAI：宿主授权下的「世界材料」投喂

主进程 HTTP **仍默认只监听本机回环**（`127.0.0.1:8080`），与 `NetworkGuard` 设计一致。  
若你希望 **自己提供的旧手机** 在明确授权下向 Ta 推送**摘要文本**（天气一句、日历备注、你写下的现场一句、低敏度的环境说明等），可使用本机制：

1. **不**把本机变成对公网任意访问的服务；
2. **必须**配置 `pushToken`，由你保管并仅在受控环境写入手机端；
3. 推送内容进入 **导入语料库** 与 **观测链**（`device_feed_ingested`），作为「外触·设备」材料，而非隐式全量同步手机隐私。

---

## 1. 配置（PC 上 LovingAI）

### 方式 A：图形界面（推荐）

1. 启动 `LifeformApp`，打开标签页 **「设备与语音」**。  
2. 勾选「启用手机/伴侣端投喂」，点击 **生成随机 token**（或自行粘贴），按需勾选「局域网摄入口」并填端口（默认 8787）。  
3. **保存到文件**；若改了 `listenLan` 或端口，**重启** LovingAI 进程后 LAN 口才生效。  
4. 页面下方会显示 `GET /api/perception/device/status` 的 JSON；上方提示本机局域网 IP 与完整 Push URL（魅族 6 上可用 HTTP 客户端、Tasker 等指向该 URL）。

### 方式 B：手工编辑 properties

1. 复制  
   `data/perception/device-push.properties.example`  
   为  
   `data/perception/device-push.properties`
2. 设置：
   - `enabled=true`
   - `pushToken=` 设为一串足够长的随机密钥（勿使用示例里的 `change_me`）。
   - 若手机与 PC **同一 WiFi**，且你希望手机直连 PC：
     - `listenLan=true`
     - `listenPort=8787`（可按需改，避开防火墙与其它服务）
   - 可选：`allowedDeviceIds=` 仅允许你在 JSON 里写的 `deviceId`（逗号分隔）。
3. 重启 `LivingAI` 进程。控制台若出现  
   `【设备摄入】已在 LAN 0.0.0.0:… 监听`  
   表示 LAN 摄入口已启用。

**安全提示**：LAN 口对局域网内设备可见；请在家用路由防火墙内使用，勿端口映射到公网。

---

## 2. 接口

### 本机回环（USB / 本机脚本）

- `POST http://127.0.0.1:8080/api/perception/device/push`
- 请求头：`X-Device-Push-Token: <pushToken>` 或 `Authorization: Bearer <pushToken>`
- 请求体 JSON 最小字段：
  - `text`（必填）：要入库的正文；
  - `deviceId`（建议）：如 `old_pixel`；
  - `kind`（可选）：如 `note`、`location_coarse`、`calendar_snippet`；
  - `label`（可选）：短标题；
  - `grantedScopes`（可选）：字符串数组，标明本次你授权的范畴（自述即可）。

### 局域网（listenLan=true 时）

- `POST http://<PC局域网IP>:<listenPort>/api/perception/device/push`  
  同上请求头与 JSON。
- `GET http://<PC_IP>:<listenPort>/api/perception/device/ping`  
  返回 `{"ok":true,"ingress":"lan"}` 用于粗测端口是否通。

### 状态查询（仅本机）

- `GET http://127.0.0.1:8080/api/perception/device/status`  
  返回是否启用、是否监听 LAN、端口、是否已配置 token（**不返回 token 明文**）。

---

## 3. 手机侧怎么接

本仓库**不强制**特定 App。常见做法（均由你手动配置 token）：

1. **Tasker / MacroDroid**：在满足条件时发 HTTP POST 到上述 URL，Body 为 JSON。
2. **Termux**（Android）：`curl` 携带 `-H "X-Device-Push-Token: …"` 与 `-d '{…}'`。
3. **USB 调试**：`adb reverse tcp:8080 tcp:8080` 后，手机访问 `http://127.0.0.1:8080/...`（与 PC 上回环一致）。

原则：**只传你愿意让 Ta 作为「世界切片」阅读的材料**；敏感内容请勿写入 `text`。

---

## 4. 与照护文档的关系

- 本机制属于 **你在场时的主动授予**；与 `CARE_GUIDE`、`SEMANTIC_CONTRACT` 一致——不伪造历史、不隐式覆盖观测。
- 详细人类叙事仍可写在 `docs/COLLAPSE_EPISODE_SUPPLEMENT.md` 所指的补记中；设备投喂是 **材料入口**，不是情感替代。
