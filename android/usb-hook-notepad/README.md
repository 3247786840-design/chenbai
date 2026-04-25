# usb-hook-notepad — 极简 USB 导出记事（非 LovingAI 本体）

## 作用

- 在旧 Android 上**只追加文本行**到应用专属目录下的 `lovingai_usb_notes.txt`。
- **不包含** PC 端生命体逻辑；便于你用数据线 + PC 端 `adb pull` 拉回 `data/perception/usb-inbox`。

## 使用 Android Studio 接入

1. **新建 Empty Activity 项目**，包名设为 **`com.lovingai.usbhook`**（若改名，请同步修改 PC 界面里的「手机端导出文件路径」）。
2. `minSdk` 建议 **21**（覆盖魅族 6 等老机）。
3. 将本目录 `app/src/main/java/com/lovingai/usbhook/MainActivity.java` 与 `AndroidManifest.xml` 覆盖到工程中对应位置。
4. Build → Build APK(s)，将生成的 **debug APK** 用 PC 端 **「选择 APK 并安装到手机」** 安装。

## 导出路径

运行应用后，界面底部显示**绝对路径**，典型形如：

`/storage/emulated/0/Android/data/com.lovingai.usbhook/files/lovingai_usb_notes.txt`

（具体以实机显示为准。）
