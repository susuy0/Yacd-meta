package com.futoonpay.admin.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

enum class BatchKind { MERCHANT_STATUS, MERCHANT_KEY, ORDER_STATUS, ORDER_ACTION, SETTLEMENT_STATUS, SETTLEMENT_ACTION, PAYMENT_ACTION }
enum class BatchOutcomeStatus(val label: String) { SUCCESS("成功"), FAILED("失败"), UNCERTAIN("结果待核对"), NOT_RUN("未执行"), SKIPPED("已跳过") }

data class BatchCommand(
    val id: String,
    val label: String,
    val kind: BatchKind,
    val fields: Map<String, String> = emptyMap(),
    val nativeAction: NativeAction? = null,
    val skipReason: String = "",
) {
    // Keys must never appear in the default string representation (including crash reports).
    override fun toString() = "BatchCommand(id=$id, kind=$kind)"
}
data class BatchOutcome(val command: BatchCommand, val status: BatchOutcomeStatus, val message: String, val newKey: String = "") {
    override fun toString() = "BatchOutcome(id=${command.id}, status=$status)"
}
data class BatchRequest(val module: String, val title: String, val commands: List<BatchCommand>, val warning: String = "将逐条提交所选记录，请核对后执行。")
data class BatchProgress(val request: BatchRequest, val started: Boolean = false, val running: Boolean = false, val results: List<BatchOutcome> = emptyList()) {
    val retryable: List<BatchCommand> get() = results.filter { it.status in setOf(BatchOutcomeStatus.FAILED, BatchOutcomeStatus.NOT_RUN) }.map { it.command }
}

/** One in-flight batch per repository, sequential requests, no implicit mutation retries. */
class BatchRunner {
    private val mutex = Mutex()
    suspend fun run(commands: List<BatchCommand>, execute: suspend (BatchCommand) -> BatchOutcome, progress: (List<BatchOutcome>) -> Unit = {}): List<BatchOutcome> {
        val targets = commands.distinctBy { it.id }.toList()
        if (!mutex.tryLock()) return targets.map { BatchOutcome(it, BatchOutcomeStatus.NOT_RUN, "已有批量操作正在执行") }.also(progress)
        try {
            val results = mutableListOf<BatchOutcome>()
            var stopReason: String? = null
            targets.forEach { command ->
                val outcome = when {
                    stopReason != null -> BatchOutcome(command, BatchOutcomeStatus.NOT_RUN, stopReason!!)
                    command.skipReason.isNotBlank() -> BatchOutcome(command, BatchOutcomeStatus.SKIPPED, command.skipReason)
                    else -> try {
                        execute(command)
                    } catch (error: CancellationException) { throw error
                    } catch (_: SessionExpiredException) {
                        stopReason = "登录已过期，请重新登录后核对"
                        BatchOutcome(command, BatchOutcomeStatus.NOT_RUN, stopReason!!)
                    } catch (_: PermissionDeniedException) {
                        stopReason = "权限不足，已停止后续操作"
                        BatchOutcome(command, BatchOutcomeStatus.FAILED, stopReason!!)
                    } catch (_: Exception) {
                        // A lost response does not mean the write failed. Never auto retry.
                        stopReason = "前一条结果未确认，请核对后台后再继续"
                        BatchOutcome(command, BatchOutcomeStatus.UNCERTAIN, "请求结果未确认，请先核对后台；不要直接重复提交")
                    }
                }
                results += outcome
                if (outcome.status == BatchOutcomeStatus.UNCERTAIN && stopReason == null) stopReason = "前一条结果未确认，请核对后台后再继续"
                progress(results.toList())
            }
            return results.toList()
        } finally { mutex.unlock() }
    }
}

fun merchantBatchOptions(merchants: List<MerchantSummary>): List<BatchRequest> = buildList {
    listOf("status" to "商户", "pay" to "支付", "settle" to "结算").forEach { (type, label) ->
        listOf(true, false).forEach { enabled ->
            add(BatchRequest("merchants", "${if (enabled) "开启" else "关闭"}$label", merchants.map {
                BatchCommand(it.uid, "${it.username.ifBlank { "商户" }} · UID ${it.uid}", BatchKind.MERCHANT_STATUS, mapOf("type" to type, "status" to if (enabled) "1" else "0"))
            }))
        }
    }
    add(merchantKeyRequest(merchants))
}
fun merchantKeyRequest(merchants: List<MerchantSummary>) = BatchRequest("merchants", "重置密钥", merchants.map {
    BatchCommand(it.uid, "${it.username.ifBlank { "商户" }} · UID ${it.uid}", BatchKind.MERCHANT_KEY)
}, "重置后旧密钥立即失效，请更新商户接入配置。新密钥仅在本次结果中显示，请及时复制保存。")

fun orderBatchOptions(orders: List<OrderSummary>): List<BatchRequest> = buildList {
    listOf(0 to "设为待支付", 1 to "设为已支付").forEach { (status, label) ->
        add(BatchRequest("orders", label, orders.map { BatchCommand(it.tradeNo, it.tradeNo, BatchKind.ORDER_STATUS, mapOf("status" to "$status")) }, "将直接修改订单状态，请核对支付结果后执行。"))
    }
    listOf("queryChannelOrder" to "查询上游订单", "notify" to "补发通知", "freeze" to "冻结订单", "unfreeze" to "解冻订单").forEach { (action, label) ->
        add(BatchRequest("orders", label, orders.map {
            val eligible = when (action) { "queryChannelOrder" -> it.status == 0; "freeze" -> it.status == 1; "unfreeze" -> it.status == 3; else -> it.status in 1..3 }
            BatchCommand(it.tradeNo, it.tradeNo, BatchKind.ORDER_ACTION, mapOf("action" to action), skipReason = if (eligible) "" else "当前订单状态不支持$label")
        }))
    }
}
fun settlementBatchOptions(rows: List<SettlementSummary>, modernBackend: Boolean = false): List<BatchRequest> = buildList {
    if (modernBackend) {
        listOf(1 to "标记已完成", 6 to "作废结算").forEach { (status, label) ->
            add(BatchRequest("settlements", label, rows.map { row -> BatchCommand(row.id, "结算 #${row.id} · 商户 ${row.merchantId}", BatchKind.SETTLEMENT_STATUS,
                mapOf("status" to status.toString()), skipReason = when {
                    row.status == 2 -> "正在结算，禁止手动修改"
                    row.status == 1 && status == 6 -> "已完成的结算不能作废"
                    else -> ""
                }) }, settlementStatusConfirmation(status)))
        }
        return@buildList
    }
    listOf(0 to "设为待结算", 1 to "设为已完成", 2 to "设为正在结算").forEach { (status, label) ->
        add(BatchRequest("settlements", label, rows.map { BatchCommand(it.id, "结算 #${it.id} · 商户 ${it.merchantId}", BatchKind.SETTLEMENT_STATUS,
            mapOf("status" to "$status", "uid" to it.merchantId, "usdt_rate" to it.usdtRate, "usdt" to it.usdtAmount)) }))
    }
    add(BatchRequest("settlements", "查询结算结果", rows.map { BatchCommand(it.id, "结算 #${it.id}", BatchKind.SETTLEMENT_ACTION, mapOf("action" to "settle_result")) }))
    add(BatchRequest("settlements", "检查完成", rows.map { BatchCommand(it.id, "结算 #${it.id}", BatchKind.SETTLEMENT_ACTION,
        mapOf("action" to "settle_setresult", "result" to "checked"), skipReason = if (it.status == 3) "" else "仅结算失败记录可填写 checked") }))
}

data class PaymentBatchRow(val id: String, val label: String, val actions: List<NativeAction>)
/** Only expose actual row actions; never infer PHP act names from UI labels. */
fun paymentBatchOptions(module: String, rows: List<PaymentBatchRow>): List<BatchRequest> {
    val groups = listOf("启用" to setOf("启用", "开启"), "停用" to setOf("停用", "禁用", "关闭"), "删除" to setOf("删除"),
        "检测" to setOf("检测", "手动检测", "立即检测"), "恢复" to setOf("恢复", "恢复通道"), "重置监测结果" to setOf("重置", "重置结果", "重置监测结果"))
    return groups.mapNotNull { (label, aliases) ->
        if (rows.none { row -> row.actions.any { it.label in aliases && !it.endpoint.startsWith("futoon://") } }) return@mapNotNull null
        BatchRequest(module, label, rows.map { row ->
            val action = row.actions.firstOrNull { it.label in aliases && !it.endpoint.startsWith("futoon://") }
            BatchCommand(row.id, row.label, BatchKind.PAYMENT_ACTION, mapOf("module" to module), action, if (action == null) "当前记录没有$label 操作" else "")
        }, if (label == "删除") "删除所选配置后无法恢复，请核对每条记录及关联业务。" else "将调用每条记录的官网操作。")
    }
}
