package com.futoonpay.admin.data

internal fun nativeVisibleValue(label: String, value: String): String {
    if (Regex("password|secret|api.?key|token|密码|密钥", RegexOption.IGNORE_CASE).containsMatchIn(label)) return if (value.isBlank()) "—" else "••••••••"
    return value.replace(Regex("(://[^:/@\\s]+:)[^@/\\s]+@"), "$1••••••••@")
}

internal fun nativeActionTarget(document: NativeDocument?, action: NativeAction): String {
    document?.tables.orEmpty().forEach { table ->
        val index = table.rowActions.indexOfFirst { actions -> actions.any { it.endpoint == action.endpoint && it.fields == action.fields } }
        if (index < 0) return@forEach
        val row = table.rows.getOrNull(index).orEmpty()
        val fields = table.headers.mapIndexed { i, label -> label to row.getOrNull(i).orEmpty() }
        val identity = fields.filter { (label, _) -> label.equals("id", true) || label.contains("名称") || label.equals("name", true) }.joinToString(" · ") { nativeVisibleValue(it.first, it.second) }
        val associations = fields.filter { (label, _) -> label.contains("关联") || label.contains("成员") }.joinToString("\n") { "${it.first}：${nativeVisibleValue(it.first, it.second)}" }
        return "目标：${identity.ifBlank { action.label }}" + if (associations.isNotBlank()) "\n$associations" else ""
    }
    return "目标：${action.label}"
}

/** Only explicit editor/detail links are navigation; mutations still require confirmation. */
internal fun NativeAction.opensNativeDocument(): Boolean = method.equals("GET", true) && !destructive &&
    listOf("编辑", "配置", "查看", "详情", "新增", "添加").any(label::contains) &&
    !listOf("save", "set", "del", "delete", "remove", "reset", "update", "check", "detect").any {
        endpoint.substringAfter("act=", "").substringBefore('&').startsWith(it, true)
    }

internal fun NativeAction.documentPath(): String = endpoint + if (fields.isEmpty()) "" else
    (if (endpoint.contains('?')) "&" else "?") + fields.entries.joinToString("&") { (key, value) ->
        java.net.URLEncoder.encode(key, "UTF-8") + "=" + java.net.URLEncoder.encode(value, "UTF-8")
    }
