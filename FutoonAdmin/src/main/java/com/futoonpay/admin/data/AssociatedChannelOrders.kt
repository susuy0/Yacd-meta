package com.futoonpay.admin.data

/** Complete pagination over a set of individually supported backend filters, grouped by channel.
 * Never sends an empty channel filter and never invents a comma-separated backend API contract.
 */
internal suspend fun FutoonRepository.associatedOrders(page: Int, filters: Map<String, String>, ids: Set<String>): PagedResult<OrderSummary> {
    require(page in 1..100000 && ids.isNotEmpty()) { "请先关联通道" }
    val requested = filters["channel"].orEmpty()
    require(requested.isBlank() || requested in ids) { "筛选通道不在当前会话关联范围" }
    val targets = if (requested.isBlank()) ids.toList() else listOf(requested)
    val size = FutoonRepository.PAGE_SIZE
    suspend fun load(id: String, p: Int): PagedResult<OrderSummary> {
        val result = orders(p, filters + ("channel" to id))
        check(result.total >= 0 && result.rows.all { it.channelId == id }) { "后台返回了其他通道的订单，请核对筛选接口" }
        return result
    }
    val first = targets.associateWith { load(it, 1) }
    val total = first.values.sumOf { it.total.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    var offset = (page.toLong() - 1) * size
    val rows = mutableListOf<OrderSummary>()
    for (id in targets) {
        val count = first.getValue(id).total
        if (offset >= count) { offset -= count; continue }
        var local = offset.toInt()
        while (rows.size < size && local < count) {
            val p = local / size + 1
            val result = if (p == 1) first.getValue(id) else load(id, p)
            val chunk = result.rows.drop(local % size).take(minOf(size - rows.size, count - local))
            check(chunk.isNotEmpty()) { "通道订单在分页期间发生变化，请刷新列表" }
            rows += chunk
            local += chunk.size
        }
        offset = 0
        if (rows.size == size) break
    }
    return PagedResult(total, rows)
}
