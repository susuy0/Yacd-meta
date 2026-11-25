package com.futoonpay.admin.data

data class RollRouteVisualItem(
    val index: Int,
    val channelId: String,
    val channelLabel: String,
    val weight: Int,
    val fraction: Float,
)

/** Converts the official list[n][channel]/list[n][weight] route form into display rows. */
fun NativeForm.toRollRouteVisualItems(): List<RollRouteVisualItem> {
    val channelPattern = Regex("list\\[(\\d+)]\\[channel]")
    val rows = fields.mapNotNull { field ->
        val index = channelPattern.matchEntire(field.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return@mapNotNull null
        if (field.value.isBlank() || field.value == "0") return@mapNotNull null
        val weight = fields.firstOrNull { it.name == "list[$index][weight]" }
            ?.value?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val label = field.options.firstOrNull { it.value == field.value }?.label
            .orEmpty().ifBlank { "支付通道 #${field.value}" }
        Triple(index, field.value, label) to weight
    }.sortedBy { it.first.first }
    val totalWeight = rows.sumOf { it.second.toLong() }.coerceAtLeast(1)
    return rows.map { (channel, weight) ->
        RollRouteVisualItem(
            index = channel.first,
            channelId = channel.second,
            channelLabel = channel.third,
            weight = weight,
            fraction = weight.toFloat() / totalWeight.toFloat(),
        )
    }
}
