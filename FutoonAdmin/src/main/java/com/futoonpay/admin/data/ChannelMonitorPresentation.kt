package com.futoonpay.admin.data

enum class MonitorHealth(val label: String, internal val rank: Int) {
    ABNORMAL("异常", 0),
    UNKNOWN("待确认", 1),
    DISABLED("已停用", 2),
    HEALTHY("正常", 3),
}

enum class MonitorSortDimension(val label: String) {
    HEALTH("健康状态"),
    NAME("通道名称"),
    FIVE_MINUTES("5 分钟成功率"),
    THIRTY_MINUTES("30 分钟成功率"),
    ONE_HOUR("1 小时成功率"),
    ONE_DAY("24 小时成功率"),
    LAST_CHECKED("最近检测"),
    ID("通道 ID"),
}

enum class MonitorGroupDimension(val label: String) {
    NONE("不分组"),
    HEALTH("健康状态"),
    PAYMENT_TYPE("支付方式"),
    PLUGIN("支付插件"),
}

data class ChannelMonitorItem(
    val id: String,
    val name: String,
    val paymentType: String,
    val plugin: String,
    val status: String,
    val result: String,
    val lastChecked: String,
    val fiveMinuteRate: Double?,
    val thirtyMinuteRate: Double?,
    val oneHourRate: Double?,
    val oneDayRate: Double?,
    val health: MonitorHealth,
    val fields: List<Pair<String, String>>,
    val actions: List<NativeAction>,
) {
    fun searchableText(): String = fields.joinToString(" ") { "${it.first} ${it.second}" }

    fun groupLabel(dimension: MonitorGroupDimension): String = when (dimension) {
        MonitorGroupDimension.NONE -> "全部通道"
        MonitorGroupDimension.HEALTH -> health.label
        MonitorGroupDimension.PAYMENT_TYPE -> paymentType.ifBlank { "未标注支付方式" }
        MonitorGroupDimension.PLUGIN -> plugin.ifBlank { "未标注插件" }
    }
}

fun NativeTable.toChannelMonitorItems(): List<ChannelMonitorItem> = rows.mapIndexed { rowIndex, row ->
    val fields = headers.mapIndexedNotNull { index, header ->
        row.getOrNull(index)?.takeIf(String::isNotBlank)?.let { header.ifBlank { "字段 ${index + 1}" } to it }
    }
    fun value(vararg tokens: String): String {
        val index = headers.indexOfFirst { header ->
            val normalized = header.normalizedMonitorHeader()
            tokens.any { token -> normalized == token || normalized.contains(token) }
        }
        return row.getOrNull(index).orEmpty()
    }

    val id = value("id", "通道id")
    val name = value("名称", "通道名称", "channelname").ifBlank { value("通道") }
    val paymentType = value("支付方式", "typename", "支付类型")
    val plugin = value("插件", "plugin")
    val status = value("状态", "status")
    val result = value("监测结果", "检测结果", "result")
    val lastChecked = value("最近检测", "最后检测", "检测时间", "lasttime")
    ChannelMonitorItem(
        id = id,
        name = name,
        paymentType = paymentType,
        plugin = plugin,
        status = status,
        result = result,
        lastChecked = lastChecked,
        fiveMinuteRate = value("5分钟成功率", "5min").monitorPercent(),
        thirtyMinuteRate = value("30分钟成功率", "30min").monitorPercent(),
        oneHourRate = value("1小时成功率", "1hour").monitorPercent(),
        oneDayRate = value("24小时成功率", "24hour").monitorPercent(),
        health = monitorHealth(status, result),
        fields = fields,
        actions = rowActions.getOrNull(rowIndex).orEmpty(),
    )
}

fun sortChannelMonitorItems(
    items: List<ChannelMonitorItem>,
    primary: MonitorSortDimension,
    primaryAscending: Boolean,
    secondary: MonitorSortDimension,
    secondaryAscending: Boolean,
): List<ChannelMonitorItem> = items.sortedWith(
    Comparator { left, right ->
        compareMonitorDimension(left, right, primary, primaryAscending)
            .takeIf { it != 0 }
            ?: compareMonitorDimension(left, right, secondary, secondaryAscending)
                .takeIf { it != 0 }
            ?: compareMonitorValues(left.id.toDoubleOrNull(), right.id.toDoubleOrNull(), true)
                .takeIf { it != 0 }
            ?: left.name.compareTo(right.name, ignoreCase = true)
    },
)

private fun compareMonitorDimension(
    left: ChannelMonitorItem,
    right: ChannelMonitorItem,
    dimension: MonitorSortDimension,
    ascending: Boolean,
): Int = when (dimension) {
    MonitorSortDimension.HEALTH -> compareMonitorValues(left.health.rank, right.health.rank, ascending)
    MonitorSortDimension.NAME -> compareMonitorValues(left.name, right.name, ascending)
    MonitorSortDimension.FIVE_MINUTES -> compareMonitorValues(left.fiveMinuteRate, right.fiveMinuteRate, ascending)
    MonitorSortDimension.THIRTY_MINUTES -> compareMonitorValues(left.thirtyMinuteRate, right.thirtyMinuteRate, ascending)
    MonitorSortDimension.ONE_HOUR -> compareMonitorValues(left.oneHourRate, right.oneHourRate, ascending)
    MonitorSortDimension.ONE_DAY -> compareMonitorValues(left.oneDayRate, right.oneDayRate, ascending)
    MonitorSortDimension.LAST_CHECKED -> compareMonitorValues(left.lastChecked.monitorTimestamp(), right.lastChecked.monitorTimestamp(), ascending)
    MonitorSortDimension.ID -> compareMonitorValues(left.id.toDoubleOrNull(), right.id.toDoubleOrNull(), ascending)
}

private fun <T : Comparable<T>> compareMonitorValues(left: T?, right: T?, ascending: Boolean): Int {
    if (left == null && right == null) return 0
    if (left == null) return 1
    if (right == null) return -1
    val compared = left.compareTo(right)
    return if (ascending) compared else -compared
}

private fun String.normalizedMonitorHeader(): String = lowercase().replace(" ", "").replace("_", "")

private fun String.monitorPercent(): Double? {
    val number = Regex("-?\\d+(?:\\.\\d+)?").find(this)?.value?.toDoubleOrNull() ?: return null
    return if (!contains('%') && number in 0.0..1.0) number * 100 else number
}

private fun String.monitorTimestamp(): Long? = filter(Char::isDigit).take(14).toLongOrNull()

private fun monitorHealth(status: String, result: String): MonitorHealth {
    val normalizedStatus = status.trim().lowercase()
    val normalizedResult = result.trim().lowercase()
    val combined = "$normalizedStatus $normalizedResult"
    return when {
        normalizedStatus == "0" || listOf("关闭", "禁用", "停用", "disabled", "offline").any(combined::contains) -> MonitorHealth.DISABLED
        normalizedResult == "0" || listOf("异常", "失败", "错误", "超时", "不可用", "failed", "error", "timeout").any(combined::contains) -> MonitorHealth.ABNORMAL
        normalizedStatus == "1" || normalizedResult == "1" || listOf("正常", "成功", "可用", "启用", "开启", "success", "healthy").any(combined::contains) -> MonitorHealth.HEALTHY
        else -> MonitorHealth.UNKNOWN
    }
}
