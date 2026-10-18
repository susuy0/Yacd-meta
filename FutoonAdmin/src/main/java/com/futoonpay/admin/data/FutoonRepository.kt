package com.futoonpay.admin.data

import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class FutoonRepository(
    private val api: FutoonApiClient,
) {
    val backendAddress: String get() = api.baseUrl.toString()
    private val batchRunner = BatchRunner()

    suspend fun runBatch(commands: List<BatchCommand>, onProgress: (List<BatchOutcome>) -> Unit = {}): List<BatchOutcome> =
        batchRunner.run(commands, ::executeBatchCommand, onProgress)

    private suspend fun executeBatchCommand(command: BatchCommand): BatchOutcome {
        if (command.id.isBlank()) return BatchOutcome(command, BatchOutcomeStatus.FAILED, "记录缺少有效 ID")
        val fields = command.fields
        if (command.kind == BatchKind.MERCHANT_KEY) {
            if ((command.id.toLongOrNull() ?: 0) <= 0) return BatchOutcome(command, BatchOutcomeStatus.FAILED, "无效商户 UID")
            val json = api.jsonGet("ajax_user.php", "ulist.php", mapOf("act" to "resetUser", "uid" to command.id))
            if (json.optInt("code", -1) !in setOf(0, 200)) return BatchOutcome(command, BatchOutcomeStatus.FAILED, "密钥重置被后台拒绝，请核对权限或商户记录")
            val key = json.optString("key").trim()
            if (!Regex("[a-zA-Z0-9]{32}").matches(key)) return BatchOutcome(command, BatchOutcomeStatus.UNCERTAIN, "后台已返回成功，但未提供有效新密钥，请到后台核对")
            return BatchOutcome(command, BatchOutcomeStatus.SUCCESS, "密钥已重置，请更新商户接入配置", key)
        }
        val result = when (command.kind) {
            BatchKind.MERCHANT_STATUS -> {
                if (fields["type"] !in setOf("status", "pay", "settle") || fields["status"] !in setOf("0", "1")) return BatchOutcome(command, BatchOutcomeStatus.FAILED, "不支持的商户状态操作")
                setMerchantStatus(command.id, fields.getValue("type"), fields["status"] == "1")
            }
            BatchKind.ORDER_STATUS -> {
                if (fields["status"] !in setOf("0", "1")) return BatchOutcome(command, BatchOutcomeStatus.FAILED, "不支持的订单状态")
                setOrderStatus(command.id, fields.getValue("status").toInt())
            }
            BatchKind.ORDER_ACTION -> {
                if (fields["action"] !in setOf("queryChannelOrder", "notify", "freeze", "unfreeze")) return BatchOutcome(command, BatchOutcomeStatus.FAILED, "不支持的订单操作")
                orderAction(fields.getValue("action"), command.id)
            }
            BatchKind.SETTLEMENT_STATUS -> {
                if (fields["status"] !in setOf("0", "1", "2")) return BatchOutcome(command, BatchOutcomeStatus.FAILED, "不支持的结算状态")
                setSettlementStatus(command.id, fields.getValue("status").toInt(), fields["uid"].orEmpty(), fields["usdt_rate"].orEmpty(), fields["usdt"].orEmpty())
            }
            BatchKind.SETTLEMENT_ACTION -> {
                if (fields["action"] !in setOf("settle_result", "settle_setresult")) return BatchOutcome(command, BatchOutcomeStatus.FAILED, "不支持的结算操作")
                settlementAction(fields.getValue("action"), command.id, fields.filterKeys { it != "action" })
            }
            BatchKind.PAYMENT_ACTION -> runPaymentAction(fields.getValue("module"), requireNotNull(command.nativeAction))
            BatchKind.MERCHANT_KEY -> error("Handled above")
        }
        return BatchOutcome(command, if (result.success) BatchOutcomeStatus.SUCCESS else if (result.partial) BatchOutcomeStatus.UNCERTAIN else BatchOutcomeStatus.FAILED, result.message)
    }
    private val discoveredPaymentPaths = mutableMapOf<String, String>()
    suspend fun captcha() = api.loadCaptcha()
    suspend fun login(username: String, password: String, captcha: String) = api.login(username, password, captcha)
    suspend fun sessionIsValid() = api.sessionIsValid()
    suspend fun logout() = api.logout()

    suspend fun merchantProfile(uid: String): NativeForm =
        merchantProfileForm(api.document("uset.php?my=edit&uid=$uid"), uid)

    suspend fun saveMerchantProfile(uid: String, original: NativeForm, values: Map<String, String>): ApiResult {
        val html = api.document("uset.php?my=edit&uid=$uid")
        val merged = merchantProfileSubmission(html, uid, original, values)
        return api.submitForm("ajax_user.php?act=editUser&uid=$uid", "POST", "uset.php?my=edit&uid=$uid", merged)
    }

    suspend fun configurationChannels(): List<ConfigurationChannel> {
        val path = paymentPath("channels")
        val html = api.document(path)
        val endpoint = NativeDocumentParser.listEndpoints(html).firstOrNull { it.contains("act=channelList") }
        if (endpoint == null) return configurationChannelsFromHtml(path, html)
        val channels = mutableListOf<ConfigurationChannel>()
        var page = 1
        val seen = mutableSetOf<String>()
        while (true) {
            val json = api.page(endpoint, path, page, 100, emptyMap())
            check(!json.has("code") || json.optInt("code") in setOf(0, 200)) { json.optString("msg", "关联通道读取失败") }
            val rows = json.rows().objectRows()
            rows.forEach { row ->
                check(row.text("id").isNotBlank() && seen.add(row.text("id"))) { "通道分页返回重复或缺少 ID，请刷新重试" }
                channels += configurationChannel(row)
            }
            if (json.has("total")) {
                if (channels.size >= json.optInt("total")) break
                check(rows.isNotEmpty()) { "通道分页结果不完整，请刷新重试" }
            } else if (rows.size < 100) break
            check(page++ < 40) { "通道数量超过读取上限，请缩小后台范围" }
        }
        return channels
    }

    private fun configurationChannel(row: JSONObject): ConfigurationChannel {
        val proxyKey = listOf("proxyid", "proxy_id", "proxy").firstOrNull { row.has(it) && !row.isNull(it) }
        return ConfigurationChannel(row.text("id"), row.text("name"), row.text("type"), row.text("rate"), row.text("paymin"), row.text("paymax"), proxyKey?.let { row.text(it) })
    }

    /** Legacy installations render the catalogue in PHP; only follow their own pagination links. */
    private suspend fun configurationChannelsFromHtml(path: String, firstHtml: String): List<ConfigurationChannel> = coroutineScope {
        val firstUrl = api.baseUrl.resolve(path) ?: error("无效通道列表地址")
        val queue = java.util.ArrayDeque<String>().apply { add(firstUrl.toString()) }
        val visited = mutableSetOf<String>()
        val ids = linkedSetOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            check(visited.size <= 40) { "通道分页超过读取上限" }
            val dom = org.jsoup.Jsoup.parse(if (current == firstUrl.toString()) firstHtml else api.document(current))
            dom.select("table").forEach { table ->
                val headers = table.select("thead th").toList().ifEmpty { table.select("tr").firstOrNull()?.select("th")?.toList().orEmpty() }
                val idIndex = headers.indexOfFirst { it.text().trim().equals("ID", true) || it.text().trim() in setOf("编号", "通道ID", "通道 ID") }
                if (idIndex >= 0) table.select("tr").filter { it.select("td").isNotEmpty() }.forEach { row ->
                    val id = row.select("td").getOrNull(idIndex)?.text()?.trim().orEmpty()
                    if (row.select("td[colspan]").isNotEmpty() && listOf("暂无", "没有", "无数据").any { row.text().contains(it) }) return@forEach
                    check(id.matches(Regex("[1-9][0-9]*"))) { "后台通道列表未提供有效 ID" }
                    ids.add(id)
                }
            }
            dom.select(".pagination a[href]").forEach { link ->
                val raw = link.attr("href").substringBefore('#')
                val target = firstUrl.resolve(raw) ?: return@forEach
                if (target.host == firstUrl.host && target.scheme == firstUrl.scheme && target.port == firstUrl.port && target.encodedPath == firstUrl.encodedPath && target.toString() !in visited) queue.add(target.toString())
            }
        }
        val semaphore = Semaphore(4)
        ids.map { id -> async {
            semaphore.withPermit {
                val json = api.jsonGet("ajax_pay.php?act=getChannel&id=$id", path)
                check(!json.has("code") || json.optInt("code") in setOf(0, 200)) { json.optString("msg", "通道详情读取失败") }
                val row = json.optJSONObject("data") ?: error("后台未提供通道 #$id 的详情")
                check(row.text("id") == id) { "通道详情 ID 不匹配" }
                configurationChannel(row)
            }
        } }.awaitAll()
    }

    suspend fun dashboard(forceRefresh: Boolean = false): DashboardStats = coroutineScope {
        val todayDate = LocalDate.now().toString()
        suspend fun count(endpoint: String, referer: String, fields: Map<String, String>): Int? = runCatching {
            api.page(endpoint, referer, 1, 1, fields).optInt("total", -1).takeIf { it >= 0 }
        }.getOrNull()
        val todayCount = async { count("ajax_order.php?act=orderList", "order.php", OrderSearchFilters(startDate = todayDate, endDate = todayDate).toRequest()) }
        val successful = async { count("ajax_order.php?act=orderList", "order.php", OrderSearchFilters(startDate = todayDate, endDate = todayDate, status = 1).toRequest()) }
        val pendingOrders = async { count("ajax_order.php?act=orderList", "order.php", OrderSearchFilters(status = 0).toRequest()) }
        val pendingSettlements = async { count("ajax_settle.php?act=settleList", "slist.php", SettlementSearchFilters(status = 0).toRequest()) }
        val pendingDomains = async { count("ajax_user.php?act=domainList", "domain.php", mapOf("dstatus" to "0")) }
        val json = api.dashboard(forceRefresh)
        val typeNames = json.optJSONObject("paytype")
        val today = json.optJSONObject("order_today")
        val todayByType = today?.optJSONObject("paytype")
        val pairs = mutableListOf<Pair<String, String>>()
        typeNames?.keys()?.forEach { key ->
            pairs += typeNames.text(key) to (todayByType?.text(key).orEmpty().ifBlank { "0" })
        }
        val daily = mutableListOf<Pair<String, String>>()
        json.optJSONObject("order")?.let { history ->
            history.keys().forEach { date -> daily += date to history.optJSONObject(date)?.text("all").orEmpty() }
        }
        DashboardStats(
            orderCount = json.text("count1"),
            merchantCount = json.text("count2"),
            merchantBalance = json.text("usermoney"),
            settledTotal = json.text("settlemoney"),
            yesterdaySettled = json.text("yesterdaySettle"),
            todayTotal = today?.text("all").orEmpty().ifBlank { "0" },
            todayByType = pairs,
            recentDailyTotals = daily.sortedByDescending { it.first },
            todayOrderCount = todayCount.await(), todaySuccessfulCount = successful.await(),
            pendingOrderCount = pendingOrders.await(), pendingSettlementCount = pendingSettlements.await(),
            pendingDomainCount = pendingDomains.await(),
            todayByCurrency = today?.currencyAmounts("currencies").orEmpty().ifEmpty { mapOf("CNY" to today?.text("all").orEmpty().ifBlank { "0" }) },
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun orders(page: Int = 1, filters: Map<String, String> = emptyMap()): PagedResult<OrderSummary> {
        val json = api.page("ajax_order.php?act=orderList", "order.php", page, PAGE_SIZE, filters)
        return json.mapRows { row ->
            OrderSummary(
                tradeNo = row.text("trade_no"), merchantTradeNo = row.text("out_trade_no"),
                merchantId = row.text("uid"), domain = row.text("domain"), name = row.text("name"),
                amount = row.text("money"), paidAmount = row.text("realmoney"),
                merchantIncome = row.text("getmoney"), paymentType = row.text("typeshowname"),
                channelId = row.text("channel"), plugin = row.text("plugin"),
                createdAt = row.text("addtime"), completedAt = row.text("endtime"),
                status = row.intValue("status"), settlementStatus = row.intValue("settle"),
                currency = row.text("currency").ifBlank { row.text("currency_code") }.ifBlank { "CNY" },
                merchantName = row.text("username"),
            )
        }
    }

    suspend fun orderDetail(tradeNo: String): OrderDetail = api.jsonGet(
        endpoint = "ajax_order.php?act=order",
        referer = "order.php",
        query = mapOf("trade_no" to tradeNo),
    ).also { json ->
        val code = json.optInt("code", 0)
        if (code !in setOf(0, 200)) throw IllegalStateException(json.optString("msg", "订单详情读取失败"))
    }.toOrderDetail()

    suspend fun orderChannelStats(orders: List<OrderSummary>): OrderChannelStatsResult {
        val channelIds = orders.map { order ->
            Regex("\\d+").find(order.channelId)?.value?.toLongOrNull()?.toString().orEmpty()
        }.filter(String::isNotBlank).distinct()
        if (channelIds.isEmpty()) return OrderChannelStatsResult(emptyMap(), false)
        val pageStats = currentPageChannelStats(orders)
        val official = mutableMapOf<String, OfficialChannelRate>()
        val failures = mutableListOf<String>()
        runCatching { requestChannelMonitor("") }
            .onSuccess { payload -> official.putAll(parseOfficialChannelRates(payload)) }
            .onFailure { error -> failures += error.message.orEmpty().ifBlank { "全局监测请求失败" } }
        val missing = channelIds.filter { id -> official[id]?.fiveMinutes == null }
        missing.take(20).forEach { channelId ->
            runCatching { requestChannelMonitor(channelId) }
                .onSuccess { payload -> official.putAll(parseOfficialChannelRates(payload, channelId)) }
                .onFailure { error -> failures += "通道 #$channelId：${error.message.orEmpty().ifBlank { "请求失败" }}" }
        }
        val fallbackReason = when {
            failures.isNotEmpty() -> "官网通道监测读取失败，已降级为当前页最近5分钟样本：${failures.first()}"
            official.isEmpty() -> "官网通道监测返回0条记录，已降级为当前页最近5分钟样本"
            else -> "官网未返回部分通道，缺失通道已降级为当前页最近5分钟样本"
        }
        return mergeOrderChannelStats(channelIds, pageStats, official, fallbackReason)
    }

    private suspend fun requestChannelMonitor(channelId: String): JSONObject {
        val json = api.jsonPost(
            endpoint = "ajax_pay.php?act=channelMonitor",
            referer = "pay_channel_monitor.php",
            fields = mapOf("channel_id" to channelId, "type" to "0"),
        )
        val code = json.optInt("code", 0)
        if (code !in setOf(0, 200)) throw IllegalStateException(json.optString("msg", "通道监测接口返回 $code"))
        return json
    }

    suspend fun channelStatistics(channelIds: Collection<String>): Map<String, OrderChannelStat> {
        val payload = requestChannelMonitor("")
        return mergeOrderChannelStats(channelIds, emptyMap(), parseOfficialChannelRates(payload)).stats
    }

    suspend fun settlements(page: Int = 1, filters: Map<String, String> = emptyMap()): PagedResult<SettlementSummary> {
        val json = api.page("ajax_settle.php?act=settleList", "slist.php", page, PAGE_SIZE, filters)
        return json.mapRows { row ->
            SettlementSummary(
                id = row.text("id"), merchantId = row.text("uid"), username = row.text("username"),
                payType = row.text("type"), chain = row.text("chain"), account = row.text("account"),
                amount = row.text("money"), actualAmount = row.text("realmoney"),
                usdtAmount = row.text("usdt_count"), usdtRate = row.text("usdt_rate_count"),
                createdAt = row.text("addtime"), completedAt = row.text("endtime"),
                status = row.intValue("status"), automatic = row.boolFlag("auto"),
                fullSettlement = row.boolFlag("is_full"), result = row.text("result"),
                currency = row.text("currency").ifBlank { "CNY" }, fee = row.text("fee"),
            )
        }
    }

    suspend fun merchants(page: Int = 1, filters: Map<String, String> = emptyMap()): PagedResult<MerchantSummary> {
        val json = api.page("ajax_user.php?act=userList", "ulist.php", page, PAGE_SIZE, filters)
        return json.mapRows { row ->
            MerchantSummary(
                uid = row.text("uid"), username = row.text("username"), groupName = row.text("groupname"),
                balance = row.text("money"), frozenBalance = row.text("freeze_money"),
                telegramId = row.text("telegram_id"), website = row.text("url"),
                createdAt = row.text("addtime"), status = row.intValue("status"),
                paymentStatus = row.intValue("pay"), settlementStatus = row.intValue("settle"),
                account = row.text("account"), remark = row.text("username"),
                inviteCode = row.text("invite_code"), recommenderId = row.text("recommender_id"),
                recommenderPercent = row.text("recommender_percent"),
                balances = row.currencyAmounts("balances").ifEmpty {
                    buildMap { put("CNY", row.text("money")); row.text("usdt_money").takeIf(String::isNotBlank)?.let { put("USDT", it) } }
                },
            )
        }
    }

    suspend fun merchantFlows(
        merchantIds: Collection<String>,
        endDate: LocalDate = LocalDate.now(),
        days: Int = 7,
    ): Map<String, MerchantFlowSummary> = coroutineScope {
        val dates = (days.coerceIn(1, 30) - 1 downTo 0).map { offset ->
            endDate.minusDays(offset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
        val limit = Semaphore(4)
        val dailyRows = dates.map { day ->
            async {
                limit.withPermit {
                api.jsonPost(
                    endpoint = "ajax_user.php?act=userPayStat",
                    referer = "ustat.php",
                    fields = mapOf("day" to day, "method" to "type"),
                ).rows().objectRows()
                }
            }
        }.awaitAll()
        aggregateMerchantFlows(dates, merchantIds, dailyRows)
    }

    suspend fun domainAudits(page: Int = 1, filters: Map<String, String> = emptyMap()): PagedResult<DomainAuditSummary> {
        val json = api.page("ajax_user.php?act=domainList", "domain.php", page, DOMAIN_PAGE_SIZE, filters)
        return json.mapRows { row ->
            DomainAuditSummary(
                id = row.text("id"), merchantId = row.text("uid"), domain = row.text("domain"),
                status = row.intValue("status"), createdAt = row.text("addtime"), auditedAt = row.text("endtime"),
                remark = row.text("remark"), merchantName = row.text("username"), projectName = row.text("project_name"), rawStatus = row.text("status"),
            )
        }
    }

    suspend fun addDomain(merchantId: String, domain: String): ApiResult = api.postAction(
        endpoint = "ajax_user.php?act=addDomain",
        referer = "domain.php",
        fields = mapOf("uid" to merchantId, "domain" to domain),
    )

    suspend fun setDomainStatus(id: String, status: Int, remark: String = ""): ApiResult = api.postAction(
        endpoint = "ajax_user.php?act=setDomainStatus",
        referer = "domain.php",
        fields = mapOf("id" to id, "status" to status.toString(), "remark" to remark),
    )

    suspend fun deleteDomain(id: String): ApiResult = api.postAction(
        endpoint = "ajax_user.php?act=delDomain",
        referer = "domain.php",
        fields = mapOf("id" to id),
    )

    suspend fun orderAction(action: String, tradeNo: String, extra: Map<String, String> = emptyMap()): ApiResult {
        val fieldName = if (action == "queryChannelOrder") "tradeNo" else "trade_no"
        return api.postAction(
            endpoint = "ajax_order.php?act=$action",
            referer = "order.php",
            fields = mapOf(fieldName to tradeNo) + extra,
        )
    }

    suspend fun setOrderStatus(tradeNo: String, status: Int): ApiResult = api.getAction(
        endpoint = "ajax_order.php",
        referer = "order.php",
        query = mapOf("act" to "setStatus", "trade_no" to tradeNo, "status" to status.toString()),
    )

    suspend fun batchOrderStatus(tradeNos: List<String>, status: Int): ApiResult = api.postActionMulti(
        endpoint = "ajax_order.php?act=operation",
        referer = "order.php",
        fields = mapOf("status" to listOf(status.toString()), "checkbox[]" to tradeNos),
    )

    suspend fun manualRefund(tradeNo: String, amount: String, remark: String = ""): ApiResult =
        orderAction("refund", tradeNo, mapOf("money" to amount, "remark" to remark))

    suspend fun apiRefund(tradeNo: String, amount: String, paymentPassword: String, remark: String = ""): ApiResult =
        orderAction("apirefund", tradeNo, mapOf("money" to amount, "paypwd" to paymentPassword, "remark" to remark))

    suspend fun settlementAction(action: String, id: String, extra: Map<String, String> = emptyMap()): ApiResult =
        api.postAction("ajax_settle.php?act=$action", "slist.php", mapOf("id" to id) + extra)

    suspend fun batchSettlement(ids: List<String>, status: Int): ApiResult = api.postActionMulti(
        endpoint = "ajax_settle.php?act=opslist",
        referer = "slist.php",
        fields = mapOf("status" to listOf(status.toString()), "checkbox[]" to ids),
    )

    suspend fun setSettlementStatus(
        id: String,
        status: Int,
        merchantId: String,
        usdtRate: String,
        usdtAmount: String,
    ): ApiResult = api.getAction(
        endpoint = "ajax_settle.php",
        referer = "slist.php",
        query = mapOf(
            "act" to "setSettleStatus",
            "id" to id,
            "uid" to merchantId,
            "status" to status.toString(),
            "usdt_rate" to usdtRate,
            "usdt" to usdtAmount,
        ),
    )

    suspend fun failSettlementWithReason(id: String, reason: String, merchantId: String, usdtRate: String, usdtAmount: String): ApiResult {
        if (reason.isBlank()) return ApiResult(false, "请填写失败理由")
        val status = setSettlementStatus(id, 3, merchantId, usdtRate, usdtAmount)
        if (!status.success) return status
        val result = runCatching { settlementAction("settle_setresult", id, mapOf("result" to reason)) }
            .getOrElse { ApiResult(false, it.message ?: "网络请求失败") }
        return if (result.success) result else result.copy(partial = true, message = "状态已更新，失败理由保存失败：${result.message}")
    }

    suspend fun saveSettlementInfo(id: String, payType: String, account: String, name: String): ApiResult =
        settlementAction(
            "settle_save", id,
            mapOf("pay_type" to payType, "pay_account" to account, "pay_name" to name),
        )

    suspend fun setMerchantStatus(uid: String, type: String, enabled: Boolean): ApiResult = api.postAction(
        endpoint = "ajax_user.php?act=setUser",
        referer = "ulist.php",
        fields = mapOf("uid" to uid, "type" to type, "status" to if (enabled) "1" else "0"),
    )

    suspend fun setMerchantGroup(uid: String, gid: String, endTime: String): ApiResult = api.postAction(
        endpoint = "ajax_user.php?act=setUserGroup",
        referer = "ulist.php",
        fields = mapOf("uid" to uid, "gid" to gid, "endtime" to endTime),
    )

    suspend fun rechargeMerchant(uid: String, operation: String, amount: String): ApiResult {
        val fields = merchantBalanceFields(uid, operation, amount)
            ?: return ApiResult(false, "请选择增加或扣除余额，并输入大于 0、最多两位小数的金额及有效商户号")
        return api.postAction(
            endpoint = "ajax_user.php?act=recharge",
            referer = "ulist.php",
            fields = fields,
        )
    }

    suspend fun genericPage(
        endpoint: String,
        referer: String,
        idField: String,
        visibleFields: List<String>,
        page: Int = 1,
        filters: Map<String, String> = emptyMap(),
    ): PagedResult<GenericRow> {
        val json = api.page(endpoint, referer, page, PAGE_SIZE, filters)
        return json.mapRows { row ->
            GenericRow(
                id = row.text(idField),
                values = visibleFields.associateWith { field -> row.text(field) },
            )
        }
    }

    suspend fun nativeDocument(path: String): NativeDocument = NativeDocumentParser.parse(api.document(path), path)

    suspend fun paymentDocument(
        moduleId: String,
        page: Int = 1,
        query: String = "",
        rollTab: RollGroupTab = RollGroupTab.ALL,
        channelTab: PaymentChannelTab = PaymentChannelTab.ALL,
        channelType: String = "",
    ): NativeDocument {
        val basePath = paymentPath(moduleId)
        val path = when (moduleId) {
            "rolls" -> rollGroupTabPath(basePath, rollTab)
            "channels" -> paymentChannelPath(basePath, channelTab, channelType)
            else -> basePath
        }
        val html = api.document(path)
        val pageDocument = NativeDocumentParser.parse(html, path)
        val enhanced = enhancePaymentDocument(moduleId, pageDocument)

        // 支付通道、支付方式、轮询组和中转代理均由 PHP 直接输出 HTML 表格。
        // 不再构造后台并不存在的 channelList/rollList/proxyList 接口。
        if (moduleId in setOf("channels", "payment_types", "rolls", "proxies")) {
            return enhanced
        }

        // 通道监测是这些页面中唯一通过 JavaScript POST 动态加载的列表。
        val listEndpoints = (
            NativeDocumentParser.listEndpoints(html) +
                if (moduleId == "channel_monitor") listOf("ajax_pay.php?act=channelMonitor") else emptyList()
            ).distinct()
        if (listEndpoints.isEmpty()) return enhanced
        val filters = buildMap {
            if (query.isNotBlank()) put("channel_id", query.trim())
            put("type", "0")
        }
        val json = loadFirstList(listEndpoints, path, page, filters)
            ?: throw IllegalStateException("通道监测列表加载失败，请重试")
        val array = json.rows()
        if (array.length() == 0) return enhanced
        val fieldNames = NativeDocumentParser.tableFields(html)
        val objectRows = array.objectRows(fieldNames)
        val first = objectRows.firstOrNull() ?: return enhanced
        val availableKeys = buildList {
            val iterator = first.keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        val priority = listOf(
            "id", "name", "rate", "success_rate_5min", "success_rate_30min",
            "success_rate_1hour", "success_rate_24hour", "status", "result", "lasttime",
        )
        val visibleKeys = (priority.filter { it in availableKeys } + availableKeys.sorted())
            .distinct().filterNot(::isSensitiveColumn)
        val rows = mutableListOf<List<String>>()
        val actions = mutableListOf<List<NativeAction>>()
        objectRows.forEach { row ->
            val values = buildMap {
                val iterator = row.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    put(key, row.text(key))
                }
            }
            rows += visibleKeys.map { values[it].orEmpty() }
            actions += NativeDocumentParser.actionsForValues(html, values)
        }
        val dynamicTable = NativeTable(
            title = "监测记录 · ${json.optInt("total", rows.size)} 条",
            headers = visibleKeys.map(::paymentColumnLabel),
            rows = rows,
            rowActions = actions,
        )
        return enhanced.copy(tables = listOf(dynamicTable))
    }

    suspend fun paymentEditor(
        moduleId: String,
        action: NativeAction,
        current: NativeDocument,
    ): NativeForm {
        val path = paymentPath(moduleId)
        val json = api.jsonGet(action.endpoint, path, action.fields)
        check(!json.has("code") || json.optInt("code") in setOf(0, 200)) { json.optString("msg", "配置读取失败") }
        if (action.endpoint.contains("rollInfo")) return rollRouteForm(action.endpoint, json)

        val html = json.optString("data").takeIf { it.contains('<') }
        if (html != null) {
            val parsed = NativeDocumentParser.parse(html, path).forms.maxByOrNull { it.fields.size }
                ?: throw IllegalStateException("后台未返回可编辑的配置表单")
            val pluginOptions = (
                NativeDocumentParser.pluginOptions(html) +
                    current.forms.flatMap { it.fields }
                        .firstOrNull { it.name.equals("plugin", true) }?.options.orEmpty()
                ).distinctBy { it.value }
            val saveEndpoint = when {
                action.endpoint.contains("channelInfo") -> "ajax_pay.php?act=saveChannelInfo&id=${action.endpoint.queryValue("id")}"
                parsed.action != path -> parsed.action
                else -> current.forms.maxByOrNull { it.fields.size }?.action ?: parsed.action
            }
            return parsed.copy(
                title = action.label,
                action = saveEndpoint,
                method = "POST",
                fields = parsed.fields.map { field ->
                    if (field.name.equals("plugin", true)) {
                        field.copy(type = "select", options = (field.options + pluginOptions).distinctBy { it.value })
                    } else field
                },
            )
        }

        val data = json.optJSONObject("data") ?: json
        val template = current.forms.filter { form ->
            when {
                action.endpoint.contains("getChannel") -> form.fields.any { it.name == "plugin" } && form.fields.any { it.name == "rate" }
                action.endpoint.contains("getPayType") -> form.fields.any { it.name == "device" } && form.fields.any { it.name == "showname" }
                action.endpoint.contains("getRoll") -> form.fields.any { it.name == "kind" } && form.fields.any { it.name == "type" }
                else -> true
            }
        }.maxByOrNull { it.fields.size } ?: throw IllegalStateException("当前页面没有可用的编辑表单")
        val saveEndpoint = when {
            action.endpoint.contains("getChannel") -> "ajax_pay.php?act=saveChannel"
            action.endpoint.contains("getPayType") -> "ajax_pay.php?act=savePayType"
            action.endpoint.contains("getRoll") -> "ajax_pay.php?act=saveRoll"
            else -> template.action
        }
        val id = action.endpoint.queryValue("id")
        val pluginOptions = buildList {
            current.forms.flatMap { it.fields }.firstOrNull { it.name.equals("plugin", true) }?.options?.let(::addAll)
            listOf("plugins", "pluginList", "plugin_list").forEach { key ->
                val source = data.optJSONArray(key) ?: json.optJSONArray(key)
                if (source != null) for (index in 0 until source.length()) {
                    when (val option = source.opt(index)) {
                        is JSONObject -> {
                            val value = option.text("value").ifBlank { option.text("plugin") }.ifBlank { option.text("id") }
                            val label = option.text("label").ifBlank { option.text("name") }.ifBlank { value }
                            if (value.isNotBlank()) add(NativeOption(value, label))
                        }
                        is String -> if (option.isNotBlank()) add(NativeOption(option, option))
                    }
                }
            }
        }.distinctBy { it.value }
        val fields = template.fields.map { field ->
            val value = when (field.name) {
                "action" -> if (action.label.contains("新增") || action.label.contains("添加")) "add" else "edit"
                "id" -> id.ifBlank { data.text("id") }
                else -> {
                    val key = field.name.removeSuffix("[]")
                    val array = data.optJSONArray(key)
                    if (field.type == "multiselect" && array != null) (0 until array.length()).joinToString(",") { array.optString(it) }
                    else if (data.has(key)) data.text(key) else field.value
                }
            }
            if (field.name.equals("plugin", true)) {
                val options = (field.options + pluginOptions).distinctBy { it.value }
                field.copy(value = if (field.sensitive) "" else value, type = "select", options = options)
            } else field.copy(value = if (field.sensitive) "" else value,
                readOnly = field.readOnly || (moduleId == "payment_types" && field.name == "name" && id.isNotBlank()))
        }
        return template.copy(title = action.label, action = saveEndpoint, method = "POST", fields = fields)
    }

    /** 官网在选择支付方式后，使用该接口动态填充支付插件下拉框。 */
    suspend fun paymentPlugins(typeId: String): List<NativeOption> {
        if (typeId.isBlank() || typeId == "0") return emptyList()
        val path = paymentPath("channels")
        val json = api.jsonGet("ajax_pay.php?act=getPlugins&typeid=$typeId", path)
        val code = json.optInt("code", 0)
        if (code !in setOf(0, 200)) {
            throw IllegalStateException(json.optString("msg", "支付插件加载失败"))
        }
        val data = json.optJSONArray("data") ?: json.rows()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val value = item.text("name").ifBlank { item.text("value") }.ifBlank { item.text("plugin") }
                val label = item.text("showname").ifBlank { item.text("label") }.ifBlank { value }
                if (value.isNotBlank()) add(NativeOption(value, label))
            }
        }.distinctBy { it.value }
    }

    suspend fun pluginAccountForm(pluginName: String, channelId: String = ""): NativeForm? {
        if (pluginName.isBlank()) return null
        val json = api.jsonGet("ajax_pay.php?act=getPlugin&name=${URLEncoder.encode(pluginName, "UTF-8")}", paymentPath("channels"))
        val data = json.optJSONObject("data") ?: json
        val inputs = data.opt("inputs") ?: return null
        val fields = buildList {
            when (inputs) {
                is JSONObject -> inputs.keys().forEach { name ->
                    val config = inputs.optJSONObject(name)
                    val label = config?.text("label")?.ifBlank { config.text("name") } ?: inputs.optString(name, name)
                    val secret = Regex("key|secret|token|password|密钥|密码", RegexOption.IGNORE_CASE).containsMatchIn("$name $label")
                    add(NativeField(name, label.ifBlank { name }, "", if (secret) "password" else "text", sensitive = secret))
                }
                is JSONArray -> for (index in 0 until inputs.length()) {
                    val config = inputs.optJSONObject(index) ?: continue
                    val name = config.text("name").ifBlank { config.text("key") }
                    if (name.isBlank()) continue
                    val label = config.text("label").ifBlank { config.text("title") }.ifBlank { name }
                    val secret = Regex("key|secret|token|password|密钥|密码", RegexOption.IGNORE_CASE).containsMatchIn("$name $label")
                    add(NativeField(name, label, "", if (secret) "password" else "text", required = config.optBoolean("required", false), sensitive = secret))
                }
            }
        }
        return fields.takeIf { it.isNotEmpty() }?.let { NativeForm("账户配置 · $pluginName", "ajax_pay.php?act=saveChannelInfo&id=$channelId", "POST", it) }
    }

    suspend fun paymentChannelDetail(item: PaymentChannelItem): PaymentChannelDetail {
        val path = paymentPath("channels")
        val channelJson = api.jsonGet("ajax_pay.php?act=getChannel&id=${item.id}", path)
        val channelCode = channelJson.optInt("code", 0)
        if (channelCode !in setOf(0, 200)) {
            throw IllegalStateException(channelJson.optString("msg", "通道详情加载失败"))
        }
        val channel = channelJson.optJSONObject("data") ?: channelJson
        val pluginValue = channel.text("plugin").ifBlank { item.plugin }
        val pluginJson = runCatching {
            api.jsonGet("ajax_pay.php?act=getPlugin&name=${URLEncoder.encode(pluginValue, "UTF-8")}", path)
        }.getOrNull()
        val plugin = pluginJson?.optJSONObject("data") ?: pluginJson
        val mode = when (channel.text("mode")) {
            "0" -> "支付金额扣除手续费后加入商户余额"
            "1" -> "支付完成后不增加商户余额，并扣除手续费"
            else -> channel.text("mode").ifBlank { "默认模式" }
        }
        return PaymentChannelDetail(
            item = item,
            paymentTypeId = channel.text("type"),
            mode = mode,
            dailyLimit = channel.text("daytop").ifBlank { "不限" }.let { if (it == "0") "不限" else it },
            minimumAmount = channel.text("paymin").ifBlank { "不限" },
            maximumAmount = channel.text("paymax").ifBlank { "不限" },
            pluginName = plugin?.text("name").orEmpty().ifBlank { pluginValue },
            pluginDescription = plugin?.text("showname").orEmpty(),
            pluginAuthor = plugin?.text("author").orEmpty(),
            pluginLink = plugin?.text("link").orEmpty(),
            pluginPath = pluginValue.takeIf(String::isNotBlank)?.let { "/plugins/$it/" }.orEmpty(),
        )
    }

    suspend fun testPayment(channelId: String, name: String, amount: String): PaymentTestResult {
        val path = paymentPath("channels")
        val json = api.jsonPost(
            endpoint = "ajax_pay.php?act=testpay",
            referer = path,
            fields = mapOf("channel" to channelId, "name" to name, "money" to amount),
        )
        val code = json.optInt("code", -1)
        return PaymentTestResult(
            success = code == 0 || code == 200,
            message = json.optString("msg", if (code == 0 || code == 200) "测试订单创建成功" else "测试支付失败"),
            paymentUrl = json.optString("url"),
            tradeNo = json.text("trade_no").ifBlank { json.optJSONObject("data")?.text("trade_no").orEmpty() },
            qrContent = json.text("qrcode").ifBlank { json.text("url") },
        )
    }

    suspend fun runPaymentAction(moduleId: String, action: NativeAction): ApiResult {
        val path = paymentPath(moduleId)
        if (moduleId == "payment_types" && action.endpoint.contains("delPayType")) {
            val id = action.endpoint.queryValue("id").ifBlank { action.fields["id"].orEmpty() }
            if ((id.toIntOrNull() ?: Int.MAX_VALUE) < 4) return ApiResult(false, "系统内置支付方式不能删除")
        }
        return if (action.method.equals("POST", true)) api.postAction(action.endpoint, path, action.fields)
        else api.getAction(action.endpoint, path, action.fields)
    }

    suspend fun submitNativeForm(path: String, form: NativeForm, values: Map<String, String>): ApiResult {
        val submitted = if (form.action.contains("saveRollInfo")) {
            val activeIndexes = values.mapNotNull { (name, value) ->
                Regex("list\\[(\\d+)]\\[channel]").matchEntire(name)?.groupValues?.getOrNull(1)
                    ?.takeIf { value.isNotBlank() }
            }.toSet()
            if (activeIndexes.isEmpty()) return ApiResult(false, "轮询路由至少需要一个通道")
            val members = activeIndexes.map { values["list[$it][channel]"].orEmpty() }
            if (members.distinct().size != members.size) return ApiResult(false, "同一通道不能重复添加")
            val weights = activeIndexes.map { values["list[$it][weight]"]?.toIntOrNull() }
            if (weights.any { it == null || it < 0 } || weights.filterNotNull().all { it == 0 }) return ApiResult(false, "权重须为非负整数，且至少一个通道权重大于 0")
            values.filterKeys { name ->
                val index = Regex("list\\[(\\d+)]").find(name)?.groupValues?.getOrNull(1)
                index == null || index in activeIndexes
            }
        } else values
        return api.submitForm(form.action, form.method, path, submitted)
    }

    suspend fun submitPaymentForm(moduleId: String, form: NativeForm, values: Map<String, String>): ApiResult {
        if (moduleId != "payment_types") return submitNativeForm(paymentPath(moduleId), form, values)
        form.methodFeeField()?.let { field ->
            val rate = values[field.name]?.toBigDecimalOrNull()
            if (rate == null || rate < java.math.BigDecimal.ZERO || rate > java.math.BigDecimal(100)) return ApiResult(false, "支付方式费率须在 0–100% 之间")
        }
        val submitted = values.toMutableMap()
        form.fields.firstOrNull { it.name == "name" && it.readOnly }?.let { submitted[it.name] = it.value }
        val bindings = form.methodBindingField()
        bindings?.let { field ->
            val selected = selectedChannelIds(submitted[field.name].orEmpty())
            val typeId = form.fields.firstOrNull { it.name == "id" }?.value.orEmpty()
            val channels = configurationChannels().associateBy { it.id }
            if (selected.any { id -> channels[id]?.let { it.typeId != typeId } ?: true }) return ApiResult(false, "所选通道已失效或支付方式不匹配，请移除后保存")
            submitted.remove(field.name)
            selected.forEachIndexed { index, id -> submitted["${field.name.removeSuffix("[]")}[$index]"] = id }
        }
        val result = submitNativeForm(paymentPath(moduleId), form, submitted)
        if (!result.success || (bindings == null && form.methodFeeField() == null)) return result
        val creating = form.fields.firstOrNull { it.name == "id" }?.value.isNullOrBlank()
        val id = form.fields.firstOrNull { it.name == "id" }?.value?.takeIf(String::isNotBlank) ?: result.recordId
        if (id.isNullOrBlank()) return ApiResult(false, "后台已响应，但未返回记录 ID，无法确认费率与通道绑定；请刷新核对，勿重复创建", partial = true)
        val json = try { api.jsonGet("ajax_pay.php?act=getPayType&id=$id", paymentPath(moduleId)) }
        catch (error: SessionExpiredException) { throw error }
        catch (error: Exception) { return ApiResult(false, "基本信息已保存，但配置核对失败：${error.message}；请刷新核对", recordId = id, partial = creating) }
        val saved = json.optJSONObject("data") ?: return ApiResult(false, "后台未返回保存后的配置，请刷新核对", recordId = id, partial = creating)
        val fee = form.methodFeeField()
        val savedFee = fee?.let { saved.text(it.name).toBigDecimalOrNull() }
        if (fee != null && (savedFee == null || savedFee.compareTo(values.getValue(fee.name).toBigDecimal()) != 0)) return ApiResult(false, "后台未保存支付方式费率，请刷新核对", recordId = id, partial = creating)
        if (bindings != null) {
            val key = bindings.name.removeSuffix("[]")
            val array = saved.optJSONArray(key)
            val savedIds = if (array != null) (0 until array.length()).map { array.optString(it) }.toSet() else selectedChannelIds(saved.text(key))
            if (!saved.has(key) || savedIds != selectedChannelIds(values[bindings.name].orEmpty())) return ApiResult(false, "后台未保存通道绑定，请刷新核对", recordId = id, partial = creating)
        }
        return result
    }

    suspend fun addBlacklist(type: String, content: String, reason: String, days: String, remark: String): ApiResult =
        api.postAction(
            "ajax_user.php?act=addBlack", "blacklist.php",
            mapOf("type" to type, "content" to content, "black_reason" to reason, "days" to days, "remark" to remark),
        )

    suspend fun deleteBlacklist(id: String): ApiResult = api.postAction(
        "ajax_user.php?act=delBlack", "blacklist.php", mapOf("id" to id),
    )

    private fun <T> JSONObject.mapRows(mapper: (JSONObject) -> T): PagedResult<T> {
        val array = rows().objectRows()
        val result = buildList {
            array.forEach { add(mapper(it)) }
        }
        return PagedResult(optInt("total", result.size), result)
    }

    private fun enhancePaymentDocument(moduleId: String, document: NativeDocument): NativeDocument {
        val tables = document.tables.map { table ->
            val actions = table.rows.mapIndexed { index, row ->
                val parsed = table.rowActions.getOrNull(index).orEmpty()
                val id = parsed.firstNotNullOfOrNull { action ->
                    action.endpoint.queryValue("id").takeIf(String::isNotBlank)
                        ?: action.fields["id"]?.takeIf(String::isNotBlank)
                } ?: row.firstOrNull()?.takeIf { value -> value.all { it.isDigit() } }.orEmpty()
                if (id.isBlank()) parsed else when (moduleId) {
                    "channels" -> {
                        val enabled = row.any { it.contains("开启") || it.contains("启用") || it.contains("正常") }
                        parsed.ensureActions(
                            NativeAction("编辑", "ajax_pay.php?act=getChannel&id=$id"),
                            NativeAction("账户配置", "ajax_pay.php?act=channelInfo&id=$id"),
                            NativeAction(if (enabled) "停用" else "启用", "ajax_pay.php?act=setChannel&id=$id&status=${if (enabled) 0 else 1}", destructive = enabled),
                            NativeAction("删除", "ajax_pay.php?act=delChannel&id=$id", destructive = true),
                        )
                    }
                    "payment_types" -> {
                        val enabled = row.any { it.contains("开启") || it.contains("启用") || it.contains("正常") }
                        parsed.ensureActions(
                            NativeAction("编辑", "ajax_pay.php?act=getPayType&id=$id"),
                            NativeAction("关联通道", "futoon://channels?type=$id"),
                            NativeAction(if (enabled) "停用" else "启用", "ajax_pay.php?act=setPayType&id=$id&status=${if (enabled) 0 else 1}", destructive = enabled),
                            NativeAction("删除", "ajax_pay.php?act=delPayType&id=$id", destructive = true),
                        )
                    }
                    "rolls" -> {
                        val enabled = row.any { it.contains("开启") || it.contains("启用") || it.contains("正常") }
                        parsed.ensureActions(
                            NativeAction("编辑", "ajax_pay.php?act=getRoll&id=$id"),
                            NativeAction("调整路由", "ajax_pay.php?act=rollInfo&id=$id"),
                            NativeAction(if (enabled) "停用" else "启用", "ajax_pay.php?act=setRoll&id=$id&status=${if (enabled) 0 else 1}", destructive = enabled),
                            NativeAction("删除", "ajax_pay.php?act=delRoll&id=$id", destructive = true),
                        )
                    }
                    else -> parsed
                }
            }
            table.copy(rowActions = actions)
        }
        val notice = if (moduleId == "payment_types") {
            document.notices + "支付方式费率向商户收取；通道成本在通道中单独设置。"
        } else document.notices
        return document.copy(tables = tables, notices = notice.distinct())
    }

    private fun rollRouteForm(endpoint: String, json: JSONObject): NativeForm {
        val data = json.optJSONObject("data") ?: json
        val channelArray = data.optJSONArray("channels") ?: json.optJSONArray("channels")
        val infoArray = data.optJSONArray("info") ?: json.optJSONArray("info")
        val options = buildList {
            add(NativeOption("", "不使用"))
            if (channelArray != null) for (index in 0 until channelArray.length()) {
                channelArray.optJSONObject(index)?.let { add(NativeOption(it.text("id"), it.text("name"))) }
            }
        }
        val fields = mutableListOf<NativeField>()
        val count = maxOf((infoArray?.length() ?: 0) + 3, 5)
        repeat(count) { index ->
            val info = infoArray?.optJSONObject(index)
            fields += NativeField("list[$index][channel]", "路由 ${index + 1} · 通道", info?.text("channel").orEmpty(), "select", options)
            fields += NativeField("list[$index][weight]", "路由 ${index + 1} · 权重", info?.text("weight").orEmpty().ifBlank { "1" }, "number")
        }
        return NativeForm(
            title = "调整轮询路由",
            action = "ajax_pay.php?act=saveRollInfo&id=${endpoint.queryValue("id")}",
            method = "POST",
            fields = fields,
        )
    }

    private suspend fun paymentPath(moduleId: String): String {
        discoveredPaymentPaths[moduleId]?.let { return it }
        val fallback = when (moduleId) {
            "channels" -> "pay_channel.php"
            "payment_types" -> "pay_type.php"
            "rolls" -> "pay_roll.php"
            "channel_monitor" -> "pay_channel_monitor.php"
            "proxies" -> "proxy.php"
            else -> throw IllegalArgumentException("未知支付管理模块")
        }
        val labels = when (moduleId) {
            "channels" -> listOf("支付通道")
            "payment_types" -> listOf("支付方式")
            "rolls" -> listOf("通道轮询", "通道轮训")
            "channel_monitor" -> listOf("通道监测", "通道监控", "通道检测")
            "proxies" -> listOf("中转代理", "代理管理")
            else -> emptyList()
        }
        val discovered = runCatching {
            NativeDocumentParser.navigationPath(api.document("index.php"), labels)
        }.getOrNull()
        val resolved = (discovered ?: fallback).let { path ->
            if (moduleId == "channels" || moduleId == "rolls") path.withQueryValue("tab", "all") else path
        }
        discoveredPaymentPaths[moduleId] = resolved
        return resolved
    }

    private fun NativeDocument.filterRows(query: String): NativeDocument {
        val needle = query.trim()
        if (needle.isBlank()) return this
        return copy(tables = tables.map { table ->
            val indexes = table.rows.indices.filter { index ->
                table.rows[index].any { value -> value.contains(needle, ignoreCase = true) }
            }
            table.copy(
                rows = indexes.map(table.rows::get),
                rowActions = indexes.map { index -> table.rowActions.getOrNull(index).orEmpty() },
            )
        })
    }

    private fun String.withQueryValue(name: String, value: String): String {
        val expression = Regex("([?&])${Regex.escape(name)}=[^&#]*")
        if (expression.containsMatchIn(this)) {
            return replace(expression) { match -> "${match.groupValues[1]}$name=$value" }
        }
        return this + if (contains('?')) "&$name=$value" else "?$name=$value"
    }

    private fun isSensitiveColumn(name: String): Boolean {
        val lower = name.lowercase()
        return listOf("pwd", "password", "key", "secret", "token", "private", "cert").any { lower.contains(it) }
    }

    private fun paymentColumnLabel(name: String): String = when (name.lowercase()) {
        "id" -> "ID"
        "channel" -> "通道"
        "name" -> "名称"
        "rate" -> "费率"
        "typename" -> "支付方式"
        "plugin" -> "插件"
        "url" -> "地址"
        "ip" -> "IP"
        "status" -> "状态"
        "result" -> "监测结果"
        "lasttime" -> "最近检测"
        "success_rate_5min" -> "5 分钟成功率"
        "success_rate_30min" -> "30 分钟成功率"
        "success_rate_1hour" -> "1 小时成功率"
        "success_rate_24hour" -> "24 小时成功率"
        "addtime" -> "创建时间"
        "remark" -> "备注"
        else -> name
    }

    private fun String.queryValue(name: String): String = Regex("(?:[?&])${Regex.escape(name)}=([^&#]+)")
        .find(this)?.groupValues?.getOrNull(1).orEmpty()

    private fun List<NativeAction>.ensureActions(vararg desired: NativeAction): List<NativeAction> =
        (this + desired.filter { candidate -> none { it.endpoint == candidate.endpoint || it.label == candidate.label } })

    /** 同一个旧后台模块可能用 POST 或 GET 加载 Bootstrap Table；只读取第一个有效响应。 */
    private suspend fun loadFirstList(
        endpoints: List<String>,
        referer: String,
        page: Int,
        filters: Map<String, String>,
    ): JSONObject? {
        var emptyResponse: JSONObject? = null
        for (endpoint in endpoints.distinct()) {
            for (method in listOf("POST", "GET")) {
                val response = runCatching { api.page(endpoint, referer, page, PAGE_SIZE, filters, method) }.getOrNull()
                    ?: continue
                val codeRejected = response.has("code") && response.optInt("code", -1) !in setOf(0, 200)
                val message = response.optString("msg", response.optString("message"))
                if (codeRejected || message.contains("no act", true)) continue
                if (response.rows().length() > 0) return response
                if (emptyResponse == null) emptyResponse = response
            }
        }
        return emptyResponse
    }

    companion object {
        const val PAGE_SIZE = 30
        const val DOMAIN_PAGE_SIZE = 15
    }
}
