function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, OPTIONS",
      "access-control-allow-headers": "authorization, x-widget-token",
    },
  });
}

function cookieValue(cookie, name) {
  for (const part of String(cookie || "").split(";")) {
    const i = part.indexOf("=");
    if (i <= 0) continue;
    if (part.slice(0, i).trim() === name) return part.slice(i + 1).trim();
  }
  return "";
}

function jwtPayload(token) {
  try {
    const part = String(token || "").split(".")[1];
    if (!part) return {};
    let b64 = part.replace(/-/g, "+").replace(/_/g, "/");
    while (b64.length % 4) b64 += "=";
    return JSON.parse(atob(b64));
  } catch {
    return {};
  }
}

async function fetchWallet(env) {
  const cookie = String(env.MINIMAX_COOKIE || "")
    .replace(/^cookie:\s*/i, "")
    .replaceAll("&amp;", "&")
    .trim();
  if (!cookie) {
    return json({ base_resp: { status_code: 400, status_msg: "MINIMAX_COOKIE secret missing" } }, 500);
  }

  const region = String(env.MINIMAX_REGION || "cn").toLowerCase() === "global" ? "global" : "cn";
  const platform = region === "cn" ? "https://platform.minimaxi.com" : "https://platform.minimax.io";
  const account = region === "cn" ? "https://www.minimaxi.com" : "https://www.minimax.io";
  const groupId = env.MINIMAX_GROUP_ID || cookieValue(cookie, "minimax_group_id_v2");

  const baseHeaders = {
    "Cookie": cookie,
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "en-US,en;q=0.9",
    "Content-Type": "application/json",
    "Origin": platform,
    "Referer": `${platform}/`,
    "Sec-Fetch-Dest": "empty",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Site": "same-site",
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15",
    "X-Requested-With": "XMLHttpRequest",
  };
  if (groupId) baseHeaders["X-Group-Id"] = groupId;
  const webToken = cookieValue(cookie, "_token");
  const tokenHeaders = { ...baseHeaders };
  if (webToken) {
    tokenHeaders["Token"] = webToken;
    const userId = jwtPayload(webToken)?.user?.id;
    if (userId) tokenHeaders["Userid"] = String(userId);
  }

  async function call(headers) {
    const resp = await fetch(`${account}/account/query_balance`, { headers });
    let data;
    try {
      data = await resp.json();
    } catch {
      return { resp, data: { base_resp: { status_code: resp.status, status_msg: "MiniMax returned non-JSON" } } };
    }
    return { resp, data };
  }

  let { resp, data } = await call(tokenHeaders);
  let base = data.base_resp || {};
  if (base.status_code === 1000) {
    ({ resp, data } = await call(baseHeaders));
    base = data.base_resp || {};
  }

  if (!resp.ok || base.status_code) {
    return json({
      base_resp: {
        status_code: base.status_code || resp.status,
        status_msg: base.status_msg || `HTTP ${resp.status}`,
      },
    }, resp.ok ? 502 : resp.status);
  }

  return json({
    amount: data.available_amount,
    available_amount: data.available_amount,
    cash_balance: data.cash_balance,
    voucher_balance: data.voucher_balance,
    credit_balance: data.credit_balance,
    owed_amount: data.owed_amount,
    updated_at: new Date().toISOString(),
    base_resp: { status_code: 0, status_msg: "success" },
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return json({});
    if (request.method !== "GET") {
      return json({ base_resp: { status_code: 405, status_msg: "method not allowed" } }, 405);
    }

    const expected = String(env.WALLET_PROXY_TOKEN || "").trim();
    if (expected) {
      const url = new URL(request.url);
      const auth = request.headers.get("authorization") || "";
      const token = request.headers.get("x-widget-token") || auth.replace(/^Bearer\s+/i, "") || url.searchParams.get("token") || "";
      if (token !== expected) {
        return json({ base_resp: { status_code: 401, status_msg: "unauthorized" } }, 401);
      }
    }

    return fetchWallet(env);
  },
};
