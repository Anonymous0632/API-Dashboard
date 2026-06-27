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
| 16 | Android 模拟器视觉与交互检视 | 完成 |
| 17 | Android 真实凭证导入与接口刷新联调 | 完成 |

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
- 在 Android 模拟器中安装 APK、启动 App，并成功把 `API Dashboard` 4x2 小组件添加到桌面。
- 使用非敏感假缓存验证 Android 小组件 4 平台 2x2 布局，Claude / Codex / MiniMax / DeepSeek 卡片均可显示。
- 缩短 Android MiniMax 钱包明细文案，将 `代金券` 简化为 `券`，避免 4x2 小组件内文案被截断。
- 在模拟器中通过系统文件选择器导入本地 `aiquota-token.json`，App 显示刷新完成。
- 真实配置导入后，桌面小组件可显示当前启用的 Codex / MiniMax / DeepSeek 平台卡片；测试结束后已删除模拟器内 JSON 并卸载测试 App。
- README 补充 Android 原生小组件部署步骤，覆盖构建 APK、`adb install`、App 内导入 JSON 和添加 4x2 桌面小组件。

## 验证记录

- `node --check ai-quota-widget.js`：通过
- `node --check cloudflare/minimax-wallet-worker.js`：通过
- `bash -n export-tokens.sh`：通过
- `git diff --check`：通过
- MiniMax Worker 正确 token 请求：返回余额数据
- MiniMax Worker 错误 token 请求：返回 401
- Android 命令行构建：`gradle :app:assembleDebug` 通过，生成 `android/app/build/outputs/apk/debug/app-debug.apk`
- Android Wrapper 构建：`./gradlew :app:assembleDebug` 通过
- Android debug APK SHA-256：`edef7400768f8c35f29ffe4bf1687c10d8e5657f7526a9761af3dbb2654811f6`
- Android App 启动检视：模拟器通过，配置导入页正常显示
- Android 小组件添加检视：模拟器通过，Launcher 可识别 `API Dashboard` 4x2 小组件并添加到桌面
- Android 4 平台布局检视：模拟器通过，使用非敏感假缓存验证 2x2 卡片布局
- Android 真实凭证接口联调：模拟器通过；未在日志或文档中输出真实 token/API Key/余额数值

## 发布准备

- 敏感配置文件已加入 `.gitignore`。
- 本地 `aiquota-token.json` 不应提交。
- Cloudflare Worker Secret 不应写入仓库。
- GitHub 发布应使用新的干净提交历史，不沿用旧仓库历史。
- Android 本地 `local.properties`、`.gradle/`、`build/` 已加入 `.gitignore`。
