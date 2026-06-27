package com.anonymous.apidashboard.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class SecureStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "aiquota_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun putJson(key: String, value: JSONObject) {
        prefs.edit().putString(key, value.toString()).apply()
    }

    fun getJson(key: String): JSONObject? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun hasAnyCredential(): Boolean {
        return getJson(KEY_CLAUDE)?.optCleanString("accessToken")?.isNotEmpty() == true ||
            getJson(KEY_CODEX)?.optCleanString("accessToken")?.isNotEmpty() == true ||
            getJson(KEY_MINIMAX)?.let {
                it.optCleanString("planApiKey").isNotEmpty() ||
                    it.optCleanString("balanceProxyUrl").isNotEmpty() ||
                    it.optCleanString("balanceCookie").isNotEmpty()
            } == true ||
            getJson(KEY_DEEPSEEK)?.optCleanString("apiKey")?.isNotEmpty() == true
    }

    companion object {
        const val KEY_CLAUDE = "aiquota.claude"
        const val KEY_CODEX = "aiquota.codex"
        const val KEY_SETTINGS = "aiquota.settings"
        const val KEY_MINIMAX = "aiquota.minimax"
        const val KEY_DEEPSEEK = "aiquota.deepseek"
    }
}
