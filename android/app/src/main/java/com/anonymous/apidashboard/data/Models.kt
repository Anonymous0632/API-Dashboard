package com.anonymous.apidashboard.data

enum class ProviderId(val key: String, val title: String) {
    Claude("claude", "Claude"),
    Codex("codex", "Codex"),
    MiniMax("minimax", "MiniMax"),
    DeepSeek("deepseek", "DeepSeek");

    companion object {
        val keys = entries.map { it.key }.toSet()

        fun fromKey(key: String): ProviderId? = entries.firstOrNull { it.key == key }
    }
}

data class CardSnapshot(
    val provider: ProviderId,
    val title: String,
    val primaryLabel: String,
    val primaryValue: String,
    val secondaryLabel: String,
    val secondaryValue: String,
    val footer: String,
    val accent: String,
    val isStale: Boolean = false,
    val error: String? = null,
)

data class ImportResult(
    val imported: List<String>,
)

data class UsageWindow(
    val remain: Double,
    val resetAt: Long?,
)

data class MiniMaxPlan(
    val remain: Double,
    val resetAt: Long?,
    val detail: String,
)

data class MiniMaxBalance(
    val balance: Double,
    val text: String,
    val detail: String,
)
