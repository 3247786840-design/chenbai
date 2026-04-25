# USB 有线伴侣（手机只记事，本体在 PC）

## 原则

- **LovingAI 与生命体推理仍在 PC**；手机仅作为你愿意保留的「外触记事 / 缓冲」。
- 传输走 **USB + adb**（需手机开启 **开发者选项 → USB 调试**），不依赖手机安装完整 LovingAI。
- PC 端 **「设备与语音」** 标签页提供：检测 adb、**选择 APK 并安装**、`adb pull` 到 `data/perception/usb-inbox`、**将 usb-inbox 导入资料库**。

## 1. PC 环境

1. 安装 [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)，确保命令行能执行 `adb`（或将完整路径填进界面里的 **adb** 框，例如 `C:\platform-tools\adb.exe`）。
2. 手机用数据线连接 PC，授权此计算机调试。
3. 在 LovingAI 界面点击 **检测 adb**、**列出设备**，确认出现 `device` 状态。

## 2. 伴侣 APK

仓库提供**极简**源码（仅追加文本到应用专属目录），需在本机用 **Android Studio** 新建空项目后将源文件拷入并编译，得到 `app-debug.apk`：

- 目录：`LovingAI/android/usb-hook-notepad/`

安装方式：

- 在 **「设备与语音」** 点击 **「选择 APK 并安装到手机」**，选中你编译好的 APK（等价于 `adb install -r -d xxx.apk`）。

## 3. 拉取与导入

1. 在手机上打开伴侣应用，将片段 **追加** 到导出文件（界面内显示完整路径）。
2. 将该路径填到 PC 界面 **「手机端导出文件路径」**（默认与示例包名 `com.lovingai.usbhook` 一致，若你改包名请同步修改）。
3. 点击 **「adb pull 到 usb-inbox」**。
4. 点击 **「将 usb-inbox 导入资料库」**，文本会进入与「导入 / 外触」相同的资料索引，供对话检索。

## 4. 与 WiFi token 推送的关系

- **USB**：离线友好、不暴露局域网端口；适合「旧机只当记事本」。
- **WiFi + device/push**：实时推送短文本，见 `DEVICE_PHONE_INGEST.md`。

二者可同时存在，由你选择使用场景。
