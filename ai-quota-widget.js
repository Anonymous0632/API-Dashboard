// AI 额度小组件 — Scriptable
// 在 iPhone 桌面/负一屏显示 Claude Code / Codex / MiniMax / DeepSeek 的额度与余额
// 纯手机本地运行，token 存 Keychain，Mac 关机也能用
//
// 首次使用：见 SETUP.md。简述——
//   1) 在 Mac 跑 export-tokens.sh，复制输出的 JSON
//   2) 把 JSON 拷到 iPhone 剪贴板
//   3) 在 Scriptable 里运行本脚本一次，自动导入 Keychain
//   4) 桌面添加「中号」Scriptable 组件，选本脚本

// ============ 配置 ============
const KC_CLAUDE = "aiquota.claude"; // Keychain key：{accessToken, refreshToken, expiresAt}
const KC_CODEX = "aiquota.codex";   // Keychain key：{accessToken, refreshToken, accountId}
const KC_SETTINGS = "aiquota.settings"; // Keychain key：{enabled:["claude","codex","minimax","deepseek"]}
const KC_MINIMAX = "aiquota.minimax";   // Keychain key：{region, planApiKey, balanceCookie, authorizationToken}
const KC_DEEPSEEK = "aiquota.deepseek"; // Keychain key：{apiKey}
const CACHE_FILE = "aiquota-cache.json"; // 拉取失败时回退用的本地缓存

// 社区已知的 OAuth client_id 与刷新端点（token 过期时用 refresh_token 续期）
// 注：非官方，若刷新失败请按 SETUP.md 重新导入 token
const CLAUDE_REFRESH_URL = "https://console.anthropic.com/v1/oauth/token";
const CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e";
const CODEX_REFRESH_URL = "https://auth.openai.com/oauth/token";
const CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
const MINIMAX_CLIENT_ID = "659cf4c1-615c-45f6-a5f6-4bf15eb476e5";
const PROVIDERS = ["claude", "codex", "minimax", "deepseek"];

// ============ Keychain 读写 ============
function kcGet(key) {
  if (!Keychain.contains(key)) return null;
  try { return JSON.parse(Keychain.get(key)); } catch (e) { return null; }
}
function kcSet(key, obj) { Keychain.set(key, JSON.stringify(obj)); }

function cleanStr(v) {
  return typeof v === "string" ? v.trim() : "";
}
function cleanCookie(v) {
  return cleanStr(v).replace(/&amp;/g, "&");
}
function normalizeEnabled(v) {
  if (Array.isArray(v)) return v.filter((x) => PROVIDERS.includes(x));
  if (v && typeof v === "object") {
    let onlyTrue = PROVIDERS.filter((x) => v[x] === true);
    return onlyTrue.length > 0 ? onlyTrue : PROVIDERS.filter((x) => v[x] !== false);
  }
  return null;
}
function getSettings() {
  return kcGet(KC_SETTINGS) || {};
}
function providerEnabled(settings, id) {
  let enabled = normalizeEnabled(settings.enabled);
  if (!enabled || enabled.length === 0) return true; // 兼容旧用户：未配置开关时默认全开
  return enabled.includes(id);
}
function hasAnyCredential() {
  return !!(
    kcGet(KC_CLAUDE)?.accessToken ||
    kcGet(KC_CODEX)?.accessToken ||
    kcGet(KC_MINIMAX)?.planApiKey ||
    kcGet(KC_MINIMAX)?.balanceProxyUrl ||
    kcGet(KC_MINIMAX)?.balanceCookie ||
    kcGet(KC_DEEPSEEK)?.apiKey
  );
}

function importSettings(parsed, imported) {
  let enabled = normalizeEnabled(parsed?.settings?.enabled ?? parsed?.enabled);
  if (enabled) {
    kcSet(KC_SETTINGS, { enabled });
    imported.push("显示设置");
  }
}
function importMiniMax(parsed, imported) {
  let src = parsed?.minimax || {};
  let planApiKey = cleanStr(src.planApiKey || src.tokenPlanApiKey || src.codingApiKey || src.codingPlanApiKey);
  let balanceCookie = cleanCookie(src.balanceCookie || src.apiCookie || src.cookie);
  let balanceProxyUrl = cleanStr(src.balanceProxyUrl || src.walletProxyUrl || src.proxyUrl);
  let balanceProxyToken = cleanStr(src.balanceProxyToken || src.walletProxyToken || src.proxyToken);
  let authorizationToken = cleanStr(src.authorizationToken || src.accessToken);
  if (!planApiKey && !balanceCookie && !balanceProxyUrl) return;
  kcSet(KC_MINIMAX, {
    region: cleanStr(src.region) === "cn" ? "cn" : "global",
    planApiKey,
    balanceCookie,
    balanceProxyUrl,
    balanceProxyToken,
    authorizationToken,
    refreshToken: cleanStr(src.refreshToken),
    expiresAt: src.expiresAt || null,
  });
  imported.push("MiniMax");
}
function importDeepSeek(parsed, imported) {
  let apiKey = cleanStr(parsed?.deepseek?.apiKey || parsed?.deepseek?.key);
  if (!apiKey) return;
  kcSet(KC_DEEPSEEK, { apiKey });
  imported.push("DeepSeek");
}

// ============ 首次引导：从剪贴板导入 token ============
// 期望剪贴板里是 export-tokens.sh 输出的 JSON：
// { "settings": {enabled:[...]}, "claude": {...}, "codex": {...}, "minimax": {...}, "deepseek": {...} }
async function bootstrapIfNeeded() {
  let imported = [];

  // 1) 优先从 iCloud 同步的 token 文件导入（绕开剪贴板，最可靠）
  //    Mac 端把 aiquota-token.json 放进 Scriptable 的 iCloud 文件夹即自动同步过来
  try {
    let fm = FileManager.iCloud();
    let p = fm.joinPath(fm.documentsDirectory(), "aiquota-token.json");
    if (fm.fileExists(p)) {
      if (!fm.isFileDownloaded(p)) await fm.downloadFileFromiCloud(p);
      let parsed = JSON.parse(fm.readString(p));
      if (parsed?.claude?.accessToken) { kcSet(KC_CLAUDE, parsed.claude); imported.push("Claude"); }
      if (parsed?.codex?.accessToken) { kcSet(KC_CODEX, parsed.codex); imported.push("Codex"); }
      importSettings(parsed, imported);
      importMiniMax(parsed, imported);
      importDeepSeek(parsed, imported);
      // 一次性导入：用完即删，避免之后每次运行把自刷新后的新 token 覆盖回旧的
      if (imported.length > 0) { try { fm.remove(p); } catch (e) {} }
    }
  } catch (e) {}

  // 2) 退而求其次：剪贴板（保留原逻辑）
  if (imported.length === 0) {
    let parsed = null;
    try { parsed = JSON.parse(Pasteboard.paste()); } catch (e) { parsed = null; }
    if (parsed?.claude?.accessToken) { kcSet(KC_CLAUDE, parsed.claude); imported.push("Claude"); }
    if (parsed?.codex?.accessToken) { kcSet(KC_CODEX, parsed.codex); imported.push("Codex"); }
    importSettings(parsed, imported);
    importMiniMax(parsed, imported);
    importDeepSeek(parsed, imported);
  }

  if (imported.length > 0) {
    if (!config.runsInWidget) { // 组件上下文弹窗会卡→timeout，只在 App 内提示
      let a = new Alert();
      a.title = "已更新 token";
      a.message = `已写入：${imported.join(" + ")}。回桌面长按组件刷新即可。`;
      a.addAction("好");
      await a.present();
    }
    return true;
  }
  // 没有可导入内容：只要至少配置过一个平台就放行（正常运行，不打扰）
  if (hasAnyCredential()) return true;
  if (!config.runsInWidget) {
    let a = new Alert();
    a.title = "需要先导入 token";
    a.message = "请在 Mac 把 aiquota-token.json 放进 Scriptable 的 iCloud 文件夹，再运行本脚本一次。";
    a.addAction("好");
    await a.present();
  }
  return false;
}

// ============ token 刷新 ============
async function refreshClaude(tok) {
  let req = new Request(CLAUDE_REFRESH_URL);
  req.timeoutInterval = 6; // 防止刷新请求挂起拖垮整个组件（received timeout）
  req.method = "POST";
  req.headers = { "Content-Type": "application/json" };
  req.body = JSON.stringify({
    grant_type: "refresh_token",
    refresh_token: tok.refreshToken,
    client_id: CLAUDE_CLIENT_ID,
  });
  let r = await req.loadJSON();
  if (!r || !r.access_token) throw new Error("Claude 刷新失败");
  let updated = {
    accessToken: r.access_token,
    refreshToken: r.refresh_token || tok.refreshToken,
    expiresAt: Date.now() + (r.expires_in || 3600) * 1000,
  };
  kcSet(KC_CLAUDE, updated);
  return updated;
}
async function refreshCodex(tok) {
  let req = new Request(CODEX_REFRESH_URL);
  req.timeoutInterval = 6; // 同上：刷新挂起也不拖垮组件
  req.method = "POST";
  req.headers = { "Content-Type": "application/json" };
  req.body = JSON.stringify({
    grant_type: "refresh_token",
    refresh_token: tok.refreshToken,
    client_id: CODEX_CLIENT_ID,
  });
  let r = await req.loadJSON();
  if (!r || !r.access_token) throw new Error("Codex 刷新失败");
  let updated = {
    accessToken: r.access_token,
    refreshToken: r.refresh_token || tok.refreshToken,
    accountId: tok.accountId,
  };
  kcSet(KC_CODEX, updated);
  return updated;
}
async function refreshMiniMax(tok) {
  let region = miniMaxRegion(tok);
  let req = new Request(`${region.account}/oauth2/token`);
  req.timeoutInterval = 6;
  req.method = "POST";
  req.headers = { "Content-Type": "application/x-www-form-urlencoded" };
  req.body = [
    "grant_type=refresh_token",
    "refresh_token=" + encodeURIComponent(tok.refreshToken),
    "client_id=" + encodeURIComponent(MINIMAX_CLIENT_ID),
  ].join("&");
  let r = await req.loadJSON();
  if (!r || r.status !== "success" || !r.access_token) throw new Error("MiniMax 刷新失败");
  let updated = {
    region: tok.region === "cn" ? "cn" : "global",
    planApiKey: r.access_token,
    balanceCookie: tok.balanceCookie,
    authorizationToken: r.access_token,
    refreshToken: r.refresh_token || tok.refreshToken,
    expiresAt: r.expired_in ? new Date(r.expired_in).toISOString() : tok.expiresAt,
  };
  kcSet(KC_MINIMAX, updated);
  return updated;
}

// ============ 拉取额度 ============
// 返回统一结构：{ fiveHour:{remain, resetAt}, sevenDay:{remain, resetAt} }
// remain 为剩余百分比(0-100)，resetAt 为毫秒时间戳
// 包装：未配置返回 null（不显示该列），失败返回 {error:true}（回退缓存），成功返回数据
async function getClaude() {
  if (!kcGet(KC_CLAUDE)?.accessToken) return null;
  try { return await fetchClaude(); } catch (e) { return { error: true }; }
}
async function getCodex() {
  if (!kcGet(KC_CODEX)?.accessToken) return null;
  try { return await fetchCodex(); } catch (e) { return { error: true }; }
}
async function getMiniMax() {
  let cfg = kcGet(KC_MINIMAX);
  if (!cfg?.planApiKey && !cfg?.balanceCookie && !cfg?.balanceProxyUrl) return null;
  if (cfg.balanceCookie) cfg.balanceCookie = cleanCookie(cfg.balanceCookie);
  let out = {};
  let failed = 0;
  let total = 0;
  let errors = [];
  if (cfg.planApiKey) {
    total++;
    try { out.plan = await fetchMiniMaxPlan(cfg); } catch (e) { failed++; errors.push("套餐:" + String(e.message || e)); }
  }
  if (cfg.balanceProxyUrl || cfg.balanceCookie) {
    total++;
    try { out.api = await fetchMiniMaxBalance(cfg); } catch (e) { failed++; errors.push("余额:" + String(e.message || e)); }
  }
  if (!out.plan && !out.api) {
    let message = errors.join(" / ") || "MiniMax 请求失败";
    let code = (message.match(/status_code=(\d+)/) || message.match(/HTTP\s+(\d+)/) || [])[1] || "失败";
    let hasToken = cfg.balanceCookie && cfg.balanceCookie.indexOf("_token=") >= 0;
    let hasGroup = cfg.balanceCookie && cfg.balanceCookie.indexOf("minimax_group_id_v2=") >= 0;
    let hint = `C${String(cfg.balanceCookie || "").length} T${hasToken ? "Y" : "N"} G${hasGroup ? "Y" : "N"}`;
    return { error: true, code, message: hint + " " + message };
  }
  if (failed > 0 && failed < total) out.partialError = true;
  if (errors.length > 0) out.message = errors.join(" / ");
  return out;
}
async function getDeepSeek() {
  if (!kcGet(KC_DEEPSEEK)?.apiKey) return null;
  try { return await fetchDeepSeek(); } catch (e) { return { error: true }; }
}

async function fetchClaude() {
  let tok = kcGet(KC_CLAUDE);
  if (!tok) throw new Error("无 Claude token");
  // 过期则先刷新
  if (tok.expiresAt && Date.now() > tok.expiresAt - 60000) {
    try { tok = await refreshClaude(tok); } catch (e) { /* 用旧 token 试一次 */ }
  }
  const call = async (t) => {
    let req = new Request("https://api.anthropic.com/api/oauth/usage");
    req.timeoutInterval = 6; // 慢/挂起请求快速失败→回退缓存，而非拖垮组件
    req.headers = {
      "Authorization": "Bearer " + t.accessToken,
      "anthropic-beta": "oauth-2025-04-20",
      "User-Agent": "claude-cli",
    };
    let resp = await req.loadJSON();
    let status = req.response ? req.response.statusCode : 200;
    return { resp, status };
  };
  let { resp, status } = await call(tok);
  if (status === 401) { tok = await refreshClaude(tok); ({ resp, status } = await call(tok)); }
  // 没有 five_hour 字段说明拿到的是错误体(401/限流等),抛错让上层回退缓存并标「离线」,
  // 不能 100-undefined 伪装成「还剩100%」
  if (!resp || !resp.five_hour) throw new Error("Claude 响应异常 status=" + status);
  return {
    fiveHour: { remain: 100 - (resp.five_hour?.utilization ?? 0), resetAt: Date.parse(resp.five_hour?.resets_at) },
    sevenDay: { remain: 100 - (resp.seven_day?.utilization ?? 0), resetAt: Date.parse(resp.seven_day?.resets_at) },
  };
}
async function fetchCodex() {
  let tok = kcGet(KC_CODEX);
  if (!tok) throw new Error("无 Codex token");
  const call = async (t) => {
    let req = new Request("https://chatgpt.com/backend-api/wham/usage");
    req.timeoutInterval = 6; // 同上
    req.headers = {
      "Authorization": "Bearer " + t.accessToken,
      "chatgpt-account-id": t.accountId,
      "User-Agent": "codex-cli",
    };
    let resp = await req.loadJSON();
    let status = req.response ? req.response.statusCode : 200;
    return { resp, status };
  };
  let { resp, status } = await call(tok);
  if (status === 401) { tok = await refreshCodex(tok); ({ resp, status } = await call(tok)); }
  // 同 Claude:没有 rate_limit 字段说明是错误体,抛错回退缓存,不伪装成满额
  if (!resp || !resp.rate_limit) throw new Error("Codex 响应异常 status=" + status);
  let rl = resp.rate_limit || {};
  return {
    fiveHour: { remain: 100 - (rl.primary_window?.used_percent ?? 0), resetAt: (rl.primary_window?.reset_at ?? 0) * 1000 },
    sevenDay: { remain: 100 - (rl.secondary_window?.used_percent ?? 0), resetAt: (rl.secondary_window?.reset_at ?? 0) * 1000 },
  };
}

function miniMaxRegion(cfg) {
  return cfg?.region === "cn"
    ? { api: "https://api.minimaxi.com", platform: "https://platform.minimaxi.com", account: "https://account.minimaxi.com", accountApi: "https://www.minimaxi.com" }
    : { api: "https://api.minimax.io", platform: "https://platform.minimax.io", account: "https://account.minimax.io", accountApi: "https://www.minimax.io" };
}
function epochMs(v) {
  if (!v) return null;
  return v < 10000000000 ? v * 1000 : v;
}
function numberValue(v) {
  if (typeof v === "number") return v;
  if (typeof v === "string") {
    let n = Number(v.replace(/,/g, ""));
    return isNaN(n) ? null : n;
  }
  return null;
}
function clampRemain(v) {
  return Math.max(0, Math.min(100, v));
}
function remainFromUsage(total, used, percent) {
  let pct = numberValue(percent);
  if (pct !== null) return clampRemain(pct);
  let t = numberValue(total);
  let u = numberValue(used);
  if (!t || u === null) return null;
  return clampRemain((Math.max(0, t - u) / t) * 100);
}
function earliestReset(items, key) {
  let times = items.map((x) => epochMs(x[key])).filter(Boolean);
  if (times.length === 0) return null;
  return Math.min(...times);
}
function formatCount(n) {
  if (n === null || n === undefined || isNaN(n)) return "";
  if (n >= 10000) return `${Math.round(n / 1000) / 10}万`;
  return String(Math.round(n));
}
function currencySymbol(currency) {
  if (currency === "CNY" || currency === "RMB") return "¥";
  if (currency === "USD") return "$";
  return currency ? currency + " " : "";
}
function formatMoney(amount, currency) {
  let symbol = currencySymbol(currency);
  return `${symbol}${Number(amount || 0).toFixed(2)}`;
}
function cookieValue(cookie, name) {
  let parts = String(cookie || "").split(";");
  for (let p of parts) {
    let i = p.indexOf("=");
    if (i <= 0) continue;
    if (p.slice(0, i).trim() === name) return p.slice(i + 1).trim();
  }
  return "";
}
function jwtPayload(token) {
  try {
    let part = String(token || "").split(".")[1];
    if (!part) return {};
    let b64 = part.replace(/-/g, "+").replace(/_/g, "/");
    while (b64.length % 4) b64 += "=";
    return JSON.parse(Data.fromBase64String(b64).toRawString());
  } catch (e) {
    return {};
  }
}

async function fetchMiniMaxPlan(cfg) {
  if (cfg.refreshToken && cfg.expiresAt && Date.now() > Date.parse(cfg.expiresAt) - 5 * 60 * 1000) {
    try { cfg = await refreshMiniMax(cfg); } catch (e) { /* 用旧 token 试一次 */ }
  }
  const call = async (c) => {
    let r = miniMaxRegion(c);
    let req = new Request(`${r.api}/v1/token_plan/remains`);
    req.timeoutInterval = 6;
    req.headers = {
      "Authorization": "Bearer " + c.planApiKey,
      "Accept": "application/json",
      "Content-Type": "application/json",
      "MM-API-Source": "ai-quota-widget",
    };
    let resp = await req.loadJSON();
    let status = req.response ? req.response.statusCode : 200;
    return { resp, status };
  };
  let { resp, status } = await call(cfg);
  if ((status === 401 || status === 403) && cfg.refreshToken) {
    cfg = await refreshMiniMax(cfg);
    ({ resp, status } = await call(cfg));
  }
  if (status !== 200 || resp?.base_resp?.status_code) throw new Error("MiniMax 套餐响应异常 status=" + status);
  let models = Array.isArray(resp?.model_remains) ? resp.model_remains : [];
  let rows = models.map((m) => ({
    remain: remainFromUsage(m.current_interval_total_count, m.current_interval_usage_count, m.current_interval_remaining_percent),
    resetAt: epochMs(m.end_time),
  })).filter((x) => x.remain !== null);
  if (rows.length === 0) throw new Error("MiniMax 套餐数据为空");
  let remain = Math.min(...rows.map((x) => x.remain));
  return {
    remain,
    resetAt: earliestReset(models, "end_time"),
    detail: `${models.length}项额度`,
  };
}

async function fetchMiniMaxBalance(cfg) {
  if (cfg.balanceProxyUrl) return await fetchMiniMaxBalanceProxy(cfg);

  const call = async (region) => {
    let accountHost = region.accountApi || region.platform;
    let req = new Request(`${accountHost}/account/query_balance`);
    req.timeoutInterval = 6;
    req.headers = {
      "Cookie": cfg.balanceCookie,
      "Accept": "application/json, text/plain, */*",
      "Accept-Language": "en-US,en;q=0.9",
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
      "Origin": region.platform,
      "Referer": `${region.platform}/`,
      "Sec-Fetch-Dest": "empty",
      "Sec-Fetch-Mode": "cors",
      "Sec-Fetch-Site": "same-site",
      "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15",
    };
    let groupId = cookieValue(cfg.balanceCookie, "minimax_group_id_v2");
    if (groupId) req.headers["X-Group-Id"] = groupId;
    let webToken = cookieValue(cfg.balanceCookie, "_token");
    if (webToken) {
      req.headers["Token"] = webToken;
      req.headers["Authorization"] = "Bearer " + webToken;
      let userId = jwtPayload(webToken)?.user?.id;
      if (userId) req.headers["Userid"] = String(userId);
    }
    if (cfg.authorizationToken) req.headers["Authorization"] = "Bearer " + cfg.authorizationToken;
    let resp = await req.loadJSON();
    let status = req.response ? req.response.statusCode : 200;
    return { resp, status };
  };
  let regions = cfg.region === "cn"
    ? [miniMaxRegion({ region: "cn" }), miniMaxRegion({ region: "global" })]
    : [miniMaxRegion({ region: "global" }), miniMaxRegion({ region: "cn" })];
  let resp = null;
  let status = 0;
  let lastError = "MiniMax API 余额请求失败";
  for (let r of regions) {
    try {
      ({ resp, status } = await call(r));
      let body = resp?.data || resp || {};
      let base = body.base_resp || resp?.base_resp;
      if (status === 200 && !base?.status_code) break;
      lastError = `${r.accountApi || r.platform} HTTP ${status} status_code=${base?.status_code || "?"} ${base?.status_msg || ""}`;
    } catch (e) {
      lastError = `${r.accountApi || r.platform} ${String(e.message || e)}`;
    }
  }
  let data = resp?.data || resp || {};
  let base = data.base_resp || resp?.base_resp;
  if (status !== 200 || base?.status_code) throw new Error(lastError);
  let balance = null;
  for (let k of ["available_amount", "cash_balance", "points_balance", "point_balance", "credits_balance", "credit_balance", "balance"]) {
    balance = numberValue(data[k]);
    if (balance !== null) break;
  }
  if (balance === null) throw new Error("MiniMax API 余额字段缺失");
  let detail = "账户钱包";
  let cash = numberValue(data.cash_balance);
  let voucher = numberValue(data.voucher_balance);
  if (cash !== null || voucher !== null) {
    detail = `现金 ¥${Number(cash || 0).toFixed(2)} / 代金券 ¥${Number(voucher || 0).toFixed(2)}`;
  }
  return { balance, text: `¥${Number(balance).toFixed(2)}`, detail };
}

function parseMiniMaxBalance(data) {
  let src = data?.minimaxWallet || data?.data || data || {};
  let balance = null;
  for (let k of ["amount", "available_amount", "cash_balance", "points_balance", "point_balance", "credits_balance", "credit_balance", "balance"]) {
    balance = numberValue(src[k]);
    if (balance !== null) break;
  }
  if (balance === null) throw new Error("MiniMax API 余额字段缺失");
  let cash = numberValue(src.cash_balance);
  let voucher = numberValue(src.voucher_balance);
  let detail = src.updated_at ? "Worker " + agoText(Date.parse(src.updated_at)) : "账户钱包";
  if (cash !== null || voucher !== null) {
    detail = `现金 ¥${Number(cash || 0).toFixed(2)} / 代金券 ¥${Number(voucher || 0).toFixed(2)}`;
  }
  return { balance, text: `¥${Number(balance).toFixed(2)}`, detail };
}

async function fetchMiniMaxBalanceProxy(cfg) {
  let req = new Request(cfg.balanceProxyUrl);
  req.timeoutInterval = 6;
  req.headers = { "Accept": "application/json" };
  if (cfg.balanceProxyToken) {
    req.headers["Authorization"] = "Bearer " + cfg.balanceProxyToken;
    req.headers["X-Widget-Token"] = cfg.balanceProxyToken;
  }
  let resp = await req.loadJSON();
  let status = req.response ? req.response.statusCode : 200;
  let base = resp?.base_resp || {};
  if (status !== 200 || base.status_code) {
    throw new Error(`Worker HTTP ${status} status_code=${base.status_code || "?"} ${base.status_msg || ""}`);
  }
  return parseMiniMaxBalance(resp);
}

async function fetchDeepSeek() {
  let tok = kcGet(KC_DEEPSEEK);
  let req = new Request("https://api.deepseek.com/user/balance");
  req.timeoutInterval = 6;
  req.headers = {
    "Authorization": "Bearer " + tok.apiKey,
    "Accept": "application/json",
  };
  let resp = await req.loadJSON();
  let status = req.response ? req.response.statusCode : 200;
  if (status !== 200 || !Array.isArray(resp?.balance_infos)) throw new Error("DeepSeek 响应异常 status=" + status);
  let balances = resp.balance_infos.map((x) => ({
    currency: x.currency,
    total: Number(x.total_balance || 0),
    granted: Number(x.granted_balance || 0),
    toppedUp: Number(x.topped_up_balance || 0),
  })).filter((x) => !isNaN(x.total));
  if (balances.length === 0) throw new Error("DeepSeek 余额为空");
  let selected = balances.find((x) => x.currency === "USD" && x.total > 0)
    || balances.find((x) => x.total > 0)
    || balances.find((x) => x.currency === "USD")
    || balances[0];
  return {
    available: resp.is_available !== false,
    currency: selected.currency,
    total: selected.total,
    granted: selected.granted,
    toppedUp: selected.toppedUp,
    text: formatMoney(selected.total, selected.currency),
    detail: `充值 ${formatMoney(selected.toppedUp, selected.currency)} / 赠送 ${formatMoney(selected.granted, selected.currency)}`,
  };
}

// ============ 本地缓存（拉取失败时回退） ============
function cachePath() {
  let fm = FileManager.local();
  return fm.joinPath(fm.documentsDirectory(), CACHE_FILE);
}
function saveCache(data) {
  try { FileManager.local().writeString(cachePath(), JSON.stringify(data)); } catch (e) {}
}
function loadCache() {
  try {
    let fm = FileManager.local();
    if (fm.fileExists(cachePath())) return JSON.parse(fm.readString(cachePath()));
  } catch (e) {}
  return null;
}

// ============ 工具：颜色 / 倒计时文案 ============
function colorFor(remain) {
  if (remain > 50) return new Color("#34c759"); // 绿
  if (remain > 20) return new Color("#ff9f0a"); // 橙
  return new Color("#ff3b30");                  // 红
}
function balanceColor(amount, fallback) {
  let n = numberValue(amount);
  if (n !== null && n < 5) return new Color("#ff453a");  // 低于 5 元醒目提示
  if (n !== null && n < 20) return new Color("#ffb340");
  return fallback || new Color("#7cddc3");
}
function tintedBg(color, alpha) {
  // Scriptable 的 Color 不能直接读回 hex，卡片背景由调用方传入固定色值。
  return color || new Color("#ffffff", alpha || 0.07);
}
function resetText(resetAt, isWeekly) {
  // 5小时显示「几点恢复」，周显示「哪天恢复」，最直接
  if (!resetAt || isNaN(resetAt)) return "";
  if (resetAt - Date.now() <= 0) return "即将恢复";
  let d = new Date(resetAt);
  if (isWeekly) {
    return `${d.getMonth() + 1}月${d.getDate()}日 恢复`;
  }
  let hh = String(d.getHours()).padStart(2, "0");
  let mm = String(d.getMinutes()).padStart(2, "0");
  return `${hh}:${mm} 恢复`;
}
function agoText(ts) {
  let m = Math.floor((Date.now() - ts) / 60000);
  if (m <= 0) return "刚刚";
  if (m < 60) return `${m}分钟前`;
  return `${Math.floor(m / 60)}小时前`;
}

// ============ 渲染：进度条图片 ============
function barImage(remain, w, h) {
  let ctx = new DrawContext();
  ctx.size = new Size(w, h);
  ctx.opaque = false;
  ctx.respectScreenScale = true;
  // 底槽
  let track = new Path();
  track.addRoundedRect(new Rect(0, 0, w, h), h / 2, h / 2);
  ctx.addPath(track);
  ctx.setFillColor(new Color("#ffffff", 0.18));
  ctx.fillPath();
  // 填充（剩余比例）
  let fw = Math.max(h, w * Math.max(0, Math.min(100, remain)) / 100);
  let fill = new Path();
  fill.addRoundedRect(new Rect(0, 0, fw, h), h / 2, h / 2);
  ctx.addPath(fill);
  ctx.setFillColor(colorFor(remain));
  ctx.fillPath();
  return ctx.getImage();
}

// ============ 渲染：单个 Agent 卡片 ============
function renderMetricLine(col, label, remain, detail, barW, showDetail, narrow) {
  let line = col.addStack();
  line.layoutHorizontally();
  line.centerAlignContent();
  line.spacing = narrow ? 3 : 4;
  let lb = line.addText(label);
  lb.font = Font.systemFont(narrow ? 9 : 11);
  lb.textColor = new Color("#ffffff", 0.72);
  lb.size = new Size(narrow ? 18 : 24, 0);
  let img = line.addImage(barImage(remain, barW, 6));
  img.imageSize = new Size(barW, 6);
  let pct = line.addText(`${Math.round(remain)}%`);
  pct.font = Font.semiboldSystemFont(narrow ? 10 : 12);
  pct.textColor = colorFor(remain);
  if (showDetail && detail) {
    let rt = col.addText("   " + detail);
    rt.font = Font.systemFont(8);
    rt.textColor = new Color("#ffffff", 0.46);
    rt.lineLimit = 1;
  }
}
function renderValueLine(col, label, value, detail, showDetail, narrow, valueColor) {
  let line = col.addStack();
  line.layoutHorizontally();
  line.centerAlignContent();
  line.spacing = narrow ? 3 : 4;
  let lb = line.addText(label);
  lb.font = Font.systemFont(narrow ? 9 : 11);
  lb.textColor = new Color("#ffffff", 0.72);
  lb.size = new Size(narrow ? 24 : 30, 0);
  let val = line.addText(value);
  val.font = Font.semiboldSystemFont(narrow ? 13 : 15);
  val.textColor = valueColor || Color.white();
  val.lineLimit = 1;
  if (showDetail && detail) {
    let dt = col.addText("   " + detail);
    dt.font = Font.systemFont(8);
    dt.textColor = new Color("#ffffff", 0.46);
    dt.lineLimit = 1;
  }
}
function renderCard(stack, platform, barW, cardW, cardH, showDetail) {
  let narrow = cardW < 120;
  let col = stack.addStack();
  col.layoutVertically();
  col.spacing = 0;
  col.setPadding(10, narrow ? 6 : 10, 8, narrow ? 6 : 10);
  col.size = new Size(cardW, cardH);
  col.backgroundColor = tintedBg(platform.bg, 0.07);
  col.cornerRadius = 8;

  let t = col.addText(platform.title);
  t.font = Font.semiboldSystemFont(narrow ? 13 : 16);
  t.textColor = platform.accent;
  t.lineLimit = 1;
  col.addSpacer();

  if (platform.kind === "windows") {
    renderMetricLine(col, "5h", platform.data.fiveHour.remain, resetText(platform.data.fiveHour.resetAt, false), barW, showDetail, narrow);
    col.addSpacer(narrow ? 4 : 6);
    renderMetricLine(col, "周", platform.data.sevenDay.remain, resetText(platform.data.sevenDay.resetAt, true), barW, showDetail, narrow);
  } else if (platform.kind === "minimax") {
    if (platform.data.plan) {
      let detail = [platform.data.plan.detail, resetText(platform.data.plan.resetAt, false)].filter(Boolean).join(" · ");
      renderMetricLine(col, "套餐", platform.data.plan.remain, detail, barW, showDetail, narrow);
      if (platform.data.api) col.addSpacer(narrow ? 4 : 6);
    }
    if (platform.data.api) renderValueLine(col, "余额", platform.data.api.text, platform.data.api.detail, showDetail, narrow, balanceColor(platform.data.api.balance, platform.accent));
  } else if (platform.kind === "balance") {
    let valueColor = platform.data.available ? balanceColor(platform.data.total, platform.accent) : new Color("#ff453a");
    renderValueLine(col, "余额", platform.data.text, platform.data.available ? platform.data.detail : "余额不可用", showDetail, narrow, valueColor);
  } else if (platform.kind === "error") {
    renderValueLine(col, "状态", platform.data.text, platform.data.detail, true, narrow, new Color("#ff453a"));
  }
  col.addSpacer();
}

// ============ 渲染：组件 ============
// platforms: [{title, accent, kind, data}]，按数量自适应 1-4 个平台
function buildWidget(platforms, updatedAt, offline) {
  let w = new ListWidget();
  w.backgroundColor = new Color("#1c1c1e");
  w.setPadding(6, 6, 6, 6);

  let body = w.addStack();
  body.layoutVertically();
  body.spacing = 5;

  let rows = platforms.length <= 3
    ? [platforms]
    : [platforms.slice(0, 2), platforms.slice(2, 4)];
  let widgetW = 329;
  let widgetH = 155;
  let outerPad = 12;
  let gap = 5;
  let rowH = Math.floor((widgetH - outerPad - gap * (rows.length - 1)) / rows.length);
  let showDetail = platforms.length <= 2;
  for (let r = 0; r < rows.length; r++) {
    let rowItems = rows[r];
    let cardW = Math.floor((widgetW - outerPad - gap * (rowItems.length - 1)) / rowItems.length);
    let barW = Math.max(24, Math.min(66, cardW - (cardW < 120 ? 72 : 94)));
    let row = body.addStack();
    row.layoutHorizontally();
    row.spacing = gap;
    for (let i = 0; i < rowItems.length; i++) {
      renderCard(row, rowItems[i], barW, cardW, rowH, showDetail);
    }
  }

  return w;
}

// ============ 主流程 ============
async function main() {
  let ok = await bootstrapIfNeeded();
  if (!ok) { Script.complete(); return; }

  let settings = getSettings();
  // 各拉各的：null=未配置(不显示)，{error}=失败(回退缓存)，否则=成功
  let [claudeRes, codexRes, minimaxRes, deepseekRes] = await Promise.all([
    providerEnabled(settings, "claude") ? getClaude() : null,
    providerEnabled(settings, "codex") ? getCodex() : null,
    providerEnabled(settings, "minimax") ? getMiniMax() : null,
    providerEnabled(settings, "deepseek") ? getDeepSeek() : null,
  ]);
  let cache = loadCache() || {};
  let offline = false;

  // 解析：成功用新数据，失败回退该平台缓存并标记离线
  const resolve = (res, cached) => {
    if (res === null) return null;
    if (res.error) { offline = true; return cached || null; }
    if (res.partialError) offline = true;
    return res;
  };
  let claude = resolve(claudeRes, cache.claude);
  let codex = resolve(codexRes, cache.codex);
  let minimax = resolve(minimaxRes, cache.minimax);
  let deepseek = resolve(deepseekRes, cache.deepseek);
  let minimaxCfg = kcGet(KC_MINIMAX);
  if (!minimax && providerEnabled(settings, "minimax") && (minimaxCfg?.planApiKey || minimaxCfg?.balanceCookie || minimaxCfg?.balanceProxyUrl) && minimaxRes?.error) {
    minimax = { errorCard: true, text: String(minimaxRes.code || "失败"), detail: minimaxRes.message || "运行脚本重试" };
  }

  // 写缓存：成功的平台更新，失败的保留旧值
  let claudeOk = claudeRes && !claudeRes.error;
  let codexOk = codexRes && !codexRes.error;
  let minimaxOk = minimaxRes && !minimaxRes.error;
  let deepseekOk = deepseekRes && !deepseekRes.error;
  let newCache = {
    claude: claudeOk ? claudeRes : cache.claude,
    codex: codexOk ? codexRes : cache.codex,
    minimax: minimaxOk ? minimaxRes : cache.minimax,
    deepseek: deepseekOk ? deepseekRes : cache.deepseek,
    updatedAt: (claudeOk || codexOk || minimaxOk || deepseekOk) ? Date.now() : (cache.updatedAt || Date.now()),
  };
  saveCache(newCache);

  // 收集可显示的平台
  let platforms = [];
  if (providerEnabled(settings, "claude") && claude) {
    platforms.push({ title: "Claude", accent: new Color("#ff8a65"), bg: new Color("#2d2522"), kind: "windows", data: claude });
  }
  if (providerEnabled(settings, "codex") && codex) {
    platforms.push({ title: "Codex", accent: new Color("#30d158"), bg: new Color("#1f2b24"), kind: "windows", data: codex });
  }
  if (providerEnabled(settings, "minimax") && minimax) {
    platforms.push({ title: "MiniMax", accent: new Color("#64a8ff"), bg: new Color("#202736"), kind: minimax.errorCard ? "error" : "minimax", data: minimax });
  }
  if (providerEnabled(settings, "deepseek") && deepseek) {
    platforms.push({ title: "DeepSeek", accent: new Color("#7cddc3"), bg: new Color("#1f2b2b"), kind: "balance", data: deepseek });
  }

  if (platforms.length === 0) {
    let w = new ListWidget();
    w.backgroundColor = new Color("#1c1c1e");
    let t = w.addText("未配置 token/API Key 或暂无数据\n运行脚本导入配置");
    t.font = Font.systemFont(11); t.textColor = Color.white();
    Script.setWidget(w); Script.complete(); return;
  }

  let updatedAt = newCache.updatedAt;
  let widget = buildWidget(platforms, updatedAt, offline);
  widget.refreshAfterDate = new Date(Date.now() + 12 * 60 * 1000); // 12 分钟后刷新

  if (config.runsInWidget) {
    Script.setWidget(widget);
  } else {
    await widget.presentMedium(); // 在 App 内运行时预览
  }
  Script.complete();
}

await main();
