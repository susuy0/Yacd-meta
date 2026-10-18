package com.futoonpay.admin.data

import org.json.JSONArray
import org.json.JSONObject

data class DashboardStats(
    val orderCount: String = "—",
    val merchantCount: String = "—",
    val merchantBalance: String = "—",
    val settledTotal: String = "—",
    val yesterdaySettled: String = "—",
    val todayTotal: String = "—",
    val todayByType: List<Pair<String, String>> = emptyList(),
    val recentDailyTotals: List<Pair<String, String>> = emptyList(),
    val todayOrderCount: Int? = null,
    val todaySuccessfulCount: Int? = null,
    val pendingSettlementCount: Int? = null,
    val pendingOrderCount: Int? = null,
    val pendingDomainCount: Int? = null,
    val monitorChannelCount: Int? = null,
    val todayByCurrency: Map<String, String> = emptyMap(),
    val updatedAt: Long = 0L,
)

data class OrderSummary(
    val tradeNo: String,
    val merchantTradeNo: String,
    val merchantId: String,
    val domain: String,
    val name: String,
    val amount: String,
    val paidAmount: String,
    val merchantIncome: String,
    val paymentType: String,
    val channelId: String,
    val plugin: String,
    val createdAt: String,
    val completedAt: String,
    val status: Int,
    val settlementStatus: Int,
    val currency: String = "CNY",
    val merchantName: String = "",
)

data class OrderDetail(
    val tradeNo: String,
    val merchantTradeNo: String,
    val apiTradeNo: String,
    val merchantId: String,
    val paymentType: String,
    val channelId: String,
    val channelName: String,
    val plugin: String,
    val name: String,
    val amount: String,
    val paidAmount: String,
    val merchantIncome: String,
    val createdAt: String,
    val completedAt: String,
    val buyer: String,
    val domain: String,
    val ip: String,
    val parameter: String,
    val status: Int,
    val settlementStatus: Int,
    val notifyCount: Int,
    val currency: String = "CNY",
)

data class SettlementSummary(
    val id: String,
    val merchantId: String,
    val username: String,
    val payType: String,
    val chain: String,
    val account: String,
    val amount: String,
    val actualAmount: String,
    val usdtAmount: String,
    val usdtRate: String,
    val createdAt: String,
    val completedAt: String,
    val status: Int,
    val automatic: Boolean,
    val fullSettlement: Boolean,
    val result: String,
    val currency: String = "CNY",
    val fee: String = "",
)

data class MerchantSummary(
    val uid: String,
    val username: String,
    val groupName: String,
    val balance: String,
    val frozenBalance: String,
    val telegramId: String,
    val website: String,
    val createdAt: String,
    val status: Int,
    val paymentStatus: Int,
    val settlementStatus: Int,
    val account: String = "",
    val remark: String = "",
    val inviteCode: String = "",
    val recommenderId: String = "",
    val recommenderPercent: String = "",
    val balances: Map<String, String> = emptyMap(),
) {
    val paymentEnabled: Boolean get() = paymentStatus == 1
    val settlementEnabled: Boolean get() = settlementStatus == 1
}

data class MerchantFlowPoint(
    val date: String,
    val total: Double,
)

data class MerchantFlowSummary(
    val merchantId: String,
    val points: List<MerchantFlowPoint>,
    val alipayTotal: Double = 0.0,
    val wechatTotal: Double = 0.0,
    val qqTotal: Double = 0.0,
    val currencyPoints: Map<String, List<MerchantFlowPoint>> = emptyMap(),
) {
    val total: Double get() = points.sumOf { it.total }
    val dailyAverage: Double get() = if (points.isEmpty()) 0.0 else total / points.size
    val peak: Double get() = points.maxOfOrNull { it.total } ?: 0.0
}

enum class ChannelRateSource(val label: String) {
    OFFICIAL("官网通道监测"),
    CURRENT_PAGE("当前页降级样本"),
}

data class ChannelOrderSample(
    val tradeNo: String,
    val amount: String,
    val status: Int,
    val createdAt: String,
)

data class OrderChannelStat(
    val channelId: String,
    val channelName: String = "",
    val fiveMinuteRate: Double? = null,
    val thirtyMinuteRate: Double? = null,
    val oneHourRate: Double? = null,
    val oneDayRate: Double? = null,
    val total: Int = 0,
    val successful: Int = 0,
    val processing: Int = 0,
    val refunded: Int = 0,
    val frozen: Int = 0,
    val minuteBuckets: List<Int> = List(5) { 0 },
    val recentOrders: List<ChannelOrderSample> = emptyList(),
    val source: ChannelRateSource = ChannelRateSource.CURRENT_PAGE,
    val countsOfficial: Boolean = false,
    val processingAvailable: Boolean = true,
    val note: String = "",
)

data class OrderChannelStatsResult(
    val stats: Map<String, OrderChannelStat>,
    val fallbackUsed: Boolean,
    val message: String = "",
)

data class DomainAuditSummary(
    val id: String,
    val merchantId: String,
    val domain: String,
    val status: Int,
    val createdAt: String,
    val auditedAt: String,
    val remark: String = "",
    val merchantName: String = "",
    val projectName: String = "",
    val rawStatus: String = status.toString(),
)

data class PagedResult<T>(
    val total: Int,
    val rows: List<T>,
)

data class ApiResult(
    val success: Boolean,
    val message: String,
    val recordId: String = "",
    val partial: Boolean = false,
)

data class GenericRow(
    val id: String,
    val values: Map<String, String>,
)

data class NativeOption(val value: String, val label: String)

data class NativeField(
    val name: String,
    val label: String,
    val value: String,
    val type: String,
    val options: List<NativeOption> = emptyList(),
    val sensitive: Boolean = false,
    val required: Boolean = false,
    val readOnly: Boolean = false,
)

data class NativeForm(
    val title: String,
    val action: String,
    val method: String,
    val fields: List<NativeField>,
)

data class NativeAction(
    val label: String,
    val endpoint: String,
    val method: String = "GET",
    val fields: Map<String, String> = emptyMap(),
    val destructive: Boolean = false,
)

data class NativeTable(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val rowActions: List<List<NativeAction>> = emptyList(),
)

data class NativeDocument(
    val title: String,
    val notices: List<String>,
    val tables: List<NativeTable>,
    val forms: List<NativeForm>,
    val actions: List<NativeAction> = emptyList(),
)

internal fun JSONObject.text(name: String): String {
    if (!has(name) || isNull(name)) return ""
    return opt(name)?.toString().orEmpty()
}

internal fun JSONObject.intValue(name: String): Int = text(name).toIntOrNull() ?: 0

internal fun JSONObject.boolFlag(name: String): Boolean = text(name) == "1" || optBoolean(name, false)

/**
 * 后台不同支付模块使用了多种列表包装：rows、data、list、items、result、records。
 * data/result 还可能再包一层对象。统一在这里展开，避免合法数据被误判为“暂无数据”。
 */
internal fun JSONObject.rows(): JSONArray {
    val keys = listOf("rows", "data", "list", "items", "result", "records", "channels")
    keys.forEach { key -> normalizedRows(opt(key))?.takeIf { it.length() > 0 }?.let { return it } }
    keys.forEach { key ->
        optJSONObject(key)?.rows()?.takeIf { it.length() > 0 }?.let { return it }
    }
    return JSONArray()
}

/**
 * 某些后台列表把 rows 再编码成 JSON 字符串，或用 {id: row} 对象代替数组。
 * 这里统一展开，避免接口真实有数据却在原生端被当成空列表。
 */
private fun normalizedRows(value: Any?): JSONArray? = when (value) {
    is JSONArray -> value
    is JSONObject -> {
        value.rows().takeIf { it.length() > 0 } ?: run {
            val records = JSONArray()
            val keys = buildList {
                val iterator = value.keys()
                while (iterator.hasNext()) add(iterator.next())
            }
            val recordKeys = keys.filterNot { it in setOf("code", "msg", "message", "total", "count") }
            val wrapperKeys = setOf("rows", "data", "list", "items", "result", "records", "channels")
            if (recordKeys.isNotEmpty() && recordKeys.none { it in wrapperKeys } &&
                recordKeys.all { value.opt(it) is JSONObject || value.opt(it) is JSONArray }
            ) {
                recordKeys.forEach { records.put(value.opt(it)) }
                records
            } else null
        }
    }
    is String -> {
        val raw = value.trim()
        when {
            raw.startsWith("[") -> runCatching { JSONArray(raw) }.getOrNull()
            raw.startsWith("{") -> runCatching { normalizedRows(JSONObject(raw)) }.getOrNull()
            else -> null
        }
    }
    else -> null
}

/** 将对象行、数组行和 JSON 字符串行统一成对象，供支付配置列表使用。 */
internal fun JSONArray.objectRows(fieldNames: List<String> = emptyList()): List<JSONObject> = buildList {
    for (index in 0 until length()) {
        when (val item = opt(index)) {
            is JSONObject -> add(item)
            is JSONArray -> {
                val row = JSONObject()
                for (column in 0 until item.length()) {
                    row.put(fieldNames.getOrNull(column) ?: "column_$column", item.opt(column))
                }
                add(row)
            }
            is String -> {
                val raw = item.trim()
                when {
                    raw.startsWith("{") -> runCatching { JSONObject(raw) }.getOrNull()?.let(::add)
                    raw.startsWith("[") -> runCatching { JSONArray(raw) }.getOrNull()?.let { array ->
                        val row = JSONObject()
                        for (column in 0 until array.length()) {
                            row.put(fieldNames.getOrNull(column) ?: "column_$column", array.opt(column))
                        }
                        add(row)
                    }
                }
            }
        }
    }
}
