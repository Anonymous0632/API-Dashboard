# API Dashboard

一个运行在 iPhone Scriptable 小组件里的 AI API 额度看板。它把 Claude Code、Codex、MiniMax 和 DeepSeek 的额度或余额集中显示在桌面/负一屏，适合需要频繁确认账户余量的 AI 工具重度用户。

## 功能概览

- 支持按需启用 `claude`、`codex`、`minimax`、`deepseek`
- Claude Code / Codex：显示 5 小时窗口、本周窗口的剩余额度和恢复时间
- MiniMax：支持 Token Plan 套餐额度，也支持账户钱包余额
- DeepSeek：显示 API 账户余额，并拆分充值余额与赠送余额
- 中号小组件自适应布局：1-3 个平台单行均分，4 个平台自动 2x2
- 金额低于 5 元时高亮为红色，5-20 元显示橙色
- Token 与 API Key 存入 Scriptable Keychain，不写入脚本
- MiniMax 钱包余额可通过 Cloudflare Worker 代理查询，避免手机端 Cookie 请求失败

## 截图

![API Dashboard widget](docs/screenshot.png)

## 文件说明

| 文件 | 说明 |
| --- | --- |
| `ai-quota-widget.js` | Scriptable 小组件主脚本 |
| `export-tokens.sh` | macOS 端导出配置的辅助脚本 |
| `cloudflare/minimax-wallet-worker.js` | MiniMax 钱包余额查询代理 |
| `cloudflare/wrangler.toml` | Cloudflare Worker 部署配置 |
| `SETUP.md` | 安装、导入与 Worker 配置步骤 |
| `PROJECT.md` | 技术方案与接口说明 |
| `PROGRESS.md` | 当前版本状态与变更记录 |

## 快速开始

1. 在 iPhone 安装 Scriptable。
2. 在 Mac 上进入项目目录并导出配置：

```bash
bash export-tokens.sh > aiquota-token.json
```

3. 将 `ai-quota-widget.js` 放入 Scriptable iCloud 目录。
4. 将 `aiquota-token.json` 放入 Scriptable iCloud 目录，在 iPhone 的 Scriptable 中运行脚本一次完成导入。
5. 在桌面或负一屏添加 Scriptable 中号组件，并选择该脚本。

更完整的 MiniMax Worker、DeepSeek、手动配置说明见 [SETUP.md](SETUP.md)。

## 安全说明

- `aiquota-token.json`、`.env`、`.dev.vars` 等包含敏感信息的文件已在 `.gitignore` 中排除。
- 小组件只在用户设备上保存凭证；Scriptable 端使用 Keychain。
- Cloudflare Worker 只应保存 MiniMax Cookie 到 Secret，不应把 Cookie 写入仓库。
- 相关接口包含非公开或非稳定的账户查询接口，平台改版后可能需要调整脚本。

## 许可证

MIT License. See [LICENSE](LICENSE).
