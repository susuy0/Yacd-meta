package com.futoonpay.admin.data

import org.jsoup.Jsoup

data class ConfigurationChannel(
    val id: String, val name: String, val typeId: String, val cost: String,
    val minimum: String, val maximum: String, val proxyId: String?,
)

val METHOD_FEE_NAMES = setOf("rate", "fee_rate", "merchant_rate")
val METHOD_BINDING_NAMES = setOf("channels[]", "channel_ids[]", "channels", "channel_ids")
fun NativeForm.methodFeeField() = fields.firstOrNull { it.name in METHOD_FEE_NAMES }
fun NativeForm.methodBindingField() = fields.firstOrNull { it.name in METHOD_BINDING_NAMES && it.type == "multiselect" }
fun selectedChannelIds(value: String) = value.split(',').map(String::trim).filter(String::isNotBlank).toSet()

/** Only the account form that actually calls editUser is eligible, never API/key forms. */
fun merchantProfileForm(html: String, uid: String): NativeForm {
    require(uid.matches(Regex("[1-9][0-9]*"))) { "无效商户 UID" }
    val dom = Jsoup.parse(html)
    val callback = Regex("(?:return\\s+)?editUser\\(\\s*this\\s*,\\s*$uid\\s*\\)")
    val form = dom.select("form").firstOrNull { callback.matches(it.attr("onsubmit").trim().trimEnd(';')) }
        ?: error("后台没有返回该商户的资料编辑表单")
    val parsed = NativeDocumentParser.parse(form.outerHtml(), "uset.php?my=edit&uid=$uid").forms.singleOrNull()
        ?: error("商户资料表单不完整")
    require(parsed.fields.any { it.name == "money" } && parsed.fields.any { it.name == "status" }) { "商户资料表单缺少必需字段" }
    return parsed.copy(title = "编辑商户 · UID $uid", action = "ajax_user.php?act=editUser&uid=$uid", method = "POST",
        fields = parsed.fields.map { field -> field.copy(
            label = if (field.label.matches(Regex("[A-Za-z0-9 _]+"))) merchantProfileLabels[field.name] ?: field.label else field.label,
            readOnly = field.readOnly || field.name in setOf("uid", "money", "freeze_money", "deposit", "remain_money", "key", "apikey", "pwd")) })
}

private val merchantProfileLabels = mapOf(
    "phone" to "手机号", "email" to "邮箱", "qq" to "QQ", "url" to "网站地址", "username" to "收款人姓名", "account" to "收款账户",
    "money" to "商户余额", "freeze_money" to "冻结资金", "deposit" to "保证金", "remain_money" to "结算保留金额",
    "gid" to "用户组", "status" to "用户状态", "pay" to "支付状态", "settle" to "结算状态", "settle_id" to "结算方式",
    "upid" to "上级商户", "mode" to "结算模式", "open_code" to "付款码状态", "remark" to "备注", "ordername" to "订单显示名称",
    "cert" to "认证状态", "certtype" to "认证类型", "certmethod" to "认证方式", "certname" to "认证姓名", "certno" to "证件号码",
    "certcorpname" to "企业名称", "certcorpno" to "企业证件号码",
)

/** Identity numbers are blank on screen; unchanged values must still accompany the legacy full-row update. */
fun merchantProfileSubmission(html: String, uid: String, original: NativeForm, values: Map<String, String>): Map<String, String> {
    val fresh = merchantProfileForm(html, uid)
    val merged = mergeMerchantProfile(original, fresh, values).toMutableMap()
    val form = Jsoup.parse(html).select("form").first { Regex("(?:return\\s+)?editUser\\(\\s*this\\s*,\\s*$uid\\s*\\)").matches(it.attr("onsubmit").trim().trimEnd(';')) }
    fresh.fields.filter { it.sensitive && it.name in setOf("certno", "certcorpno") }.forEach { field ->
        if (merged[field.name].isNullOrBlank()) merged[field.name] = form.select("input[name]").firstOrNull { it.attr("name") == field.name }?.attr("value").orEmpty()
    }
    return merged
}

fun mergeMerchantProfile(original: NativeForm, fresh: NativeForm, values: Map<String, String>): Map<String, String> {
    require(original.action == fresh.action) { "商户身份发生变化，请重新打开编辑" }
    val allowed = original.fields.filter { !it.readOnly && it.type != "hidden" }.associateBy { it.name }
    val dirty = values.filter { (name, value) -> allowed[name]?.let { value != it.value && (!it.sensitive || value.isNotBlank()) } == true }
    val fields = fresh.fields.associateBy { it.name }
    require(dirty.keys.all { fields[it]?.readOnly == false }) { "后台字段发生变化，请重新打开编辑" }
    dirty.forEach { (name, value) ->
        val field = fields.getValue(name)
        require(!field.required || value.isNotBlank()) { "${field.label}不能为空" }
        if (field.type == "select") require(field.options.any { it.value == value }) { "${field.label}选项无效" }
    }
    return fresh.fields.filter { !it.sensitive }.associate { it.name to it.value }.toMutableMap().apply { putAll(dirty) }
}
