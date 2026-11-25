package com.futoonpay.admin.data

import org.json.JSONObject

internal class ChannelUnavailableException(message: String) : java.io.IOException(message)
internal data class ChannelSnapshot(val window: MonitorWindow, val rate: Double?, val total: Int?, val successful: Int?, val processing: Int?, val updated: Long = System.currentTimeMillis()) {
    val empty get() = total == 0
    val rateText get() = if (empty) "暂无订单" else rate?.let { String.format(java.util.Locale.ROOT, "%.1f%%", it) } ?: "暂未提供"
}
internal fun parseChannelSnapshot(row: JSONObject, window: MonitorWindow): ChannelSnapshot {
    val key = window.key
    fun count(vararg names: String): Int? = names.firstNotNullOfOrNull { n -> row.optString(n).replace(",", "").toIntOrNull()?.takeIf { it >= 0 } }
    fun rate(): Double? = listOf("success_rate_$key", "rate_$key").firstNotNullOfOrNull { n ->
        val text = row.optString(n).trim(); val value = text.removeSuffix("%").toDoubleOrNull()
        value?.let { if (!text.endsWith("%") && it in 0.0..1.0) it * 100 else it }?.takeIf { it.isFinite() && it in 0.0..100.0 }
    }
    val total = count("total_count_$key", "total_$key")
    val successful = count("success_count_$key", "successful_$key")?.takeIf { total == null || it <= total }
    return ChannelSnapshot(window, if (total == 0) null else rate(), total, successful, count("processing_count_$key", "pending_$key"))
}
