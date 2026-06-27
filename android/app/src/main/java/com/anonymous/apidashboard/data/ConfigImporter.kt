package com.anonymous.apidashboard.data

import org.json.JSONArray
import org.json.JSONObject

class ConfigImporter(private val store: SecureStore) {
    fun importConfig(raw: String): ImportResult {
        val parsed = JSONObject(raw)
        val imported = mutableListOf<String>()

        parsed.optObject("claude")?.let { src ->
            if (src.optCleanString("accessToken").isNotEmpty()) {
                store.putJson(SecureStore.KEY_CLAUDE, src)
                imported += "Claude"
            }
        }

        parsed.optObject("codex")?.let { src ->
            if (src.optCleanString("accessToken").isNotEmpty()) {
                store.putJson(SecureStore.KEY_CODEX, src)
                imported += "Codex"
            }
        }

        importSettings(parsed, imported)
        importMiniMax(parsed, imported)
        importDeepSeek(parsed, imported)

        return ImportResult(imported)
    }

    private fun importSettings(parsed: JSONObject, imported: MutableList<String>) {
        val enabled = normalizeEnabled(parsed.optObject("settings")?.opt("enabled") ?: parsed.opt("enabled"))
        if (enabled != null) {
            store.putJson(SecureStore.KEY_SETTINGS, JSONObject().put("enabled", JSONArray(enabled)))
            imported += "显示设置"
        }
    }

    private fun importMiniMax(parsed: JSONObject, imported: MutableList<String>) {
        val src = parsed.optObject("minimax") ?: JSONObject()
        val planApiKey = src.optCleanString("planApiKey", "tokenPlanApiKey", "codingApiKey", "codingPlanApiKey")
        val balanceCookie = cleanCookie(src.optCleanString("balanceCookie", "apiCookie", "cookie"))
        val balanceProxyUrl = src.optCleanString("balanceProxyUrl", "walletProxyUrl", "proxyUrl")
        val balanceProxyToken = src.optCleanString("balanceProxyToken", "walletProxyToken", "proxyToken")
        val authorizationToken = src.optCleanString("authorizationToken", "accessToken")
        if (planApiKey.isEmpty() && balanceCookie.isEmpty() && balanceProxyUrl.isEmpty()) return

        val out = JSONObject()
            .put("region", if (src.optCleanString("region") == "cn") "cn" else "global")
            .put("planApiKey", planApiKey)
            .put("balanceCookie", balanceCookie)
            .put("balanceProxyUrl", balanceProxyUrl)
            .put("balanceProxyToken", balanceProxyToken)
            .put("authorizationToken", authorizationToken)
            .put("refreshToken", src.optCleanString("refreshToken"))
            .put("expiresAt", src.opt("expiresAt"))
        store.putJson(SecureStore.KEY_MINIMAX, out)
        imported += "MiniMax"
    }

    private fun importDeepSeek(parsed: JSONObject, imported: MutableList<String>) {
        val apiKey = parsed.optObject("deepseek")?.optCleanString("apiKey", "key").orEmpty()
        if (apiKey.isEmpty()) return
        store.putJson(SecureStore.KEY_DEEPSEEK, JSONObject().put("apiKey", apiKey))
        imported += "DeepSeek"
    }

    private fun cleanCookie(value: String): String {
        return value.trim().replace("&amp;", "&")
    }
}
