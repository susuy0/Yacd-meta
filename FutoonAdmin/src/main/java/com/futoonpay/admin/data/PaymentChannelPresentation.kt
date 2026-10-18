package com.futoonpay.admin.data

enum class PaymentChannelTab(
    val key: String,
    val label: String,
    val websiteLabel: String,
) {
    RUNNING("assigned", "运行中", "已启用通道（已分配）"),
    DISABLED("disabled", "已停用", "未启用通道"),
    PENDING("unassigned", "待确认", "已启用通道（未分配）"),
    ALL("all", "全部", "全局全部通道"),
}

enum class PaymentChannelStatus(val label: String, internal val rank: Int) {
    DISABLED("已停用", 0),
    UNKNOWN("待确认", 1),
    ENABLED("运行中", 2),
}

enum class PaymentChannelSortDimension(val label: String) {
    STATUS("运行状态"),
    NAME("通道名称"),
    ID("通道 ID"),
    PAYMENT_TYPE("支付方式"),
    PLUGIN("支付插件"),
    RATE("预付扣点"),
}

enum class PaymentChannelGroupDimension(val label: String) {
    NONE("不分组"),
    STATUS("运行状态"),
    PAYMENT_TYPE("支付方式"),
    PLUGIN("支付插件"),
}

data class PaymentChannelItem(
    val id: String,
    val name: String,
    val paymentType: String,
    val plugin: String,
    val rate: String,
    val paymentRange: String,
    val statusText: String,
    val status: PaymentChannelStatus,
    val fields: List<Pair<String, String>>,
    val actions: List<NativeAction>,
) {
    fun searchableText(): String = fields.joinToString(" ") { "${it.first} ${it.second}" }

    fun groupLabel(dimension: PaymentChannelGroupDimension): String = when (dimension) {
        PaymentChannelGroupDimension.NONE -> "全部支付通道"
        PaymentChannelGroupDimension.STATUS -> status.label
        PaymentChannelGroupDimension.PAYMENT_TYPE -> paymentType.ifBlank { "未标注支付方式" }
        PaymentChannelGroupDimension.PLUGIN -> plugin.ifBlank { "未标注支付插件" }
    }
}

data class PaymentChannelDetail(
    val item: PaymentChannelItem,
    val paymentTypeId: String,
    val mode: String,
    val dailyLimit: String,
    val minimumAmount: String,
    val maximumAmount: String,
    val pluginName: String,
    val pluginDescription: String,
    val pluginAuthor: String,
    val pluginLink: String,
    val pluginPath: String,
)

data class PaymentTestResult(
    val success: Boolean,
    val message: String,
    val paymentUrl: String = "",
    val tradeNo: String = "",
    val qrContent: String = "",
)

fun NativeTable.toPaymentChannelItems(): List<PaymentChannelItem> = rows.mapIndexed { rowIndex, row ->
    val fields = headers.mapIndexedNotNull { index, header ->
        row.getOrNull(index)?.takeIf(String::isNotBlank)?.let { header.ifBlank { "字段 ${index + 1}" } to it }
    }
    fun value(vararg tokens: String): String {
        val index = headers.indexOfFirst { header ->
            val normalized = header.normalizedChannelHeader()
            tokens.any { token -> normalized == token || normalized.contains(token) }
        }
        return row.getOrNull(index).orEmpty()
    }

    val id = value("id", "编号", "通道id")
    val name = value("显示名称", "通道名称", "名称").ifBlank {
        id.takeIf(String::isNotBlank)?.let { "支付通道 #$it" }.orEmpty()
    }
    val statusText = value("状态", "status")
    PaymentChannelItem(
        id = id,
        name = name,
        paymentType = value("支付方式", "支付类型", "typename", "type"),
        plugin = value("支付插件", "插件", "plugin"),
        rate = value("预付扣点", "扣点", "费率", "rate"),
        paymentRange = value("支付范围", "支付限额", "金额范围", "范围"),
        statusText = statusText,
        status = paymentChannelStatus(statusText),
        fields = fields,
        actions = rowActions.getOrNull(rowIndex).orEmpty(),
    )
}

/** 支付方式列表直接取自官网新增/编辑表单中的 type select，不硬编码类型 ID。 */
fun NativeDocument.paymentChannelTypeFilters(): List<NativeOption> {
    val websiteOptions = forms.asSequence()
        .flatMap { it.fields.asSequence() }
        .filter { it.name.equals("type", true) }
        .flatMap { it.options.asSequence() }
        .filter { it.value.isNotBlank() && it.value != "0" }
        .distinctBy { it.value }
        .toList()
    return listOf(NativeOption("", "全部支付方式")) + websiteOptions
}

internal fun paymentChannelPath(path: String, tab: PaymentChannelTab, paymentType: String): String =
    path.withChannelQuery("tab", tab.key).withChannelQuery("type", paymentType.trim().takeIf(String::isNotBlank))

fun sortPaymentChannelItems(
    items: List<PaymentChannelItem>,
    primary: PaymentChannelSortDimension,
    primaryAscending: Boolean,
    secondary: PaymentChannelSortDimension,
    secondaryAscending: Boolean,
): List<PaymentChannelItem> = items.sortedWith(
    Comparator { left, right ->
        comparePaymentChannelDimension(left, right, primary, primaryAscending)
            .takeIf { it != 0 }
            ?: comparePaymentChannelDimension(left, right, secondary, secondaryAscending)
                .takeIf { it != 0 }
            ?: comparePaymentChannelValues(left.id.toDoubleOrNull(), right.id.toDoubleOrNull(), true)
                .takeIf { it != 0 }
            ?: left.name.compareTo(right.name, ignoreCase = true)
    },
)

private fun comparePaymentChannelDimension(
    left: PaymentChannelItem,
    right: PaymentChannelItem,
    dimension: PaymentChannelSortDimension,
    ascending: Boolean,
): Int = when (dimension) {
    PaymentChannelSortDimension.STATUS -> comparePaymentChannelValues(left.status.rank, right.status.rank, ascending)
    PaymentChannelSortDimension.NAME -> comparePaymentChannelValues(left.name, right.name, ascending)
    PaymentChannelSortDimension.ID -> comparePaymentChannelValues(left.id.toDoubleOrNull(), right.id.toDoubleOrNull(), ascending)
    PaymentChannelSortDimension.PAYMENT_TYPE -> comparePaymentChannelValues(left.paymentType, right.paymentType, ascending)
    PaymentChannelSortDimension.PLUGIN -> comparePaymentChannelValues(left.plugin, right.plugin, ascending)
    PaymentChannelSortDimension.RATE -> comparePaymentChannelValues(left.rate.numericChannelValue(), right.rate.numericChannelValue(), ascending)
}

private fun <T : Comparable<T>> comparePaymentChannelValues(left: T?, right: T?, ascending: Boolean): Int {
    if (left == null && right == null) return 0
    if (left == null) return 1
    if (right == null) return -1
    val compared = left.compareTo(right)
    return if (ascending) compared else -compared
}

private fun String.withChannelQuery(name: String, value: String?): String {
    val fragment = substringAfter('#', "").takeIf(String::isNotBlank)
    val withoutFragment = substringBefore('#')
    val base = withoutFragment.substringBefore('?')
    val query = withoutFragment.substringAfter('?', "")
        .split('&')
        .filter(String::isNotBlank)
        .filterNot { it.substringBefore('=') == name }
        .toMutableList()
    if (value != null) query += "$name=$value"
    return buildString {
        append(base)
        if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
        if (fragment != null) append('#').append(fragment)
    }
}

private fun String.normalizedChannelHeader(): String = lowercase().replace(" ", "").replace("_", "")

private fun String.numericChannelValue(): Double? = Regex("-?\\d+(?:\\.\\d+)?").find(this)?.value?.toDoubleOrNull()

private fun paymentChannelStatus(value: String): PaymentChannelStatus {
    val normalized = value.trim().lowercase()
    return when {
        normalized == "0" || listOf("关闭", "停用", "禁用", "未启用", "disabled", "inactive").any(normalized::contains) -> PaymentChannelStatus.DISABLED
        normalized == "1" || listOf("正常", "开启", "启用", "运行", "enabled", "active").any(normalized::contains) -> PaymentChannelStatus.ENABLED
        else -> PaymentChannelStatus.UNKNOWN
    }
}
