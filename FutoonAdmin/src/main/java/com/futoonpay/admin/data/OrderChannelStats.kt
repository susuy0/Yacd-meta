package com.futoonpay.admin.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class OfficialChannelRate(
    val channelId: String,
    val name: String,
    val fiveMinutes: Double?,
    val thirtyMinutes: Double?,
    val oneHour: Double?,
    val oneDay: Double?,
    val total: Int? = null,
    val successful: Int? = null,
    val processing: Int? = null,
)

internal fun currentPageChannelStats(
    orders: List<OrderSummary>,
    now: LocalDateTime = LocalDateTime.now(),
): Map<String, OrderChannelStat> = orders.mapNotNull { order ->
    val channelId = order.channelId.normalizedChannelId()
    val createdAt = order.createdAt.toLocalDateTimeOrNull() ?: return@mapNotNull null
    val ageSeconds = Duration.between(createdAt, now).seconds
    if (channelId.isBlank() || ageSeconds !in 0..300) return@mapNotNull null
    Triple(channelId, order, ageSeconds)
}.groupBy { it.first }.mapValues { (channelId, entries) ->
    val buckets = MutableList(5) { 0 }
    entries.forEach { (_, _, ageSeconds) ->
        val bucket = (ageSeconds / 60).toInt().coerceIn(0, 4)
        buckets[bucket] += 1
    }
    val ordersForChannel = entries.map { it.second }
    val successful = ordersForChannel.count { it.status == 1 }
    OrderChannelStat(
        channelId = channelId,
        fiveMinuteRate = if (ordersForChannel.isEmpty()) null else successful * 100.0 / ordersForChannel.size,
        total = ordersForChannel.size,
        successful = successful,
        processing = ordersForChannel.count { it.status == 0 },
        refunded = ordersForChannel.count { it.status == 2 },
        frozen = ordersForChannel.count { it.status == 3 },
        minuteBuckets = buckets,
        recentOrders = ordersForChannel.sortedByDescending { it.createdAt }.take(15).map {
            ChannelOrderSample(it.tradeNo, it.paidAmount.ifBlank { it.amount }, it.status, it.createdAt)
        },
        source = ChannelRateSource.CURRENT_PAGE,
        note = "官网接口不可用时，仅统计当前页最近5分钟订单",
    )
}

internal fun parseOfficialChannelRates(payload: JSONObject, fallbackId: String = ""): Map<String, OfficialChannelRate> {
    val rows = channelRateRows(payload)
    return rows.mapNotNull { row ->
        val id = row.text("id").ifBlank { row.text("channel_id") }.ifBlank { fallbackId }.normalizedChannelId()
        if (id.isBlank()) return@mapNotNull null
        id to OfficialChannelRate(
            channelId = id,
            name = row.text("name").ifBlank { row.text("channel_name") },
            fiveMinutes = row.percent("success_rate_5min", "rate_5min"),
            thirtyMinutes = row.percent("success_rate_30min", "rate_30min"),
            oneHour = row.percent("success_rate_1hour", "rate_1hour"),
            oneDay = row.percent("success_rate_24hour", "rate_24hour"),
            total = row.count("total_count_5min", "total_5min"),
            successful = row.count("success_count_5min", "successful_5min"),
            processing = row.count("processing_count_5min", "pending_5min"),
        )
    }.toMap()
}

internal fun mergeOrderChannelStats(
    channelIds: Collection<String>,
    pageStats: Map<String, OrderChannelStat>,
    officialRates: Map<String, OfficialChannelRate>,
    fallbackReason: String = "",
): OrderChannelStatsResult {
    var fallbackUsed = false
    val stats = channelIds.map(String::normalizedChannelId).filter(String::isNotBlank).distinct().associateWith { channelId ->
        val page = pageStats[channelId] ?: OrderChannelStat(channelId = channelId)
        val official = officialRates[channelId]
        if (official?.fiveMinutes != null) {
            val officialCounts = official.total != null && official.successful != null && official.successful <= official.total
            page.copy(
                channelName = official.name,
                fiveMinuteRate = if (officialCounts && official.total == 0) null else official.fiveMinutes,
                thirtyMinuteRate = official.thirtyMinutes,
                oneHourRate = official.oneHour,
                oneDayRate = official.oneDay,
                total = if (officialCounts) official.total!! else page.total,
                successful = if (officialCounts) official.successful!! else page.successful,
                processing = if (officialCounts) official.processing ?: 0 else page.processing,
                countsOfficial = officialCounts,
                processingAvailable = !officialCounts || official.processing != null,
                source = ChannelRateSource.OFFICIAL,
                note = if (officialCounts) "成功率与订单数量来自官网；趋势和最近订单为当前页样本" else "成功率来自官网；订单数量、趋势与最近订单为当前页样本",
            )
        } else {
            fallbackUsed = true
            page.copy(
                source = ChannelRateSource.CURRENT_PAGE,
                note = fallbackReason.ifBlank { "官网未返回该通道监测值，显示当前页最近5分钟样本" },
            )
        }
    }
    return OrderChannelStatsResult(stats, fallbackUsed, fallbackReason)
}

private fun channelRateRows(payload: JSONObject): List<JSONObject> {
    val normalized = payload.rows().objectRows()
    if (normalized.isNotEmpty()) return normalized
    val data = payload.optJSONObject("data")
    if (data != null && (data.has("id") || data.has("channel_id"))) return listOf(data)
    if (payload.has("id") || payload.has("channel_id")) return listOf(payload)
    return emptyList()
}

private fun JSONObject.percent(vararg names: String): Double? {
    val raw = names.firstNotNullOfOrNull { name -> text(name).takeIf(String::isNotBlank) } ?: return null
    val number = Regex("-?\\d+(?:\\.\\d+)?").find(raw)?.value?.toDoubleOrNull() ?: return null
    return if (!raw.contains('%') && number in 0.0..1.0) number * 100 else number
}

private fun JSONObject.count(vararg names: String): Int? = names.firstNotNullOfOrNull { name ->
    text(name).replace(",", "").toIntOrNull()?.takeIf { it >= 0 }
}

private fun String.normalizedChannelId(): String = Regex("\\d+").find(this)?.value?.toLongOrNull()?.toString().orEmpty()

private fun String.toLocalDateTimeOrNull(): LocalDateTime? = runCatching {
    LocalDateTime.parse(trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}.getOrNull()
