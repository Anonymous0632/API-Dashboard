#!/bin/bash
# 在 Mac 上运行，读取本地 Claude / Codex 的 OAuth token，
# 输出一段 JSON 供 iPhone Scriptable 一次性导入（存入 Keychain）。
# 用法：bash export-tokens.sh > aiquota-token.json
set -euo pipefail

# Claude：OAuth 存在 macOS 钥匙串 "Claude Code-credentials"
CLAUDE_JSON=$(security find-generic-password -s "Claude Code-credentials" -w 2>/dev/null || echo "{}")

# Codex：OAuth 存在 ~/.codex/auth.json
CODEX_JSON=$(cat "$HOME/.codex/auth.json" 2>/dev/null || echo "{}")

# MiniMax：官方 mmx CLI 存在 ~/.mmx/config.json；环境变量优先
MMX_CONFIG_PATH="${MMX_CONFIG_DIR:-$HOME/.mmx}/config.json"
MINIMAX_JSON=$(cat "$MMX_CONFIG_PATH" 2>/dev/null || echo "{}")

CLAUDE_JSON="$CLAUDE_JSON" CODEX_JSON="$CODEX_JSON" MINIMAX_JSON="$MINIMAX_JSON" python3 <<'PY'
import json, os

def parse_json(raw):
    try:
        return json.loads(raw or "{}")
    except Exception:
        return {}

def first(*values):
    for v in values:
        if isinstance(v, str) and v.strip():
            return v.strip()
    return ""

def region_from(*values):
    for v in values:
        if not isinstance(v, str):
            continue
        raw = v.strip().lower()
        if raw in ("cn", "china", "china-mainland"):
            return "cn"
        if "minimaxi.com" in raw:
            return "cn"
        if raw in ("global", "intl", "international") or "minimax.io" in raw:
            return "global"
    return "cn"

claude_src = json.loads(os.environ["CLAUDE_JSON"] or "{}").get("claudeAiOauth", {})
codex_src = parse_json(os.environ["CODEX_JSON"]).get("tokens", {})
minimax_src = parse_json(os.environ["MINIMAX_JSON"])
minimax_oauth = minimax_src.get("oauth") or {}

minimax_region = region_from(
    os.environ.get("MINIMAX_REGION"),
    minimax_src.get("region"),
    minimax_oauth.get("region"),
    minimax_src.get("base_url"),
    minimax_oauth.get("resource_url"),
)
minimax_plan_key = first(
    os.environ.get("MINIMAX_CODING_API_KEY"),
    os.environ.get("MINIMAX_API_KEY"),
    minimax_src.get("api_key"),
    minimax_oauth.get("access_token"),
)
minimax_cookie = first(
    os.environ.get("MINIMAX_COOKIE_HEADER"),
    os.environ.get("MINIMAX_COOKIE"),
)
minimax_wallet_proxy_url = first(
    os.environ.get("MINIMAX_WALLET_PROXY_URL"),
    os.environ.get("MINIMAX_BALANCE_PROXY_URL"),
)
minimax_wallet_proxy_token = first(
    os.environ.get("MINIMAX_WALLET_PROXY_TOKEN"),
    os.environ.get("MINIMAX_BALANCE_PROXY_TOKEN"),
)
deepseek_key = first(os.environ.get("DEEPSEEK_API_KEY"), os.environ.get("DEEPSEEK_KEY"))

out = {
    "settings": {
        "enabled": []
    },
    "claude": {
        "accessToken": claude_src.get("accessToken", ""),
        "refreshToken": claude_src.get("refreshToken", ""),
        "expiresAt": claude_src.get("expiresAt", 0),
    },
    "codex": {
        "accessToken": codex_src.get("access_token", ""),
        "refreshToken": codex_src.get("refresh_token", ""),
        "accountId": codex_src.get("account_id", ""),
    },
    "minimax": {
        "region": minimax_region,
        "planApiKey": minimax_plan_key,
        "balanceCookie": minimax_cookie,
        "balanceProxyUrl": minimax_wallet_proxy_url,
        "balanceProxyToken": minimax_wallet_proxy_token,
        "authorizationToken": first(minimax_oauth.get("access_token")),
        "refreshToken": first(minimax_oauth.get("refresh_token")),
        "expiresAt": minimax_oauth.get("expires_at") or None,
    },
    "deepseek": {
        "apiKey": deepseek_key
    },
}
if out["claude"]["accessToken"]:
    out["settings"]["enabled"].append("claude")
if out["codex"]["accessToken"]:
    out["settings"]["enabled"].append("codex")
if out["minimax"]["planApiKey"] or out["minimax"]["balanceCookie"] or out["minimax"]["balanceProxyUrl"]:
    out["settings"]["enabled"].append("minimax")
if out["deepseek"]["apiKey"]:
    out["settings"]["enabled"].append("deepseek")
# 简单校验
miss = [k for k in ("claude","codex") if not out[k]["accessToken"]]
if miss:
    import sys
    print(f"# 警告：未读到 {miss} 的 accessToken，请确认已登录对应客户端", file=sys.stderr)
if not out["minimax"]["planApiKey"]:
    import sys
    print("# 提示：未读到 MiniMax Token Plan key。可运行 mmx auth login / mmx config set --key api_key --value <key>，或设置 MINIMAX_API_KEY", file=sys.stderr)
if not out["minimax"]["balanceProxyUrl"] and not out["minimax"]["balanceCookie"]:
    import sys
    print("# 提示：未读到 MiniMax 钱包余额配置。可设置 MINIMAX_WALLET_PROXY_URL，或设置 MINIMAX_COOKIE", file=sys.stderr)
if not out["deepseek"]["apiKey"]:
    import sys
    print("# 提示：未读到 DeepSeek API Key。可设置 DEEPSEEK_API_KEY 或手动填入 deepseek.apiKey", file=sys.stderr)
print(json.dumps(out, ensure_ascii=False))
PY
