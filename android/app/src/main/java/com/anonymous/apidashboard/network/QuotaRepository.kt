package com.anonymous.apidashboard.network

import android.content.Context
import android.util.Base64
import com.anonymous.apidashboard.data.CacheStore
import com.anonymous.apidashboard.data.CardSnapshot
import com.anonymous.apidashboard.data.MiniMaxBalance
import com.anonymous.apidashboard.data.MiniMaxPlan
import com.anonymous.apidashboard.data.ProviderId
import com.anonymous.apidashboard.data.SecureStore
import com.anonymous.apidashboard.data.UsageWindow
import com.anonymous.apidashboard.data.agoText
import com.anonymous.apidashboard.data.balanceColor
import com.anonymous.apidashboard.data.clampPercent
import com.anonymous.apidashboard.data.colorForRemain
import com.anonymous.apidashboard.data.epochMs
import com.anonymous.apidashboard.data.formatMoney
import com.anonymous.apidashboard.data.normalizeEnabled
import com.anonymous.apidashboard.data.numberValue
import com.anonymous.apidashboard.data.optCleanString
import com.anonymous.apidashboard.data.optObject
import com.anonymous.apidashboard.data.parseDateMs
import com.anonymous.apidashboard.data.percentText
import com.anonymous.apidashboard.data.remainFromUsage
import com.anonymous.apidashboard.data.resetText
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

class QuotaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureStore(appContext)
    private val cache = CacheStore(appContext)
    private val api = ApiClient()

    suspend fun refresh(): List<CardSnapshot> {
        val cached = cache.load().associateBy { it.provider }
        val cards = mutableListOf<CardSnapshot>()
        enabledProviders().forEach { provider ->
            val card = runCatching { fetchProvider(provider) }.getOrElse { error ->
                cached[provider]?.copy(isStale = true, footer = "离线 " + cachedFooter(error))
                    ?: errorCard(provider, error.message ?: "请求失败")
            }
            if (card != null) cards += card
        }
        if (cards.any { !it.isStale && it.error == null }) {
            cache.save(cards)
        }
        return cards
    }

    fun cachedCards(): List<CardSnapshot> = cache.load()

    fun hasAnyCredential(): Boolean = store.hasAnyCredential()

    private fun enabledProviders(): List<ProviderId> {
        val settings = store.getJson(SecureStore.KEY_SETTINGS)
        val enabled = normalizeEnabled(settings?.opt("enabled"))
        val requested = if (enabled.isNullOrEmpty()) ProviderId.entries.map { it.key } else enabled
        return requested.mapNotNull { ProviderId.fromKey(it) }.filter { hasCredential(it) }
    }

    private fun hasCredential(provider: ProviderId): Boolean {
        return when (provider) {
            ProviderId.Claude -> store.getJson(SecureStore.KEY_CLAUDE)?.optCleanString("accessToken")?.isNotEmpty() == true
            ProviderId.Codex -> store.getJson(SecureStore.KEY_CODEX)?.optCleanString("accessToken")?.isNotEmpty() == true
            ProviderId.MiniMax -> store.getJson(SecureStore.KEY_MINIMAX)?.let {
                it.optCleanString("planApiKey").isNotEmpty() ||
                    it.optCleanString("balanceCookie").isNotEmpty() ||
                    it.optCleanString("balanceProxyUrl").isNotEmpty()
            } == true
            ProviderId.DeepSeek -> store.getJson(SecureStore.KEY_DEEPSEEK)?.optCleanString("apiKey")?.isNotEmpty() == true
        }
    }

    private suspend fun fetchProvider(provider: ProviderId): CardSnapshot? {
        return when (provider) {
            ProviderId.Claude -> fetchClaudeCard()
            ProviderId.Codex -> fetchCodexCard()
            ProviderId.MiniMax -> fetchMiniMaxCard()
            ProviderId.DeepSeek -> fetchDeepSeekCard()
        }
    }

    private suspend fun fetchClaudeCard(): CardSnapshot {
        val usage = fetchClaude()
        val accent = colorForRemain(min(usage.first.remain, usage.second.remain))
        return CardSnapshot(
            provider = ProviderId.Claude,
            title = "Claude",
            primaryLabel = "5小时",
            primaryValue = percentText(usage.first.remain),
            secondaryLabel = "本周",
            secondaryValue = percentText(usage.second.remain),
            footer = resetText(usage.first.resetAt, false).ifEmpty { resetText(usage.second.resetAt, true) },
            accent = accent,
        )
    }

    private suspend fun fetchCodexCard(): CardSnapshot {
        val usage = fetchCodex()
        val accent = colorForRemain(min(usage.first.remain, usage.second.remain))
        return CardSnapshot(
            provider = ProviderId.Codex,
            title = "Codex",
            primaryLabel = "5小时",
            primaryValue = percentText(usage.first.remain),
            secondaryLabel = "本周",
            secondaryValue = percentText(usage.second.remain),
            footer = resetText(usage.first.resetAt, false).ifEmpty { resetText(usage.second.resetAt, true) },
            accent = accent,
        )
    }

    private suspend fun fetchMiniMaxCard(): CardSnapshot {
        val cfg = store.getJson(SecureStore.KEY_MINIMAX) ?: error("无 MiniMax 配置")
        val plan = if (cfg.optCleanString("planApiKey").isNotEmpty()) {
            runCatching { fetchMiniMaxPlan(cfg) }.getOrNull()
        } else {
            null
        }
        val balance = if (cfg.optCleanString("balanceProxyUrl").isNotEmpty() || cfg.optCleanString("balanceCookie").isNotEmpty()) {
            runCatching { fetchMiniMaxBalance(cfg) }.getOrNull()
        } else {
            null
        }
        if (plan == null && balance == null) error("MiniMax 请求失败")
        val accent = balanceColor(balance?.balance, colorForRemain(plan?.remain ?: 100.0))
        return CardSnapshot(
            provider = ProviderId.MiniMax,
            title = "MiniMax",
            primaryLabel = if (plan != null) "套餐" else "余额",
            primaryValue = if (plan != null) percentText(plan.remain) else balance?.text.orEmpty(),
            secondaryLabel = if (plan != null && balance != null) "余额" else "",
            secondaryValue = if (plan != null && balance != null) balance.text else "",
            footer = balance?.detail ?: plan?.detail.orEmpty(),
            accent = accent,
        )
    }

    private suspend fun fetchDeepSeekCard(): CardSnapshot {
        val cfg = store.getJson(SecureStore.KEY_DEEPSEEK) ?: error("无 DeepSeek 配置")
        val resp = api.getJson(
            "https://api.deepseek.com/user/balance",
            mapOf(
                "Authorization" to "Bearer ${cfg.optCleanString("apiKey")}",
                "Accept" to "application/json",
            ),
        )
        val balances = resp.json.optJSONArray("balance_infos")
        if (resp.status != 200 || balances == null) error("DeepSeek 响应异常 status=${resp.status}")
        val rows = buildList {
            for (i in 0 until balances.length()) {
                val row = balances.optJSONObject(i) ?: continue
                val total = row.optCleanString("total_balance").toDoubleOrNull() ?: row.optDouble("total_balance", Double.NaN)
                if (!total.isNaN()) add(row)
            }
        }
        if (rows.isEmpty()) error("DeepSeek 余额为空")
        val selected = rows.firstOrNull { it.optString("currency") == "USD" && numberValue(it.opt("total_balance"))?.let { n -> n > 0 } == true }
            ?: rows.firstOrNull { numberValue(it.opt("total_balance"))?.let { n -> n > 0 } == true }
            ?: rows.firstOrNull { it.optString("currency") == "USD" }
            ?: rows.first()
        val currency = selected.optString("currency")
        val total = numberValue(selected.opt("total_balance")) ?: 0.0
        val granted = numberValue(selected.opt("granted_balance")) ?: 0.0
        val toppedUp = numberValue(selected.opt("topped_up_balance")) ?: 0.0
        return CardSnapshot(
            provider = ProviderId.DeepSeek,
            title = "DeepSeek",
            primaryLabel = "余额",
            primaryValue = formatMoney(total, currency),
            secondaryLabel = "充值",
            secondaryValue = formatMoney(toppedUp, currency),
            footer = "赠送 ${formatMoney(granted, currency)}",
            accent = balanceColor(total, "#7cddc3"),
        )
    }

    private suspend fun fetchClaude(): Pair<UsageWindow, UsageWindow> {
        var tok = store.getJson(SecureStore.KEY_CLAUDE) ?: error("无 Claude token")
        val expiresAt = tok.optLong("expiresAt", 0L)
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt - 60_000) {
            tok = runCatching { refreshClaude(tok) }.getOrDefault(tok)
        }
        var resp = callClaude(tok)
        if (resp.status == 401) {
            tok = refreshClaude(tok)
            resp = callClaude(tok)
        }
        val five = resp.json.optObject("five_hour") ?: error("Claude 响应异常 status=${resp.status}")
        val seven = resp.json.optObject("seven_day") ?: JSONObject()
        return UsageWindow(100.0 - five.optDouble("utilization", 0.0), parseDateMs(five.optString("resets_at"))) to
            UsageWindow(100.0 - seven.optDouble("utilization", 0.0), parseDateMs(seven.optString("resets_at")))
    }

    private suspend fun fetchCodex(): Pair<UsageWindow, UsageWindow> {
        var tok = store.getJson(SecureStore.KEY_CODEX) ?: error("无 Codex token")
        var resp = callCodex(tok)
        if (resp.status == 401) {
            tok = refreshCodex(tok)
            resp = callCodex(tok)
        }
        val rateLimit = resp.json.optObject("rate_limit") ?: error("Codex 响应异常 status=${resp.status}")
        val primary = rateLimit.optObject("primary_window") ?: JSONObject()
        val secondary = rateLimit.optObject("secondary_window") ?: JSONObject()
        return UsageWindow(100.0 - primary.optDouble("used_percent", 0.0), epochMs(primary.opt("reset_at"))) to
            UsageWindow(100.0 - secondary.optDouble("used_percent", 0.0), epochMs(secondary.opt("reset_at")))
    }

    private suspend fun refreshClaude(tok: JSONObject): JSONObject {
        val resp = api.postJson(
            "https://console.anthropic.com/v1/oauth/token",
            JSONObject()
                .put("grant_type", "refresh_token")
                .put("refresh_token", tok.optCleanString("refreshToken"))
                .put("client_id", CLAUDE_CLIENT_ID),
            mapOf("Content-Type" to "application/json"),
        )
        val accessToken = resp.json.optString("access_token")
        if (accessToken.isEmpty()) error("Claude 刷新失败")
        val updated = JSONObject()
            .put("accessToken", accessToken)
            .put("refreshToken", resp.json.optString("refresh_token", tok.optCleanString("refreshToken")))
            .put("expiresAt", System.currentTimeMillis() + resp.json.optLong("expires_in", 3600L) * 1000)
        store.putJson(SecureStore.KEY_CLAUDE, updated)
        return updated
    }

    private suspend fun refreshCodex(tok: JSONObject): JSONObject {
        val resp = api.postJson(
            "https://auth.openai.com/oauth/token",
            JSONObject()
                .put("grant_type", "refresh_token")
                .put("refresh_token", tok.optCleanString("refreshToken"))
                .put("client_id", CODEX_CLIENT_ID),
            mapOf("Content-Type" to "application/json"),
        )
        val accessToken = resp.json.optString("access_token")
        if (accessToken.isEmpty()) error("Codex 刷新失败")
        val updated = JSONObject()
            .put("accessToken", accessToken)
            .put("refreshToken", resp.json.optString("refresh_token", tok.optCleanString("refreshToken")))
            .put("accountId", tok.optCleanString("accountId"))
        store.putJson(SecureStore.KEY_CODEX, updated)
        return updated
    }

    private suspend fun callClaude(tok: JSONObject): HttpJson {
        return api.getJson(
            "https://api.anthropic.com/api/oauth/usage",
            mapOf(
                "Authorization" to "Bearer ${tok.optCleanString("accessToken")}",
                "anthropic-beta" to "oauth-2025-04-20",
                "User-Agent" to "claude-cli",
            ),
        )
    }

    private suspend fun callCodex(tok: JSONObject): HttpJson {
        return api.getJson(
            "https://chatgpt.com/backend-api/wham/usage",
            mapOf(
                "Authorization" to "Bearer ${tok.optCleanString("accessToken")}",
                "chatgpt-account-id" to tok.optCleanString("accountId"),
                "User-Agent" to "codex-cli",
            ),
        )
    }

    private suspend fun refreshMiniMax(tok: JSONObject): JSONObject {
        val region = miniMaxRegion(tok)
        val resp = api.postForm(
            "${region.account}/oauth2/token",
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to tok.optCleanString("refreshToken"),
                "client_id" to MINIMAX_CLIENT_ID,
            ),
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        )
        if (resp.json.optString("status") != "success" || resp.json.optString("access_token").isEmpty()) {
            error("MiniMax 刷新失败")
        }
        val updated = JSONObject(tok.toString())
            .put("planApiKey", resp.json.optString("access_token"))
            .put("authorizationToken", resp.json.optString("access_token"))
            .put("refreshToken", resp.json.optString("refresh_token", tok.optCleanString("refreshToken")))
            .put("expiresAt", resp.json.optString("expired_in", tok.optString("expiresAt")))
        store.putJson(SecureStore.KEY_MINIMAX, updated)
        return updated
    }

    private suspend fun fetchMiniMaxPlan(rawCfg: JSONObject): MiniMaxPlan {
        var cfg = rawCfg
        val expiresAt = parseDateMs(cfg.optString("expiresAt"))
        if (cfg.optCleanString("refreshToken").isNotEmpty() && expiresAt != null && System.currentTimeMillis() > expiresAt - 5 * 60_000) {
            cfg = runCatching { refreshMiniMax(cfg) }.getOrDefault(cfg)
        }
        var resp = callMiniMaxPlan(cfg)
        if ((resp.status == 401 || resp.status == 403) && cfg.optCleanString("refreshToken").isNotEmpty()) {
            cfg = refreshMiniMax(cfg)
            resp = callMiniMaxPlan(cfg)
        }
        val base = resp.json.optObject("base_resp")
        if (resp.status != 200 || (base?.optInt("status_code", 0) ?: 0) != 0) {
            error("MiniMax 套餐响应异常 status=${resp.status}")
        }
        val models = resp.json.optJSONArray("model_remains") ?: JSONArray()
        val rows = buildList {
            for (i in 0 until models.length()) {
                val row = models.optJSONObject(i) ?: continue
                val remain = remainFromUsage(
                    row.opt("current_interval_total_count"),
                    row.opt("current_interval_usage_count"),
                    row.opt("current_interval_remaining_percent"),
                ) ?: continue
                add(remain to epochMs(row.opt("end_time")))
            }
        }
        if (rows.isEmpty()) error("MiniMax 套餐数据为空")
        return MiniMaxPlan(
            remain = rows.minOf { it.first },
            resetAt = rows.mapNotNull { it.second }.minOrNull(),
            detail = "${models.length()}项额度",
        )
    }

    private suspend fun callMiniMaxPlan(cfg: JSONObject): HttpJson {
        val region = miniMaxRegion(cfg)
        return api.getJson(
            "${region.api}/v1/token_plan/remains",
            mapOf(
                "Authorization" to "Bearer ${cfg.optCleanString("planApiKey")}",
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "MM-API-Source" to "ai-quota-widget-android",
            ),
        )
    }

    private suspend fun fetchMiniMaxBalance(cfg: JSONObject): MiniMaxBalance {
        if (cfg.optCleanString("balanceProxyUrl").isNotEmpty()) return fetchMiniMaxBalanceProxy(cfg)
        var lastError = "MiniMax API 余额请求失败"
        val regions = if (cfg.optString("region") == "cn") {
            listOf(miniMaxRegion("cn"), miniMaxRegion("global"))
        } else {
            listOf(miniMaxRegion("global"), miniMaxRegion("cn"))
        }
        for (region in regions) {
            runCatching {
                val resp = callMiniMaxBalance(cfg, region)
                val data = resp.json.optObject("data") ?: resp.json
                val base = data.optObject("base_resp") ?: resp.json.optObject("base_resp")
                if (resp.status == 200 && (base?.optInt("status_code", 0) ?: 0) == 0) {
                    return parseMiniMaxBalance(data)
                }
                lastError = "${region.accountApi} HTTP ${resp.status} status_code=${base?.optInt("status_code") ?: "?"}"
            }.onFailure {
                lastError = "${region.accountApi} ${it.message ?: it::class.java.simpleName}"
            }
        }
        error(lastError)
    }

    private suspend fun fetchMiniMaxBalanceProxy(cfg: JSONObject): MiniMaxBalance {
        val token = cfg.optCleanString("balanceProxyToken")
        val resp = api.getJson(
            cfg.optCleanString("balanceProxyUrl"),
            buildMap {
                put("Accept", "application/json")
                if (token.isNotEmpty()) {
                    put("Authorization", "Bearer $token")
                    put("X-Widget-Token", token)
                }
            },
        )
        val base = resp.json.optObject("base_resp") ?: JSONObject()
        if (resp.status != 200 || base.optInt("status_code", 0) != 0) {
            error("Worker HTTP ${resp.status} status_code=${base.optInt("status_code", 0)}")
        }
        return parseMiniMaxBalance(resp.json.optObject("minimaxWallet") ?: resp.json.optObject("data") ?: resp.json)
    }

    private suspend fun callMiniMaxBalance(cfg: JSONObject, region: MiniMaxRegion): HttpJson {
        val cookie = cfg.optCleanString("balanceCookie")
        val headers = mutableMapOf(
            "Cookie" to cookie,
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Content-Type" to "application/json",
            "X-Requested-With" to "XMLHttpRequest",
            "Origin" to region.platform,
            "Referer" to "${region.platform}/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-site",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36",
        )
        cookieValue(cookie, "minimax_group_id_v2").takeIf { it.isNotEmpty() }?.let { headers["X-Group-Id"] = it }
        val webToken = cookieValue(cookie, "_token")
        if (webToken.isNotEmpty()) {
            headers["Token"] = webToken
            headers["Authorization"] = "Bearer $webToken"
            jwtPayload(webToken).optObject("user")?.opt("id")?.toString()?.let { headers["Userid"] = it }
        }
        cfg.optCleanString("authorizationToken").takeIf { it.isNotEmpty() }?.let {
            headers["Authorization"] = "Bearer $it"
        }
        return api.getJson("${region.accountApi}/account/query_balance", headers)
    }

    private fun parseMiniMaxBalance(data: JSONObject): MiniMaxBalance {
        var balance: Double? = null
        for (key in listOf("amount", "available_amount", "cash_balance", "points_balance", "point_balance", "credits_balance", "credit_balance", "balance")) {
            balance = numberValue(data.opt(key))
            if (balance != null) break
        }
        if (balance == null) error("MiniMax API 余额字段缺失")
        val cash = numberValue(data.opt("cash_balance"))
        val voucher = numberValue(data.opt("voucher_balance"))
        val detail = if (cash != null || voucher != null) {
            "现金 ¥${"%.2f".format(cash ?: 0.0)} / 代金券 ¥${"%.2f".format(voucher ?: 0.0)}"
        } else {
            val updatedAt = parseDateMs(data.optString("updated_at"))
            if (updatedAt != null) "Worker ${agoText(updatedAt)}" else "账户钱包"
        }
        return MiniMaxBalance(balance, "¥${"%.2f".format(balance)}", detail)
    }

    private fun miniMaxRegion(cfg: JSONObject): MiniMaxRegion = miniMaxRegion(cfg.optString("region"))

    private fun miniMaxRegion(region: String): MiniMaxRegion {
        return if (region == "cn") {
            MiniMaxRegion(
                api = "https://api.minimaxi.com",
                platform = "https://platform.minimaxi.com",
                account = "https://account.minimaxi.com",
                accountApi = "https://www.minimaxi.com",
            )
        } else {
            MiniMaxRegion(
                api = "https://api.minimax.io",
                platform = "https://platform.minimax.io",
                account = "https://account.minimax.io",
                accountApi = "https://www.minimax.io",
            )
        }
    }

    private fun cookieValue(cookie: String, name: String): String {
        return cookie.split(";").firstNotNullOfOrNull { part ->
            val idx = part.indexOf("=")
            if (idx <= 0) null else {
                val key = part.substring(0, idx).trim()
                val value = part.substring(idx + 1).trim()
                if (key == name) value else null
            }
        }.orEmpty()
    }

    private fun jwtPayload(token: String): JSONObject {
        return runCatching {
            val part = token.split(".").getOrNull(1).orEmpty()
            val padded = part.replace('-', '+').replace('_', '/').padEnd((part.length + 3) / 4 * 4, '=')
            JSONObject(String(Base64.decode(padded, Base64.DEFAULT)))
        }.getOrDefault(JSONObject())
    }

    private fun errorCard(provider: ProviderId, message: String): CardSnapshot {
        return CardSnapshot(
            provider = provider,
            title = provider.title,
            primaryLabel = "状态",
            primaryValue = "离线",
            secondaryLabel = "",
            secondaryValue = "",
            footer = message.take(24),
            accent = "#ff453a",
            isStale = true,
            error = message,
        )
    }

    private fun cachedFooter(error: Throwable): String {
        return (error.message ?: "请求失败").take(18)
    }

    private data class MiniMaxRegion(
        val api: String,
        val platform: String,
        val account: String,
        val accountApi: String,
    )

    companion object {
        private const val CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        private const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        private const val MINIMAX_CLIENT_ID = "659cf4c1-615c-45f6-a5f6-4bf15eb476e5"
    }
}
