# 项目进度

## 当前状态

API Dashboard 已完成 iPhone Scriptable 小组件主要功能开发，并新增原生 Android 小组件工程。当前版本支持 Claude Code、Codex、MiniMax 和 DeepSeek，可按需选择展示平台。

## 任务清单

| 序号 | 事项 | 状态 |
| --- | --- | --- |
| 1 | Scriptable 小组件基础结构 | 完成 |
| 2 | Claude Code 与 Codex 额度展示 | 完成 |
| 3 | MiniMax Token Plan 额度展示 | 完成 |
| 4 | MiniMax 钱包余额展示 | 完成 |
| 5 | DeepSeek API 余额展示 | 完成 |
| 6 | 多平台启用/隐藏配置 | 完成 |
| 7 | iCloud JSON 导入与 Keychain 存储 | 完成 |
| 8 | Cloudflare Worker 代理 MiniMax 钱包接口 | 完成 |
| 9 | 小组件等宽布局与颜色阈值 | 完成 |
| 10 | 文档、许可与公开发布前整理 | 完成 |
| 11 | Android 原生工程目录与 Gradle 配置 | 完成 |
| 12 | Android JSON 导入与加密存储 | 完成 |
| 13 | Android 四个平台数据层与缓存 | 完成 |
| 14 | Android Glance 桌面小组件与 WorkManager 刷新 | 完成 |
| 15 | Android 命令行 APK 构建验证 | 完成 |
| 16 | Android 真机/模拟器视觉与交互检视 | 待验证 |

## 近期变更

### 2026-06-27

- 完成 MiniMax 钱包余额 Cloudflare Worker 方案。
- Worker 支持 `Authorization`、`X-Widget-Token` 和 `?token=` 三种访问密码传递方式。
- MiniMax Cookie 改为保存在 Cloudflare Secret 中，小组件不再保存该 Cookie。
- 修复 MiniMax 钱包接口在移动端直连时的授权失败问题。
- 优化卡片内部纵向排布，使标题、额度和余额更均匀分布。
- 增加余额颜色阈值：低于 5 元显示红色，5-20 元显示橙色。
- 将项目文档重写为正式公开版本，并移除内部调试记录与历史说明。

### 2026-06-28

- 新增 `android/` 原生 Android 工程，使用 Kotlin + Jetpack Glance 实现桌面小组件。
- Android App 支持粘贴、剪贴板和系统文件选择器导入 `aiquota-token.json`。
- Android 端凭证写入 EncryptedSharedPreferences / Android Keystore，小组件只读取缓存快照。
- 移植 Claude Code、Codex、MiniMax、DeepSeek 数据请求逻辑，保留 token 刷新、MiniMax Worker 代理和失败回退缓存。
- 新增 WorkManager 后台刷新；点按小组件可触发一次刷新任务。
- 更新公开 README、项目说明和安装说明，补充 Android 使用方式。
- 修复 Android 构建配置，补充 `android.useAndroidX=true`，并将 `compileSdk` 调整为 36 以满足当前 AndroidX 依赖要求。
- 按 Glance 1.1.1 API 修正 `provideGlance`、`updateAll` 和点击回调写法。

## 验证记录

- `node --check ai-quota-widget.js`：通过
- `node --check cloudflare/minimax-wallet-worker.js`：通过
- `bash -n export-tokens.sh`：通过
- `git diff --check`：通过
- MiniMax Worker 正确 token 请求：返回余额数据
- MiniMax Worker 错误 token 请求：返回 401
- Android 命令行构建：`gradle :app:assembleDebug` 通过，生成 `android/app/build/outputs/apk/debug/app-debug.apk`
- Android debug APK SHA-256：`2c28c9dc17675c9e1f632bc7bcb727b74f224b616c6bbcbc834f16b7c2e78d9d`
- Android 真机/模拟器视觉检视：待验证，当前环境未提供可运行的安卓设备或 `test-android-apps` 工具

## 发布准备

- 敏感配置文件已加入 `.gitignore`。
- 本地 `aiquota-token.json` 不应提交。
- Cloudflare Worker Secret 不应写入仓库。
- GitHub 发布应使用新的干净提交历史，不沿用旧仓库历史。
- Android 本地 `local.properties`、`.gradle/`、`build/` 已加入 `.gitignore`。
