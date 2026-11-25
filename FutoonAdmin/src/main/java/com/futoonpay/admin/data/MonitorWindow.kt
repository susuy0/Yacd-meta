package com.futoonpay.admin.data

enum class MonitorWindow(val label: String, val key: String) {
    DAY("24小时", "24hour"), TWELVE_HOURS("12小时", "12hour"), SIX_HOURS("6小时", "6hour"),
    HOUR("1小时", "1hour"), HALF_HOUR("半小时", "30min"), FIVE_MINUTES("5分钟", "5min")
}

data class MonitorWindowMetrics(val rate: Double?, val successful: Int?, val total: Int?, val processing: Int?)

fun ChannelMonitorItem.metrics(window: MonitorWindow): MonitorWindowMetrics {
    fun find(vararg keys: String): String? = fields.firstOrNull { (label, _) ->
        val normalized = label.lowercase().replace(" ", "").replace("_", "")
        keys.any { normalized == it.lowercase().replace("_", "") }
    }?.second
    fun count(vararg keys: String) = find(*keys)?.replace(",", "")?.toIntOrNull()?.takeIf { it >= 0 }
    val key = window.key
    val chinese = when (window) { MonitorWindow.HALF_HOUR -> "30分钟"; MonitorWindow.FIVE_MINUTES -> "5分钟"; else -> window.label }
    val rate = when (window) {
        MonitorWindow.DAY -> oneDayRate; MonitorWindow.HOUR -> oneHourRate
        MonitorWindow.HALF_HOUR -> thirtyMinuteRate; MonitorWindow.FIVE_MINUTES -> fiveMinuteRate
        else -> find("success_rate_$key", "rate_$key", "${chinese}成功率")?.let { raw ->
            Regex("\\d+(?:\\.\\d+)?").find(raw)?.value?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
        }
    }
    return MonitorWindowMetrics(rate,
        count("success_count_$key", "successful_$key", "${chinese}成功数"),
        count("total_count_$key", "total_$key", "${chinese}总订单"),
        count("processing_count_$key", "pending_$key", "${chinese}处理中"))
}

/** Operational enabled status; never derives a health label from a rate or detection result. */
fun ChannelMonitorItem.enabledLabel(): String = when (status.trim().lowercase()) {
    "1", "启用", "已启用", "正常", "enabled" -> "启用"
    "0", "禁用", "停用", "已停用", "disabled" -> "停用"
    else -> status.ifBlank { "未标注状态" }
}
