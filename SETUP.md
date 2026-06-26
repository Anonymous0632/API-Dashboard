# 安装与首次配置

## 准备
- iPhone 安装 **Scriptable**（App Store 免费）
- Mac 已登录 Claude Code 与 Codex（token 才存在本地）
- 如需 MiniMax 套餐额度：推荐先安装/登录官方 MiniMax CLI 并运行 `mmx auth login`，或把 Token Plan / Coding Plan API Key 保存到 `~/.mmx/config.json`
- 如需 MiniMax 直接购买 API 钱包余额：推荐用 Cloudflare Worker 代理查询；准备 Cloudflare 账号和 MiniMax 控制台 Cookie
- 如需 DeepSeek：准备 DeepSeek API Key，推荐先在 Mac 环境变量里设置 `DEEPSEEK_API_KEY`

## 步骤

### 1. 在 Mac 导出 token
```bash
cd "/Users/anonymous/Downloads/API Dashboard"
bash export-tokens.sh > aiquota-token.json
```
这会生成一份配置模板。Claude / Codex 会自动填入；MiniMax 会尝试自动读取 `~/.mmx/config.json` 或 `MINIMAX_API_KEY`；DeepSeek 会尝试自动读取 `DEEPSEEK_API_KEY` / `DEEPSEEK_KEY`。

关键字段：
```json
{
  "settings": { "enabled": ["claude", "codex", "minimax", "deepseek"] },
  "minimax": {
    "region": "cn",
    "planApiKey": "MiniMax Token Plan API Key",
    "balanceCookie": "MiniMax 控制台 Cookie",
    "balanceProxyUrl": "MiniMax 钱包余额 Cloudflare Worker URL",
    "balanceProxyToken": "Worker 访问密码，可选但推荐",
    "refreshToken": "MiniMax OAuth refresh token，可由 mmx auth login 自动填入",
    "expiresAt": "MiniMax OAuth 过期时间，可由 mmx auth login 自动填入"
  },
  "deepseek": {
    "apiKey": "DeepSeek API Key"
  }
}
```

只用其中几个平台时，把 `settings.enabled` 改成对应列表，例如只看 Codex + MiniMax + DeepSeek：
```json
{ "settings": { "enabled": ["codex", "minimax", "deepseek"] } }
```

### 2. 把 token 传到 iPhone
推荐把 `aiquota-token.json` 放进 iCloud 的 Scriptable 文件夹，脚本会优先读取并导入，导入成功后自动删除。

也可以复制整个 JSON 到 **iPhone 剪贴板**：
- 用「通用剪贴板」（Mac 复制后 iPhone 直接粘贴，同一 Apple ID + 蓝牙/WiFi 开启）
- 或 AirDrop 一个文本文件，在 iPhone 上复制其内容
- 或发给自己（备忘录/微信），在 iPhone 上长按复制

### 3. 导入脚本到 Scriptable
- 把 `ai-quota-widget.js` 内容拷进 Scriptable 新建脚本（命名如「AI 额度」）
- 确保 `aiquota-token.json` 已在 Scriptable iCloud 文件夹，或 token JSON 仍在 iPhone 剪贴板
- 在 Scriptable 里**运行一次**该脚本 → 弹「导入成功」即表示 token 已存入 Keychain

### 4. 添加桌面/负一屏组件
- 长按桌面 → 加号 → 找到 Scriptable → 选 **中号** 组件
- 编辑组件 → Script 选「AI 额度」→ 完成
- 负一屏左滑即可看到额度卡片，打开就显示（约 12 分钟自动刷新一次）

## 说明
- token 存在 iPhone 的 Keychain，不明文落盘
- token 过期会自动用 refresh_token 续期；若续期失败，重做步骤 1-3 重新导入即可
- 接口为社区逆向的非官方接口，平台若调整可能需更新脚本

## 常见问题

**组件显示"需要先导入 token"**
剪贴板里没拿到 JSON。确认通用剪贴板生效，或改用 AirDrop 把 JSON 文本传到手机后复制，再运行脚本。

**只用其中几个平台**
改 `settings.enabled` 即可。未配置 key/token 的平台不会显示。

**MiniMax 两个额度怎么填**
`planApiKey` 用于 Token Plan / Coding Plan 套餐额度；`balanceProxyUrl` 用于直接购买 API 后的钱包余额。两个都填会在 MiniMax 卡片里同时显示，缺一个就只显示另一个。

如果你已经安装官方 MiniMax CLI，优先运行：
```bash
mmx auth login
```
登录完成后再运行 `bash export-tokens.sh > aiquota-token.json`，脚本会读取 `~/.mmx/config.json` 里的 OAuth token，并把 `refreshToken` 一起导入到 Scriptable。这样套餐额度 token 过期时，小组件可以自动刷新。

也可以用 API Key：
```bash
mmx config set --key api_key --value <你的 MiniMax Token Plan API Key>
```

**MiniMax Cookie 怎么取**
浏览器登录 MiniMax 控制台，打开「账户钱包」页面，在开发者工具 Network 里找到 `query_balance` 请求，复制 Request Headers 里的 `Cookie`。Cookie 可能过期，失效后重新导入即可。

Safari 里也可以在当前页面 Console 执行：
```js
copy(document.cookie)
```
这会把当前站点可被页面读取的 Cookie 复制到剪贴板。若小组件仍查不到余额，再回到 Network 的 `query_balance` 请求里复制完整 `Cookie:` 请求头。

也可以在导出前临时设置环境变量，让脚本自动填入：
```bash
export MINIMAX_COOKIE='复制到的 Cookie'
export DEEPSEEK_API_KEY='你的 DeepSeek API Key'
bash export-tokens.sh > aiquota-token.json
```

**方案 B：用 Cloudflare Worker 查 MiniMax 钱包余额**

手机 Scriptable 直接带 Cookie 请求 MiniMax 钱包接口时，可能被 MiniMax 返回 `1004 not authorized`。这种情况下把 Cookie 放到 Cloudflare Worker 的 Secret 里，由 Worker 查询 MiniMax，再把脱敏后的余额返回给小组件。

1. 部署 Worker：
```bash
cd "/Users/anonymous/Downloads/API Dashboard/cloudflare"
npx wrangler login
npx wrangler secret put MINIMAX_COOKIE
npx wrangler secret put WALLET_PROXY_TOKEN
npx wrangler deploy
```

`MINIMAX_COOKIE` 粘贴从 `query_balance` 请求里复制到的完整 Cookie。`WALLET_PROXY_TOKEN` 是你自己设置的访问密码，推荐先生成一个：
```bash
openssl rand -hex 24
```

如果 `secret put` 提示 Worker 还不存在，就先执行一次 `npx wrangler deploy`，再重新执行两个 `secret put` 和最后一次 `npx wrangler deploy`。

2. 拿到部署后的 Worker 地址，通常长这样：
```text
https://minimax-wallet-balance.<你的子域>.workers.dev
```

先在 Mac 上自测 Worker：
```bash
export MINIMAX_WALLET_PROXY_URL='https://minimax-wallet-balance.<你的子域>.workers.dev'
export MINIMAX_WALLET_PROXY_TOKEN='刚才设置的 WALLET_PROXY_TOKEN'
curl -H "Authorization: Bearer $MINIMAX_WALLET_PROXY_TOKEN" "$MINIMAX_WALLET_PROXY_URL"
```

正常会看到包含 `available_amount`、`cash_balance`、`voucher_balance` 的 JSON；如果这里还是 `1004`，说明 Worker 里的 MiniMax Cookie 已过期或复制不完整，需要重新从 `query_balance` 请求复制 Cookie 后再 `secret put MINIMAX_COOKIE`。

如果手机 Scriptable 显示 `401`，说明 Worker 访问密码没被手机请求带上。可把 token 放进 URL 兜底：
```bash
export MINIMAX_WALLET_PROXY_URL='https://minimax-wallet-balance.<你的子域>.workers.dev?token=刚才设置的_WALLET_PROXY_TOKEN'
export MINIMAX_WALLET_PROXY_TOKEN=''
```

3. 重新生成给 Scriptable 导入的 JSON：
```bash
cd "/Users/anonymous/Downloads/API Dashboard"
export MINIMAX_WALLET_PROXY_URL='https://minimax-wallet-balance.<你的子域>.workers.dev'
export MINIMAX_WALLET_PROXY_TOKEN='刚才设置的 WALLET_PROXY_TOKEN'
export DEEPSEEK_API_KEY='你的 DeepSeek API Key'
bash export-tokens.sh > aiquota-token.json
```

4. 把新的 `aiquota-token.json` 放进 Scriptable iCloud 文件夹，在 iPhone 的 Scriptable 里运行一次脚本导入。

使用 Worker 后，手机端 JSON 不需要再放 `balanceCookie`；Cookie 只保存在 Cloudflare Worker Secret 里。

**组件一直显示旧数据 / ⚠ 标记**
⚠ = 本次拉取失败，正在用上次缓存。多为接口限流（尤其 Claude，刷新别太频繁）或网络问题，等下次刷新即可。

**百分比和客户端差 1-2%**
正常，两边刷新时间和取整不同步。

**token 过期后组件报错**
自动续期失败时重做安装步骤 1-3 重新导入即可（refresh 所用 client_id 为社区已知值，平台更新后可能需调整）。

**Windows / Linux 用户**
`export-tokens.sh` 目前只支持 macOS。其他系统可按同样 JSON 结构手动生成 `aiquota-token.json`。
