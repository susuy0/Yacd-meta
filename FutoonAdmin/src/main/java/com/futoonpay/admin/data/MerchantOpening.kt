package com.futoonpay.admin.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.json.JSONObject

internal const val MERCHANT_OPENING_PAGE = "uset.php?my=add"
internal const val MERCHANT_OPENING_ACTION = "ajax_user.php?act=addUser"
private val openingNames = setOf("email", "pwd", "gid", "url", "settle_id", "account", "username", "mode", "status", "pay", "settle")

/** The observed addUser form, kept separate from editUser and its full-row update. */
internal fun merchantOpeningForm(html: String): NativeForm {
    val callback = Regex("(?:return\\s+)?addUser\\(\\s*this\\s*\\)")
    val form = Jsoup.parse(html).select("form").singleOrNull { callback.matches(it.attr("onsubmit").trim().trimEnd(';')) }
        ?: error("后台未返回开户表单，请核对登录状态和开户权限")
    require(form.attr("method").equals("POST", true)) { "后台开户表单提交方式发生变化，请更新客户端" }
    val parsed = NativeDocumentParser.parse(form.outerHtml(), MERCHANT_OPENING_PAGE).forms.singleOrNull()
        ?: error("开户表单不完整")
    require(parsed.fields.map { it.name }.containsAll(openingNames)) { "后台开户字段不完整，请重新加载" }
    require(parsed.fields.none { it.name in setOf("uid", "key", "apikey", "money") }) { "后台返回了已有商户表单，请重新打开开户" }
    return parsed.copy(title = "商户开户", action = MERCHANT_OPENING_ACTION, method = "POST", fields = parsed.fields.map { field ->
        when (field.name) {
            "pwd" -> field.copy(label = "登录密码（选填）", type = "password", sensitive = true, value = "")
            "username" -> field.copy(label = "备注 / 结算账号姓名", required = true)
            "email" -> field.copy(label = "邮箱 / 登录账号（选填）", type = "email")
            "url" -> field.copy(label = "网站域名（选填）")
            "settle" -> {
                require(field.options.map { it.value }.containsAll(listOf("0", "1"))) { "后台结算权限选项不完整" }
                field.copy(label = "结算模式", value = "0", options = listOf(NativeOption("1", "D0 · 开启结算"), NativeOption("0", "D1 · 不开启结算")))
            }
            else -> field
        }
    })
}

internal fun settlementCycle(permission: String): String = when (permission) {
    "1" -> "D0"
    "0" -> "D1"
    else -> error("请选择 D0 或 D1 结算模式")
}

/** Always use fresh hidden fields, but never silently alter any reviewed visible field. */
internal fun merchantOpeningSubmission(original: NativeForm, fresh: NativeForm): Map<String, String> {
    require(original.action == MERCHANT_OPENING_ACTION && fresh.action == MERCHANT_OPENING_ACTION && original.method == "POST" && fresh.method == "POST") { "无效开户表单" }
    val old = original.fields.filter { it.type != "hidden" }.associateBy { it.name }
    val current = fresh.fields.filter { it.type != "hidden" }.associateBy { it.name }
    require(old.keys == current.keys) { "后台开户字段发生变化，请重新加载并核对" }
    return fresh.fields.associate { field ->
        val previous = old[field.name]
        val value = if (field.type == "hidden") field.value else {
            require(previous != null && previous.type == field.type && previous.readOnly == field.readOnly && previous.required == field.required) { "后台开户字段发生变化，请重新加载并核对" }
            if (field.readOnly) require(previous.value == field.value) { "${field.label}已变化，请重新加载" }
            previous.value
        }
        require(!field.required || value.isNotBlank()) { "${field.label}不能为空" }
        if (field.type == "select") require(field.options.any { it.value == value }) { "${field.label}选项已变化，请重新选择" }
        require(value.length <= 4096) { "${field.label}最多 4096 个字符" }
        require(!value.contains('\u0000')) { "${field.label}包含无效字符" }
        field.name to value
    }.also { settlementCycle(it.getValue("settle")) }
}

internal enum class OpeningOutcome { CREATED, REJECTED, UNCERTAIN }
internal data class MerchantOpeningResult(val outcome: OpeningOutcome, val uid: String = "", val key: String = "", val message: String = "") {
    override fun toString() = "MerchantOpeningResult(outcome=$outcome, uid=$uid, key=<redacted>)"
}

internal fun merchantOpeningResult(json: JSONObject): MerchantOpeningResult {
    if (!json.has("code") || json.isNull("code")) return MerchantOpeningResult(OpeningOutcome.UNCERTAIN, message = "后台未明确确认开户结果，请先查询商户，勿重复提交")
    if (json.optInt("code", -1) != 0) return MerchantOpeningResult(OpeningOutcome.REJECTED, message = json.optString("msg", "后台拒绝开户"))
    val uid = json.optString("uid").trim()
    if (!uid.matches(Regex("[1-9][0-9]{0,18}")) || uid.toLongOrNull() == null) return MerchantOpeningResult(OpeningOutcome.UNCERTAIN, message = "后台返回开户成功，但商户 ID 缺失，请先查询商户，勿重复提交")
    val key = json.optString("key").trim().takeIf { it.matches(Regex("[A-Za-z0-9]{32}")) }.orEmpty()
    return MerchantOpeningResult(OpeningOutcome.CREATED, uid, key, if (key.isBlank()) "开户已成功，但后台未提供有效密钥，请核对商户资料" else "开户成功")
}

internal fun merchantBindingLink(json: JSONObject): String {
    check(json.optInt("code", -1) == 0) { json.optString("msg", "绑定链接获取失败") }
    val raw = json.optString("bind").trim()
    val url = raw.toHttpUrlOrNull()
    check(url != null && url.isHttps && url.host in setOf("t.me", "telegram.me") && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
        url.encodedPath.matches(Regex("/[A-Za-z0-9_]+")) && url.queryParameterNames == setOf("start") && url.queryParameter("start").orEmpty().matches(Regex("[A-Za-z0-9_-]+_bind"))) { "后台未提供有效的 Telegram Bot 绑定链接" }
    return raw
}
