package com.futoonpay.admin.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.math.BigDecimal
import java.io.IOException

/** Request identity is retained before transmission; a refresh never discards an unknown result. */
interface BalancePendingStore {
    fun read(uid: String): String
    fun write(uid: String, value: String)
}

internal class MemoryBalancePendingStore : BalancePendingStore {
    private val rows = mutableMapOf<String, String>()
    @Synchronized override fun read(uid: String) = rows[uid].orEmpty()
    @Synchronized override fun write(uid: String, value: String) { rows[uid] = value }
}

enum class ModernBalanceOperation(val action: String, val code: String, val label: String, val scale: Int) {
    ADD("add", "0", "充值", 2), DEDUCT("reduce", "1", "扣除", 2),
    FREEZE("freeze", "2", "冻结", 6), UNFREEZE("unfreeze", "3", "解冻", 6),
    CANCEL_WITHDRAWAL("cancelWithdrawal", "4", "解除提现申请", 6);
    companion object { fun fromAction(action: String) = entries.firstOrNull { it.action == action } }
}

data class BalanceApplication(
    val id: String, val amount: String, val unit: String, val releaseAmount: String,
    val status: String, val chain: String, val createdAt: String, val version: String,
    val canCancel: Boolean, val blockedReason: String,
)

data class MerchantBalanceInfo(
    val uid: String, val total: String, val available: String, val frozen: String,
    val reserved: String, val shortfall: String, val freezable: String, val releasable: String,
    val consistent: Boolean, val applications: List<BalanceApplication>, val applicationsMore: Boolean,
)

data class BalanceCommand(val operation: ModernBalanceOperation, val amount: String, val note: String, val applicationId: String = "")

data class PendingBalanceOperation(val uid: String, val fields: Map<String, String>) {
    val operation get() = ModernBalanceOperation.entries.single { it.code == fields["actdo"] }
    fun command() = BalanceCommand(operation, fields.getValue("rmb"), fields.getValue("note"), fields["application_id"].orEmpty())
}

data class BalanceSession internal constructor(
    val info: MerchantBalanceInfo,
    internal val token: String,
    internal val requestId: String,
    val pending: PendingBalanceOperation?,
)

fun balanceCommandError(info: MerchantBalanceInfo, command: BalanceCommand): String? {
    val note = command.note.trim()
    if (note.codePointCount(0, note.length) !in 3..200) return "请填写 3 至 200 个字的操作原因"
    if (command.operation == ModernBalanceOperation.CANCEL_WITHDRAWAL) {
        val application = info.applications.firstOrNull { it.id == command.applicationId } ?: return "请选择提现申请"
        return if (application.canCancel) null else application.blockedReason.ifBlank { "此申请不可解除" }
    }
    val amount = command.amount.trim()
    val pattern = if (command.operation.scale == 2) Regex("(0|[1-9][0-9]{0,7})(\\.[0-9]{1,2})?") else Regex("(0|[1-9][0-9]{0,9})(\\.[0-9]{1,6})?")
    if (!pattern.matches(amount) || amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } != true)
        return "请输入大于 0 的金额，${command.operation.label}最多 ${command.operation.scale} 位小数"
    val limit = when (command.operation) {
        ModernBalanceOperation.DEDUCT, ModernBalanceOperation.FREEZE -> info.freezable
        ModernBalanceOperation.UNFREEZE -> info.releasable
        else -> null
    }
    if (limit != null && amount.toBigDecimal() > limit.toBigDecimal()) return "金额超过本次安全上限 $limit 元"
    return null
}

/** All entry points share one account/backend/merchant lock, including detached panels. */
private object BalanceLocks {
    private val locks = mutableMapOf<String, Mutex>()
    @Synchronized fun get(key: String) = locks.getOrPut(key) { Mutex() }
}

internal class ModernMerchantBalance(private val api: FutoonApiClient, private val store: BalancePendingStore) {
    private fun validUid(uid: String) { require(Regex("[1-9][0-9]{0,9}").matches(uid)) { "商户号无效" } }
    private fun pending(uid: String): PendingBalanceOperation? {
        val raw = store.read(uid)
        if (raw.isBlank()) return null
        val json = JSONObject(raw)
        val fields = json.keys().asSequence().associateWith { json.getString(it) }
        require(fields["uid"] == uid && Regex("[0-9a-f]{32}").matches(fields["request_id"].orEmpty()) && fields["actdo"] in setOf("0", "1", "2", "3", "4")) { "余额暂存记录异常，请先核对操作日志" }
        require(fields.keys.all { it in setOf("uid", "actdo", "rmb", "note", "request_id", "application_id", "application_version") })
        require(!fields["rmb"].isNullOrBlank() && !fields["note"].isNullOrBlank())
        return PendingBalanceOperation(uid, fields)
    }
    suspend fun open(uid: String): BalanceSession {
        validUid(uid)
        return BalanceLocks.get(api.baseUrl.toString() + uid).withLock {
            val data = api.jsonGet("ajax_user.php?act=balance_info", "ulist.php", mapOf("uid" to uid))
            require(data.optInt("code", -2) == 0) { data.optString("msg", "余额读取失败") }
            val b = data.getJSONObject("balance")
            fun money(key: String): String = b.getString(key).also { require(Regex("-?[0-9]+(?:\\.[0-9]+)?").matches(it)) { "余额字段不完整" } }
            val rows = b.optJSONArray("applications")
            val applications = (0 until (rows?.length() ?: 0)).map { index ->
                val a = rows!!.getJSONObject(index)
                BalanceApplication(a.getString("id"), a.getString("amount"), a.getString("amount_unit"), a.getString("release_amount"), a.optString("status_label"), a.optString("chain_label"), a.optString("created_at"), a.getString("version"), a.optBoolean("can_cancel"), a.optString("blocked_reason"))
            }
            val info = MerchantBalanceInfo(uid, money("money"), money("available"), money("freeze_money"), money("reserved"), money("reservation_shortfall"), money("freezable"), money("releasable"), b.getBoolean("reservation_consistent"), applications, b.optBoolean("applications_more"))
            val token = data.getString("token").also { require(it.isNotBlank()) }
            val requestId = data.getString("request_id").also { require(Regex("[0-9a-f]{32}").matches(it)) }
            BalanceSession(info, token, requestId, pending(uid))
        }
    }
    suspend fun submit(session: BalanceSession, command: BalanceCommand, retry: Boolean): ApiResult =
        BalanceLocks.get(api.baseUrl.toString() + session.info.uid).withLock {
            val uid = session.info.uid
            val previous = pending(uid)
            if (previous != null && !retry) return@withLock ApiResult(false, "上次余额操作结果尚未确认，请查询原操作结果", uncertain = true)
            if (previous == null && retry) return@withLock ApiResult(false, "原操作已确认或不存在，请重新读取余额")
            val fields = previous?.fields ?: run {
                balanceCommandError(session.info, command)?.let { return@withLock ApiResult(false, it) }
                val base = linkedMapOf("uid" to uid, "actdo" to command.operation.code, "rmb" to command.amount.trim(), "note" to command.note.trim(), "request_id" to session.requestId)
                if (command.operation == ModernBalanceOperation.CANCEL_WITHDRAWAL) {
                    val application = session.info.applications.single { it.id == command.applicationId }
                    require(Regex("[0-9a-f]{64}").matches(application.version)) { "申请版本无效，请刷新" }
                    base["rmb"] = application.releaseAmount
                    base["application_id"] = application.id
                    base["application_version"] = application.version
                }
                // A failed durable write aborts before any network mutation.
                store.write(uid, JSONObject(base as Map<*, *>).toString())
                base
            }
            val response = try {
                api.jsonPost("ajax_user.php?act=" + if (fields["actdo"] == "4") "withdrawal_cancel" else "recharge", "ulist.php", fields + ("token" to session.token), once = true)
            } catch (e: Exception) {
                return@withLock ApiResult(false, "连接中断，原操作结果尚未确认。请查询结果 / 安全重试，勿新建重复操作。", uncertain = true)
            }
            val code = response.optInt("code", -2)
            if (code == 0 || code == -1) {
                try { store.write(uid, "") } catch (_: Exception) {
                    return@withLock ApiResult(false, "后台已返回结果，但本机未能清理操作记录；请查询原操作结果", uncertain = true)
                }
            }
            ApiResult(code == 0, response.optString("msg").ifBlank { if (code == 0) "操作成功" else if (code == -1) "操作未执行，请核对输入" else "未确认操作结果，请查询原操作结果" }, uncertain = code !in setOf(0, -1))
        }
}
