package com.futoonpay.admin.data

enum class RollGroupStatus(val label: String, internal val rank: Int) {
    DISABLED("已停用", 0),
    UNKNOWN("待确认", 1),
    ENABLED("运行中", 2),
}

/**
 * 官网 pay_roll.php 自带的四个列表标签。
 *
 * UI 使用运营侧熟悉的短标签，但每次切换都会把对应的官网 tab 参数交给服务端，
 * 不再依据当前页面的“状态”文字在本地猜测归类。
 */
enum class RollGroupTab(
    val key: String,
    val label: String,
    val websiteLabel: String,
) {
    RUNNING("assigned", "运行中", "已启用（已分配）"),
    DISABLED("disabled", "已停用", "未启用"),
    PENDING("unassigned", "待确认", "已启用（未分配）"),
    ALL("all", "全部", "全部"),
}

internal fun rollGroupTabPath(path: String, tab: RollGroupTab): String {
    val expression = Regex("([?&])tab=[^&#]*")
    if (expression.containsMatchIn(path)) {
        return path.replace(expression) { match -> "${match.groupValues[1]}tab=${tab.key}" }
    }
    val fragmentIndex = path.indexOf('#').takeIf { it >= 0 } ?: path.length
    val base = path.substring(0, fragmentIndex)
    val fragment = path.substring(fragmentIndex)
    val separator = if (base.contains('?')) '&' else '?'
    return "$base$separator" + "tab=${tab.key}$fragment"
}

enum class RollSortDimension(val label: String) {
    STATUS("运行状态"),
    NAME("轮询组名称"),
    ID("轮询组 ID"),
    MODE("轮询模式"),
    PAYMENT_TYPE("支付方式"),
}

enum class RollGroupDimension(val label: String) {
    NONE("不分组"),
    STATUS("运行状态"),
    MODE("轮询模式"),
    PAYMENT_TYPE("支付方式"),
}

data class RollGroupItem(
    val id: String,
    val name: String,
    val paymentType: String,
    val mode: String,
    val statusText: String,
    val status: RollGroupStatus,
    val routeSummary: String,
    val fields: List<Pair<String, String>>,
    val actions: List<NativeAction>,
) {
    fun searchableText(): String = fields.joinToString(" ") { "${it.first} ${it.second}" }

    fun groupLabel(dimension: RollGroupDimension): String = when (dimension) {
        RollGroupDimension.NONE -> "全部轮询组"
        RollGroupDimension.STATUS -> status.label
        RollGroupDimension.MODE -> mode.ifBlank { "未标注模式" }
        RollGroupDimension.PAYMENT_TYPE -> paymentType.ifBlank { "未标注支付方式" }
    }
}

fun NativeTable.toRollGroupItems(): List<RollGroupItem> = rows.mapIndexed { rowIndex, row ->
    val fields = headers.mapIndexedNotNull { index, header ->
        row.getOrNull(index)?.takeIf(String::isNotBlank)?.let { header.ifBlank { "字段 ${index + 1}" } to it }
    }
    fun value(vararg tokens: String): String {
        val index = headers.indexOfFirst { header ->
            val normalized = header.normalizedRollHeader()
            tokens.any { token -> normalized == token || normalized.contains(token) }
        }
        return row.getOrNull(index).orEmpty()
    }

    val id = value("id", "编号", "轮询组id", "轮训组id")
    val name = value("轮询组名称", "轮训组名称", "规则名称", "名称").ifBlank {
        id.takeIf(String::isNotBlank)?.let { "轮询组 #$it" }.orEmpty()
    }
    val paymentType = value("支付方式", "支付类型", "typename", "type")
    val mode = value("轮询模式", "轮训模式", "规则模式", "模式", "kind")
    val statusText = value("状态", "status")
    val routeSummary = fields.firstOrNull { (label, _) ->
        val normalized = label.normalizedRollHeader()
        normalized.contains("路由") || (normalized.contains("通道") && !normalized.contains("支付方式") && !normalized.contains("支付类型"))
    }?.second.orEmpty()
    RollGroupItem(
        id = id,
        name = name,
        paymentType = paymentType,
        mode = mode,
        statusText = statusText,
        status = rollGroupStatus(statusText),
        routeSummary = routeSummary,
        fields = fields,
        actions = rowActions.getOrNull(rowIndex).orEmpty(),
    )
}

fun sortRollGroupItems(
    items: List<RollGroupItem>,
    primary: RollSortDimension,
    primaryAscending: Boolean,
    secondary: RollSortDimension,
    secondaryAscending: Boolean,
): List<RollGroupItem> = items.sortedWith(
    Comparator { left, right ->
        compareRollDimension(left, right, primary, primaryAscending)
            .takeIf { it != 0 }
            ?: compareRollDimension(left, right, secondary, secondaryAscending)
                .takeIf { it != 0 }
            ?: compareRollValues(left.id.toDoubleOrNull(), right.id.toDoubleOrNull(), true)
                .takeIf { it != 0 }
            ?: left.name.compareTo(right.name, ignoreCase = true)
    },
)

private fun compareRollDimension(
    left: RollGroupItem,
    right: RollGroupItem,
    dimension: RollSortDimension,
    ascending: Boolean,
): Int = when (dimension) {
    RollSortDimension.STATUS -> compareRollValues(left.status.rank, right.status.rank, ascending)
    RollSortDimension.NAME -> compareRollValues(left.name, right.name, ascending)
    RollSortDimension.ID -> compareRollValues(left.id.toDoubleOrNull(), right.id.toDoubleOrNull(), ascending)
    RollSortDimension.MODE -> compareRollValues(left.mode, right.mode, ascending)
    RollSortDimension.PAYMENT_TYPE -> compareRollValues(left.paymentType, right.paymentType, ascending)
}

private fun <T : Comparable<T>> compareRollValues(left: T?, right: T?, ascending: Boolean): Int {
    if (left == null && right == null) return 0
    if (left == null) return 1
    if (right == null) return -1
    val compared = left.compareTo(right)
    return if (ascending) compared else -compared
}

private fun String.normalizedRollHeader(): String = lowercase().replace(" ", "").replace("_", "")

private fun rollGroupStatus(value: String): RollGroupStatus {
    val normalized = value.trim().lowercase()
    return when {
        normalized == "0" || listOf("关闭", "停用", "禁用", "未启用", "disabled", "inactive").any(normalized::contains) -> RollGroupStatus.DISABLED
        normalized == "1" || listOf("正常", "开启", "启用", "运行", "enabled", "active").any(normalized::contains) -> RollGroupStatus.ENABLED
        else -> RollGroupStatus.UNKNOWN
    }
}
