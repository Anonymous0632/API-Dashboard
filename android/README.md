# API Dashboard Android

安卓端是一个独立原生工程，用于在 Android 桌面小组件中显示与 iOS Scriptable 版一致的 AI 额度与余额。

## 功能

- 导入 `export-tokens.sh` 生成的 `aiquota-token.json`
- 凭证写入 Android 加密存储，不明文落盘
- 支持 Claude Code、Codex、MiniMax、DeepSeek
- MiniMax 钱包余额优先使用 Cloudflare Worker 代理
- Glance 桌面小组件显示 1-4 个平台卡片
- WorkManager 后台定时刷新，App 内可手动刷新

## 开发与运行

1. 用 Android Studio 打开本目录 `android/`
2. 等待 Gradle 同步完成
3. 运行 `app` 到安卓手机或模拟器
4. 在 App 内粘贴或选择 `aiquota-token.json` 导入
5. 添加桌面小组件 `API Dashboard`

命令行验证：

```bash
cd android
gradle :app:assembleDebug
```

当前仓库没有提交 Gradle Wrapper。可用 Android Studio 直接同步构建，或在安装 JDK 17、Android SDK 和本机 Gradle 后执行上面的命令。
