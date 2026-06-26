# 项目进度

## 当前状态

API Dashboard 已完成主要功能开发，并已在 iPhone Scriptable 小组件中完成实际显示验证。当前版本支持 Claude Code、Codex、MiniMax 和 DeepSeek，可按需选择展示平台。

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

## 近期变更

### 2026-06-27

- 完成 MiniMax 钱包余额 Cloudflare Worker 方案。
- Worker 支持 `Authorization`、`X-Widget-Token` 和 `?token=` 三种访问密码传递方式。
- MiniMax Cookie 改为保存在 Cloudflare Secret 中，小组件不再保存该 Cookie。
- 修复 MiniMax 钱包接口在移动端直连时的授权失败问题。
- 优化卡片内部纵向排布，使标题、额度和余额更均匀分布。
- 增加余额颜色阈值：低于 5 元显示红色，5-20 元显示橙色。
- 将项目文档重写为正式公开版本，并移除内部调试记录与历史说明。

## 验证记录

- `node --check ai-quota-widget.js`：通过
- `node --check cloudflare/minimax-wallet-worker.js`：通过
- `bash -n export-tokens.sh`：通过
- `git diff --check`：通过
- MiniMax Worker 正确 token 请求：返回余额数据
- MiniMax Worker 错误 token 请求：返回 401

## 发布准备

- 敏感配置文件已加入 `.gitignore`。
- 本地 `aiquota-token.json` 不应提交。
- Cloudflare Worker Secret 不应写入仓库。
- GitHub 发布应使用新的干净提交历史，不沿用旧仓库历史。
