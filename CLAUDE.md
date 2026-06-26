# Development Notes

## Scope

This repository contains a Scriptable-based iOS widget and an optional Cloudflare Worker for MiniMax wallet balance retrieval.

## Local Development

- Keep code changes focused and avoid unrelated refactors.
- Use JavaScript compatible with the Scriptable runtime.
- Prefer concise Chinese comments only where they clarify non-obvious behavior.
- Run syntax checks before publishing:

```bash
node --check ai-quota-widget.js
node --check cloudflare/minimax-wallet-worker.js
bash -n export-tokens.sh
git diff --check
```

## Security

- Do not commit account tokens, API keys, cookies, or generated local config.
- Keep `aiquota-token.json`, `.env`, `.dev.vars`, and Cloudflare local state ignored.
- Store MiniMax Cookie as a Cloudflare Secret when using the Worker path.
- Store mobile-side credentials in Scriptable Keychain.
