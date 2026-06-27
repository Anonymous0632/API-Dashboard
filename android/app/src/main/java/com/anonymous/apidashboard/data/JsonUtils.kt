package com.anonymous.apidashboard.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

fun JSONObject.optCleanString(name: String): String {
    return optString(name, "").trim()
}

fun JSONObject.optCleanString(vararg names: String): String {
    for (name in names) {
        val value = optCleanString(name)
        if (value.isNotEmpty()) return value
    }
    return ""
}

fun JSONObject.optObject(name: String): JSONObject? {
    return opt(name) as? JSONObject
}

fun JSONArray.toStringList(): List<String> {
    val out = mutableListOf<String>()
    for (i in 0 until length()) {
        val value = optString(i, "").trim()
        if (value.isNotEmpty()) out += value
    }
    return out
}

fun normalizeEnabled(value: Any?): List<String>? {
    return when (value) {
        is JSONArray -> value.toStringList().filter { it in ProviderId.keys }
        is JSONObject -> {
            val onlyTrue = ProviderId.entries.filter { value.opt(it.key) == true }.map { it.key }
            if (onlyTrue.isNotEmpty()) {
                onlyTrue
            } else {
                ProviderId.entries.filter { value.opt(it.key) != false }.map { it.key }
            }
        }
        else -> null
    }
}

fun numberValue(value: Any?): Double? {
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.replace(",", "").toDoubleOrNull()
        else -> null
    }
}

fun clampPercent(value: Double): Double = max(0.0, min(100.0, value))

fun remainFromUsage(total: Any?, used: Any?, percent: Any?): Double? {
    numberValue(percent)?.let { return clampPercent(it) }
    val totalNumber = numberValue(total)
    val usedNumber = numberValue(used)
    if (totalNumber == null || totalNumber <= 0 || usedNumber == null) return null
    return clampPercent((max(0.0, totalNumber - usedNumber) / totalNumber) * 100.0)
}

fun epochMs(value: Any?): Long? {
    val n = numberValue(value)?.toLong() ?: return null
    return if (n < 10_000_000_000L) n * 1000 else n
}

fun parseDateMs(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(value)?.time
        }.getOrNull()
}

fun percentText(value: Double): String {
    return "${value.toInt()}%"
}

fun colorForRemain(remain: Double): String {
    return when {
        remain > 50 -> "#34c759"
        remain > 20 -> "#ff9f0a"
        else -> "#ff3b30"
    }
}

fun balanceColor(amount: Double?, fallback: String): String {
    return when {
        amount != null && amount < 5 -> "#ff453a"
        amount != null && amount < 20 -> "#ffb340"
        else -> fallback
    }
}

fun currencySymbol(currency: String?): String {
    return when (currency) {
        "CNY", "RMB" -> "¥"
        "USD" -> "$"
        null, "" -> ""
        else -> "$currency "
    }
}

fun formatMoney(amount: Double, currency: String?): String {
    return currencySymbol(currency) + String.format(Locale.US, "%.2f", amount)
}

fun resetText(resetAt: Long?, weekly: Boolean): String {
    if (resetAt == null || resetAt <= 0) return ""
    val delta = resetAt - System.currentTimeMillis()
    if (delta <= 0) return "即将恢复"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = resetAt }
    return if (weekly) {
        "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日 恢复"
    } else {
        "%02d:%02d 恢复".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}

fun agoText(timestamp: Long?): String {
    val ts = timestamp ?: return ""
    val minutes = ((System.currentTimeMillis() - ts) / 60_000).toInt()
    return when {
        minutes <= 0 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        else -> "${minutes / 60}小时前"
    }
}
