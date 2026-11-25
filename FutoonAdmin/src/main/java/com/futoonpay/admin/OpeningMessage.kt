package com.futoonpay.admin

import com.futoonpay.admin.data.settlementCycle

internal val openingPlaceholders = listOf("商户ID", "密钥", "网站域名", "结算账号姓名", "结算周期", "绑定链接")
internal val defaultOpeningTemplate = """
🏡【开户成功】
------------------------------
用户 ID: {商户ID}
密钥: {密钥}
网站域名: {网站域名}
结算账号姓名: {结算账号姓名}
结算周期: {结算周期}
点击此链接绑定Bot机器人

{绑定链接}

----------------

接口信息(均不建议使用mapi)

CDN 新通用域名
cdnhk.33zn.com
Cloudflare 新通用域名
normal.33zn.com
开发文档
normal.33zn.com/doc.html

所有操作请在机器人完成 当前官网后台因安全原因已经永久关闭

--------
结算相关事宜请查看

https://t.me/futoondocs/14

结算手续费1.6% 汇率+0.25

日流水超6000人民币可申请费率，更低通道手续费成本。

----------
上传授权域名，并通知我们为您审核通过。⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️
⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️
""".trimIndent()

// Android uses ICU, which requires a literal closing brace to be escaped.
// Share the expression so validation and substitution always recognize the same variables.
private val openingPlaceholderPattern = Regex("\\{([^{}\\n]+)\\}")

internal fun validateOpeningTemplate(template: String) {
    require(template.isNotBlank() && template.length <= 4096) { "开户文案不能为空，最多 4096 个字符" }
    val unknown = openingPlaceholderPattern.findAll(template).map { it.groupValues[1] }.filter { it !in openingPlaceholders }.toList()
    require(unknown.isEmpty()) { "未知变量：${unknown.joinToString()}" + if (unknown.any { name -> name.any(Char::isWhitespace) }) "；变量名不能包含空格" else "" }
}

internal fun openingMessage(template: String, uid: String, key: String, values: Map<String, String>, binding: String): String {
    validateOpeningTemplate(template)
    require(uid.isNotBlank() && key.isNotBlank() && binding.isNotBlank()) { "开户信息不完整，请先补全密钥和绑定链接" }
    val replacements = mapOf("商户ID" to uid, "密钥" to key, "网站域名" to values["url"].orEmpty(), "结算账号姓名" to values["username"].orEmpty(), "结算周期" to settlementCycle(values.getValue("settle")), "绑定链接" to binding)
    // One pass: merchant-controlled text containing braces must never expand other variables.
    return openingPlaceholderPattern.replace(template) { replacements.getValue(it.groupValues[1]) }.also {
        require(it.length <= 4096) { "生成后的开户文案超过 4096 个字符，请缩短模板" }
    }
}

internal data class OpeningVariable(val name: String, val start: Int, val end: Int) {
    val known: Boolean get() = name in openingPlaceholders
}
internal fun openingVariables(text: String): List<OpeningVariable> = openingPlaceholderPattern.findAll(text).map { OpeningVariable(it.groupValues[1], it.range.first, it.range.last + 1) }.toList()
