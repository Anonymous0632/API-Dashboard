package com.anonymous.apidashboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aiquota_cache", Context.MODE_PRIVATE)

    fun save(cards: List<CardSnapshot>) {
        val arr = JSONArray()
        cards.forEach { card ->
            arr.put(
                JSONObject()
                    .put("provider", card.provider.key)
                    .put("title", card.title)
                    .put("primaryLabel", card.primaryLabel)
                    .put("primaryValue", card.primaryValue)
                    .put("secondaryLabel", card.secondaryLabel)
                    .put("secondaryValue", card.secondaryValue)
                    .put("footer", card.footer)
                    .put("accent", card.accent)
                    .put("isStale", card.isStale)
                    .put("error", card.error)
            )
        }
        prefs.edit()
            .putString(KEY_CARDS, arr.toString())
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun load(): List<CardSnapshot> {
        val raw = prefs.getString(KEY_CARDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val provider = ProviderId.fromKey(obj.optString("provider")) ?: continue
                    add(
                        CardSnapshot(
                            provider = provider,
                            title = obj.optString("title", provider.title),
                            primaryLabel = obj.optString("primaryLabel"),
                            primaryValue = obj.optString("primaryValue"),
                            secondaryLabel = obj.optString("secondaryLabel"),
                            secondaryValue = obj.optString("secondaryValue"),
                            footer = obj.optString("footer"),
                            accent = obj.optString("accent", "#34c759"),
                            isStale = obj.optBoolean("isStale", false),
                            error = obj.optString("error").ifEmpty { null },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun updatedAt(): Long? {
        val value = prefs.getLong(KEY_UPDATED_AT, 0L)
        return if (value > 0L) value else null
    }

    companion object {
        private const val KEY_CARDS = "cards"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}
