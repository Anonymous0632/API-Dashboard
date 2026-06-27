# API Dashboard 项目说明

## 1. 项目定位

API Dashboard 是一个面向个人使用的手机桌面小组件项目，用于集中查看多家 AI 服务的额度或账户余额。项目优先保证部署简单、凭证本地保存、显示信息直接可读。

当前 iOS 实现基于 Scriptable，不依赖 Apple Developer Program。Android 端新增原生 Kotlin 工程，使用 Jetpack Glance 提供桌面小组件。除 MiniMax 钱包余额可选 Cloudflare Worker 代理外，其余数据由手机端直接请求。

## 2. 支持范围

| 平台 | 展示内容 | 凭证来源 |
| --- | --- | --- |
| Claude Code | 5 小时额度、本周额度、恢复时间 | macOS Keychain 中的 Claude Code OAuth 信息 |
| Codex | 5 小时额度、本周额度、恢复时间 | `~/.codex/auth.json` |
| MiniMax | Token Plan 套餐额度、账户钱包余额 | MiniMax API Key、MiniMax Cookie 或 Worker 代理 |
| DeepSeek | API 余额、充值余额、赠送余额 | DeepSeek API Key |

`settings.enabled` 用于选择要显示的平台。未启用或未配置凭证的平台不会出现在小组件中。

## 3. 客户端实现

| 平台端 | 目录/文件 | 运行方式 | 凭证存储 |
| --- | --- | --- | --- |
| iOS | `ai-quota-widget.js` | Scriptable 中号小组件 | Scriptable Keychain |
| Android | `android/` | 原生 App + Glance 桌面小组件 | EncryptedSharedPreferences / Android Keystore |

Android App 负责导入 `aiquota-token.json`、触发立即刷新和安排后台刷新；Glance 小组件读取缓存快照展示。后台刷新使用 WorkManager，周期为 Android 允许的 15 分钟级别；桌面小组件点按会发起一次即时刷新任务。Android 工程当前使用 `compileSdk 36`、`targetSdk 35`，避免较新的 AndroidX 依赖强制改变运行时目标行为。

## 4. 数据接口

| 数据 | 请求地址 | 认证方式 |
| --- | --- | --- |
| Claude Code usage | `https://api.anthropic.com/api/oauth/usage` | Bearer OAuth access token |
| Codex usage | `https://chatgpt.com/backend-api/wham/usage` | Bearer access token + `chatgpt-account-id` |
| MiniMax Token Plan | `https://api.minimax.io/v1/token_plan/remains` 或 `https://api.minimaxi.com/v1/token_plan/remains` | MiniMax API Key |
| MiniMax wallet | `https://www.minimax.io/account/query_balance` 或 `https://www.minimaxi.com/account/query_balance` | 控制台 Cookie；推荐放入 Worker Secret |
| DeepSeek balance | `https://api.deepseek.com/user/balance` | DeepSeek API Key |

Claude Code 和 Codex 的接口返回使用比例，脚本统一换算为剩余比例。MiniMax 和 DeepSeek 的余额接口返回金额字段，脚本优先展示可用余额。

## 5. MiniMax Worker 方案

MiniMax 钱包接口对移动端或非浏览器请求较敏感，Scriptable 直连可能返回 `1004 not authorized`。因此项目提供 Cloudflare Worker：

- `MINIMAX_COOKIE` 保存到 Worker Secret
- `WALLET_PROXY_TOKEN` 作为访问密码
- Worker 查询 MiniMax 钱包接口后只返回余额字段
- 小组件保存 Worker URL 和访问密码，不保存 MiniMax Cookie
- 如果请求头传递不稳定，可使用 `?token=` URL 参数兜底

Worker 返回字段包括 `available_amount`、`cash_balance`、`voucher_balance`、`credit_balance`、`owed_amount` 和 `updated_at`。

## 6. 凭证处理

- `export-tokens.sh` 负责在 macOS 上生成一次性导入 JSON。
- Scriptable 首次运行时会读取 iCloud 目录中的 `aiquota-token.json` 或剪贴板 JSON。
- 导入成功后，凭证写入 Scriptable Keychain。
- `aiquota-token.json` 会被脚本删除，避免反复覆盖已刷新的凭证。
- Android 首次运行时通过 App 内文本框、剪贴板或系统文件选择器导入同一份 JSON。
- Android 导入成功后，凭证写入加密 SharedPreferences；小组件只读取非敏感缓存快照。
- 仓库 `.gitignore` 明确排除本地 token、环境变量和 Cloudflare 本地调试密钥。

Android 部署流程已写入 README：生成 `aiquota-token.json`，在 `android/` 下执行 `./gradlew :app:assembleDebug`，将 `app/build/outputs/apk/debug/app-debug.apk` 安装到手机，再在 App 内导入 JSON 并添加 4x2 桌面小组件。

## 7. 小组件布局

- 中号组件为主目标尺寸。
- 1-3 个平台时单行等宽展示；4 个平台时自动拆为 2x2。
- 每张卡片固定宽高，内部用弹性间距分配标题、用量和余额。
- 额度百分比使用绿色、橙色、红色表达风险等级。
- 金额低于 5 元显示红色，5-20 元显示橙色，高于 20 元使用平台主题色。
- 数据请求失败时优先回退缓存，不影响其他平台展示。
- Android Glance 版遵循同样的信息结构：平台名、主要额度/余额、次要额度/余额、恢复或明细文案。

## 8. 已知限制

- 部分接口不是官方公开稳定 API，平台调整后可能需要维护。
- iOS 小组件刷新由系统调度，无法保证实时刷新。
- Android 后台刷新受 WorkManager 与系统省电策略调度，无法保证分钟级实时刷新。
- MiniMax Cookie 可能提前失效，过期后需要更新 Cloudflare Secret。
- Claude Code / Codex 的接口只提供比例和恢复时间，不提供完整 token 计数。
- Android APK 已在临时 JDK 17 + Android SDK 36 + Gradle 8.14.3 工具链下完成命令行构建；模拟器已验证 App 启动、Launcher 识别 4x2 小组件、桌面添加、4 平台 2x2 布局，以及本地 `aiquota-token.json` 导入后的真实接口刷新链路。测试过程不输出真实 token/API Key/余额数值，测试结束后已删除模拟器内 JSON 并卸载测试 App。

## 9. 验证清单

- `node --check ai-quota-widget.js`
- `node --check cloudflare/minimax-wallet-worker.js`
- `bash -n export-tokens.sh`
- `git diff --check`
- Worker 使用正确 token 返回余额，错误 token 返回 401
- Android 工程：安装 Android Studio 后同步构建，或执行 `cd android && ./gradlew :app:assembleDebug`
- Android 构建产物：`android/app/build/outputs/apk/debug/app-debug.apk`，构建产物不提交仓库
- Android 模拟器检视：安装 debug APK 后，桌面可添加 `API Dashboard` 4x2 小组件；非敏感假缓存下 4 平台卡片布局通过
- Android 真实导入检视：通过系统文件选择器导入本地 `aiquota-token.json`，App 刷新完成，桌面小组件显示当前启用的平台卡片
