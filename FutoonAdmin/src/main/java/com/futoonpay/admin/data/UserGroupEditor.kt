package com.futoonpay.admin.data

import org.json.JSONObject

/** getGroup returns top-level JSON, unlike the HTML detail responses handled by the generic document reader. */
internal fun userGroupForm(template: NativeForm, gid: String?, data: JSONObject?): NativeForm {
    if (gid != null) {
        check(data != null && data.optInt("code", -1) == 0) { data?.optString("msg", "用户组读取失败") ?: "用户组读取失败" }
        check(data.text("gid") == gid && data.has("name") && (data.opt("info") is JSONObject || data.opt("info") is org.json.JSONArray)) { "用户组详情不完整，请刷新重试" }
    }
    fun value(name: String): String? {
        var cursor: Any? = data
        for (part in name.replace("]", "").split('[')) cursor = when (val parent = cursor) { is JSONObject -> parent.opt(part); is org.json.JSONArray -> part.toIntOrNull()?.let(parent::opt); else -> null }
        return cursor?.takeUnless { it == JSONObject.NULL }?.toString()
    }
    return template.copy(title = if (gid == null) "新增用户组" else "修改用户组", fields = template.fields.map { field ->
        val current = when {
            field.name == "action" -> if (gid == null) "add" else "edit"
            field.name == "gid" -> gid.orEmpty()
            gid != null -> value(field.name) ?: field.value
            field.name == "name" || field.name == "settings" || field.name == "settle_rate" || field.name.endsWith("[rate]") -> ""
            field.name.startsWith("info[") -> "0"
            else -> field.value
        }
        val label = mapOf("name" to "显示名称", "settings" to "用户变量", "settle_open" to "结算开关", "settle_type" to "手动结算", "settle_rate" to "结算手续费（%）")[field.name] ?: field.label
        field.copy(label = label, value = current, required = field.required || field.name == "name", options = if (field.type == "select" && current.isNotBlank() && field.options.none { it.value == current }) field.options + NativeOption(current, "原关联（$current）") else field.options)
    })
}

/** Mirrors the official channel selector: route kind follows the option, while a filled-in share is preserved. */
internal fun updateGroupRoute(form: NativeForm, values: MutableMap<String, String>, name: String, value: String) {
    values[name] = value
    val match = Regex("info\\[(\\d+)]\\[channel]").matchEntire(name) ?: return
    val option = form.fields.firstOrNull { it.name == name }?.options?.firstOrNull { it.value == value } ?: return
    val prefix = "info[${match.groupValues[1]}]"
    if (option.routeType.isNotBlank()) values["$prefix[type]"] = option.routeType
    if (values["$prefix[rate]"].isNullOrBlank() && option.suggestedRate.isNotBlank()) values["$prefix[rate]"] = option.suggestedRate
}
