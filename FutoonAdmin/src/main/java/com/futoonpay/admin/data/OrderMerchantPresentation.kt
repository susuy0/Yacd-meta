package com.futoonpay.admin.data

import org.json.JSONObject

/** The original list mappers, shared with exact matches returned by the chat bridge. */
internal fun JSONObject.toOrderSummary() = OrderSummary(
    tradeNo = text("trade_no"), merchantTradeNo = text("out_trade_no"), merchantId = text("uid"),
    domain = text("domain"), name = text("name"), amount = text("money"), paidAmount = text("realmoney"),
    merchantIncome = text("getmoney"), paymentType = text("typeshowname"), channelId = text("channel"),
    plugin = text("plugin"), createdAt = text("addtime"), completedAt = text("endtime"),
    status = intValue("status"), settlementStatus = intValue("settle"),
    currency = text("currency").ifBlank { text("currency_code") }.ifBlank { "CNY" }, merchantName = text("username"),
)

internal fun JSONObject.toMerchantSummary() = MerchantSummary(
    uid = text("uid"), username = text("username"), groupName = text("groupname"), balance = text("money"),
    frozenBalance = text("freeze_money"), telegramId = text("telegram_id"), website = text("url"),
    createdAt = text("addtime"), status = intValue("status"), paymentStatus = intValue("pay"), settlementStatus = intValue("settle"),
    account = text("account"), remark = text("username"), inviteCode = text("invite_code"),
    recommenderId = text("recommender_id"), recommenderPercent = text("recommender_percent"),
    botBindTime = text("bind_time"), fullSettlement = boolFlag("full"), fullMoney = text("full_money"), fullChain = text("full_chain"),
    fullAddress = text("full_address"), dailySettle = boolFlag("daily_settle_on"), level = text("level"), lockedCoefficient = text("locked_coefficient"),
    balances = currencyAmounts("balances").ifEmpty { buildMap { put("CNY", text("money")); text("usdt_money").takeIf(String::isNotBlank)?.let { put("USDT", it) } } },
)

internal fun JSONObject.toOrderDetail(): OrderDetail {
    val row = optJSONObject("data") ?: this
    return OrderDetail(
        tradeNo = row.text("trade_no"),
        merchantTradeNo = row.text("out_trade_no"),
        apiTradeNo = row.text("api_trade_no"),
        merchantId = row.text("uid"),
        paymentType = row.text("typename").ifBlank { row.text("typeshowname") },
        channelId = row.text("channel"),
        channelName = row.text("channelname"),
        plugin = row.text("plugin"),
        name = row.text("name"),
        amount = row.text("money"),
        paidAmount = row.text("realmoney"),
        merchantIncome = row.text("getmoney"),
        createdAt = row.text("addtime"),
        completedAt = row.text("endtime"),
        buyer = row.text("buyer"),
        domain = row.text("domain"),
        ip = row.text("ip"),
        parameter = row.text("param"),
        status = row.intValue("status"),
        settlementStatus = row.intValue("settle"),
        notifyCount = row.intValue("notify"),
        currency = row.text("currency").ifBlank { row.text("currency_code") }.ifBlank { "CNY" },
    )
}

internal fun aggregateMerchantFlows(
    dates: List<String>,
    merchantIds: Collection<String>,
    dailyRows: List<List<JSONObject>>,
): Map<String, MerchantFlowSummary> {
    val requested = merchantIds.filter(String::isNotBlank).distinct()
    val totals = requested.associateWith { DoubleArray(dates.size) }.toMutableMap()
    val alipay = mutableMapOf<String, Double>()
    val wechat = mutableMapOf<String, Double>()
    val qq = mutableMapOf<String, Double>()
    val currencies = requested.associateWith { mutableMapOf<String, DoubleArray>() }
    dailyRows.forEachIndexed { dayIndex, rows ->
        if (dayIndex !in dates.indices) return@forEachIndexed
        rows.forEach { row ->
            val uid = row.text("uid")
            if (uid.isBlank() || uid !in totals) return@forEach
            val currency = row.text("currency").ifBlank { "CNY" }.uppercase()
            currencies.getValue(uid).getOrPut(currency) { DoubleArray(dates.size) }[dayIndex] += row.moneyValue("total")
            if (currency == "CNY") {
                totals.getValue(uid)[dayIndex] += row.moneyValue("total")
                alipay[uid] = alipay.getOrDefault(uid, 0.0) + row.moneyValue("type_1")
                wechat[uid] = wechat.getOrDefault(uid, 0.0) + row.moneyValue("type_2")
                qq[uid] = qq.getOrDefault(uid, 0.0) + row.moneyValue("type_3")
            }
        }
    }
    return totals.mapValues { (uid, values) ->
        MerchantFlowSummary(
            merchantId = uid,
            points = dates.mapIndexed { index, date -> MerchantFlowPoint(date, values[index]) },
            alipayTotal = alipay.getOrDefault(uid, 0.0),
            wechatTotal = wechat.getOrDefault(uid, 0.0),
            qqTotal = qq.getOrDefault(uid, 0.0),
            currencyPoints = currencies.getValue(uid).ifEmpty { mutableMapOf("CNY" to values) }.mapValues { (_, amounts) -> dates.mapIndexed { index, date -> MerchantFlowPoint(date, amounts[index]) } },
        )
    }
}

private fun JSONObject.moneyValue(name: String): Double =
    text(name).replace(",", "").replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
