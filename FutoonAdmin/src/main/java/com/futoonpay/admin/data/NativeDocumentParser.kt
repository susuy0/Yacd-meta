package com.futoonpay.admin.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object NativeDocumentParser {
    // Android's regex engine rejects an unescaped closing brace even though the JVM accepts it.
    private val DATA_FIELDS_REGEX = Regex("data\\s*:\\s*\\{([^}]*)\\}")

    fun navigationPath(html: String, labels: List<String>): String? {
        if (labels.isEmpty()) return null
        val normalizedLabels = labels.map(::normalizeLabel)
        val links = Jsoup.parse(html).select("a[href]").mapNotNull { anchor ->
            val href = anchor.attr("href").trim()
            val label = normalizeLabel(anchor.text())
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:", true)) null
            else label to href.removePrefix("./")
        }
        return links.firstOrNull { (label, _) -> label in normalizedLabels }?.second
            ?: links.firstOrNull { (label, _) -> normalizedLabels.any { target -> label.contains(target) } }?.second
    }

    fun listEndpoints(html: String): List<String> {
        val scripts = Jsoup.parse(html).select("script:not([src])").joinToString("\n") { it.data() }
        val explicitUrls = Regex(
            "(?:url|ajaxUrl)\\s*[:=]\\s*['\"]([^'\"]+\\.php(?:\\?[^'\"]*)?)['\"]",
            setOf(RegexOption.IGNORE_CASE),
        ).findAll(scripts).map { it.groupValues[1] }
        val quotedEndpoints = Regex(
            "['\"]([^'\"]+\\.php\\?[^'\"]*act=[^'\"]+)['\"]",
            setOf(RegexOption.IGNORE_CASE),
        ).findAll(scripts).map { it.groupValues[1] }
        val mutating = listOf("save", "set", "del", "delete", "remove", "reset", "check", "detect", "add", "update")
        return (explicitUrls + quotedEndpoints)
            .filter { endpoint ->
                val action = endpoint.substringAfter("act=", "").substringBefore('&')
                endpoint.contains("act=", true) && mutating.none { action.startsWith(it, true) }
            }
            .distinct()
            .toList()
    }

    /** Bootstrap Table 的数组行需要 columns.field 才能恢复成命名字段。 */
    fun tableFields(html: String): List<String> {
        val document = Jsoup.parse(html)
        val fromMarkup = document.select("th[data-field]").map { it.attr("data-field").trim() }
            .filter(String::isNotBlank)
        if (fromMarkup.isNotEmpty()) return fromMarkup.distinct()
        val scripts = document.select("script:not([src])").joinToString("\n") { it.data() }
        return Regex("field\\s*:\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .findAll(scripts).map { it.groupValues[1].trim() }.filter(String::isNotBlank).distinct().toList()
    }

    fun pluginOptions(html: String): List<NativeOption> {
        val document = Jsoup.parse(html)
        val controls = document.select("select").filter { select ->
            val signature = listOf(
                select.attr("name"), select.id(), select.className(), select.attr("data-name"),
                select.attr("data-placeholder"), select.closest(".form-group,.input-group,td,label")?.text().orEmpty(),
            ).joinToString(" ")
            signature.contains("plugin", true) || signature.contains("插件")
        }
        return controls.flatMap { it.select("option") }
            .map { NativeOption(it.attr("value").ifBlank { it.text().trim() }, it.text().trim().ifBlank { it.attr("value") }) }
            .filter { it.value.isNotBlank() }
            .distinctBy { it.value }
    }

    fun actionsForValues(html: String, row: Map<String, String>): List<NativeAction> {
        val scripts = Jsoup.parse(html).select("script:not([src])").joinToString("\n") { it.data() }
        return extractFunctions(scripts).mapNotNull { (functionName, function) ->
            if (functionName.contains("add", true) || functionName.contains("save", true) || functionName.contains("list", true)) {
                return@mapNotNull null
            }
            val currentStatus = row["status"].orEmpty()
            val values = function.parameters.associateWith { parameter ->
                when {
                    parameter.equals("status", true) -> if (currentStatus == "1") "0" else "1"
                    parameter.equals("id", true) -> row[parameter] ?: row["id"] ?: row["channel"] ?: row["cid"]
                    parameter.equals("channel", true) || parameter.equals("cid", true) ->
                        row[parameter] ?: row["channel"] ?: row["id"]
                    else -> row[parameter]
                }.orEmpty()
            }
            val urlExpression = Regex("url\\s*:\\s*([^,}\\r\\n]+)").find(function.body)?.groupValues?.getOrNull(1)
                ?.trim()?.trimEnd(',') ?: return@mapNotNull null
            val endpoint = resolveExpression(urlExpression, values) ?: return@mapNotNull null
            if (!endpoint.contains(".php") || !endpoint.contains("act=")) return@mapNotNull null
            val method = Regex("type\\s*:\\s*['\"](GET|POST)['\"]", RegexOption.IGNORE_CASE)
                .find(function.body)?.groupValues?.getOrNull(1)?.uppercase() ?: "GET"
            val fields = DATA_FIELDS_REGEX.find(function.body)?.groupValues?.getOrNull(1)
                ?.split(',')?.mapNotNull { pair ->
                    val parts = pair.split(':', limit = 2)
                    if (parts.size != 2) null else resolveExpression(parts[1].trim(), values)
                        ?.let { parts[0].trim().trim('\'', '"') to it }
                }?.toMap().orEmpty()
            val status = Regex("(?:[?&]|^)status=([01])(?:&|$)").find(endpoint)?.groupValues?.getOrNull(1)
                ?: fields["status"]
            val label = ACTION_LABELS[functionName]
                ?.let { if (it == "修改状态" && status != null) if (status == "1") "开启" else "关闭" else it }
                ?: inferFunctionLabel(functionName, status)
                ?: return@mapNotNull null
            NativeAction(
                label = label, endpoint = endpoint, method = method, fields = fields,
                destructive = status == "0" || DESTRUCTIVE_WORDS.any { label.contains(it) },
            )
        }.distinctBy { it.label to it.endpoint }
    }

    fun parse(html: String, pagePath: String): NativeDocument {
        val document = Jsoup.parse(html)
        val scripts = document.select("script:not([src])").joinToString("\n") { it.data() }
        val functions = extractFunctions(scripts)

        val pageActions = document.select("a[onclick],button[onclick],a[href*='act=']")
            .filter { it.closest("table,form") == null }
            .mapNotNull { parseAction(it, functions) }
            .distinctBy { it.label to it.endpoint }

        document.select("nav,script,style,link,.navbar,.pagination").remove()

        val notices = document.select(".alert,.well,.help-block,.panel-body > p")
            .map { it.text().trim() }
            .filter { it.length in 2..300 }
            .distinct()

        val tables = document.select("table").mapIndexedNotNull { index, table ->
            val headers = table.select("thead th").map { it.text().trim() }.filter { it.isNotBlank() }
                .ifEmpty { table.select("tr").firstOrNull()?.select("th")?.map { it.text().trim() }.orEmpty() }
            val bodyRows = table.select("tbody tr")
            val rowElements: List<Element> = if (bodyRows.isNotEmpty()) bodyRows else table.select("tr").drop(1)
            val limitedRows = rowElements.take(100)
            val parsedRows = limitedRows.mapNotNull { row ->
                val cells = row.select("td").map { cleanCell(it) }
                if (cells.isEmpty()) null else cells to row.select("a[onclick],button[onclick],a[href*='act=']")
                    .mapNotNull { parseAction(it, functions) }
            }
            val rows = parsedRows.map { it.first }
            val rowActions = parsedRows.map { it.second }
            if (headers.isEmpty() && rows.isEmpty()) null
            else NativeTable(
                title = table.closest(".panel")?.selectFirst(".panel-title")?.text()?.trim()
                    ?: "数据表 ${index + 1}",
                headers = headers,
                rows = rows,
                rowActions = rowActions,
            )
        }

        val forms = document.select("form").mapIndexedNotNull { index, form ->
            val elements = form.select("input[name],select[name],textarea[name]")
            val fields = elements.groupBy { it.attr("name") }.mapNotNull { (_, sameName) ->
                parseField(preferredFieldElement(sameName), sameName)
            }
            if (fields.isEmpty()) null
            else {
                val inferred = inferFormSubmission(scripts, form.id())
                NativeForm(
                title = form.closest(".panel")?.selectFirst(".panel-title")?.text()?.trim()
                    ?: document.selectFirst("h1,h2,h3")?.text()?.trim()
                    ?: "配置 ${index + 1}",
                action = form.attr("action").ifBlank { inferred?.first ?: pagePath },
                method = form.attr("method").ifBlank { inferred?.second ?: "POST" }.uppercase(),
                fields = fields,
                )
            }
        }

        return NativeDocument(
            title = document.title().ifBlank { document.selectFirst("h1,h2,h3,.panel-title")?.text().orEmpty() },
            notices = notices,
            tables = tables,
            forms = forms,
            actions = pageActions,
        )
    }

    private data class ScriptFunction(val parameters: List<String>, val body: String)

    private fun extractFunctions(script: String): Map<String, ScriptFunction> {
        val header = Regex("function\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)\\s*\\{")
        val result = linkedMapOf<String, ScriptFunction>()
        header.findAll(script).forEach { match ->
            var depth = 1
            var index = match.range.last + 1
            var quote: Char? = null
            var escaped = false
            while (index < script.length && depth > 0) {
                val char = script[index]
                if (quote != null) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == quote) quote = null
                } else when (char) {
                    '\'', '"', '`' -> quote = char
                    '{' -> depth++
                    '}' -> depth--
                }
                index++
            }
            if (depth == 0) {
                val params = match.groupValues[2].split(',').map(String::trim).filter(String::isNotBlank)
                result[match.groupValues[1]] = ScriptFunction(params, script.substring(match.range.last + 1, index - 1))
            }
        }
        return result
    }

    private fun parseAction(element: Element, functions: Map<String, ScriptFunction>): NativeAction? {
        val href = element.attr("href").trim()
        if (element.attr("onclick").isBlank() && href.contains(".php") && href.contains("act=")) {
            val label = element.text().trim().ifBlank { "操作" }
            if (ACTION_WORDS.none { label.contains(it) }) return null
            return NativeAction(
                label = label.take(30), endpoint = href, method = "GET",
                destructive = DESTRUCTIVE_WORDS.any { label.contains(it) },
            )
        }
        val call = Regex("([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)").find(element.attr("onclick")) ?: return null
        val functionName = call.groupValues[1]
        val function = functions[functionName] ?: return null
        val arguments = splitArguments(call.groupValues[2])
        val values = function.parameters.mapIndexedNotNull { index, name -> arguments.getOrNull(index)?.let { name to it } }.toMap()
        val urlExpression = Regex("url\\s*:\\s*([^,}\\r\\n]+)").find(function.body)?.groupValues?.getOrNull(1)
            ?.trim()?.trimEnd(',') ?: return null
        val endpoint = resolveExpression(urlExpression, values) ?: return null
        if (!endpoint.contains(".php") || (!endpoint.contains("act=") && !endpoint.startsWith("ajax_"))) return null

        val method = Regex("type\\s*:\\s*['\"](GET|POST)['\"]", RegexOption.IGNORE_CASE)
            .find(function.body)?.groupValues?.getOrNull(1)?.uppercase() ?: "GET"
        val fields = DATA_FIELDS_REGEX.find(function.body)?.groupValues?.getOrNull(1)
            ?.split(',')?.mapNotNull { pair ->
                val parts = pair.split(':', limit = 2)
                if (parts.size != 2) null else {
                    val key = parts[0].trim().trim('\'', '"')
                    resolveExpression(parts[1].trim(), values)?.let { key to it }
                }
            }?.toMap().orEmpty()
        var label = element.text().trim().ifBlank { ACTION_LABELS[functionName] ?: functionName }
        val status = Regex("(?:[?&]|^)status=([01])(?:&|$)").find(endpoint)?.groupValues?.getOrNull(1)
            ?: fields["status"]
        if (status != null && (functionName.contains("status", true) || label == "修改状态")) {
            label = if (status == "1") "开启" else "关闭"
        }
        if (ACTION_WORDS.none { label.contains(it) }) label = inferFunctionLabel(functionName, status) ?: return null
        return NativeAction(
            label = label.take(30), endpoint = endpoint, method = method, fields = fields,
            destructive = status == "0" || DESTRUCTIVE_WORDS.any { label.contains(it) },
        )
    }

    private fun inferFormSubmission(script: String, formId: String): Pair<String, String>? {
        if (formId.isBlank()) return null
        val marker = Regex("data\\s*:\\s*\\$\\([\"']#$formId[\"']\\)\\.serialize\\(\\)")
        val match = marker.find(script) ?: return null
        val prefix = script.substring(maxOf(0, match.range.first - 900), match.range.first)
        val url = Regex("url\\s*:\\s*['\"]([^'\"]+)['\"]").findAll(prefix).lastOrNull()?.groupValues?.getOrNull(1)
            ?: return null
        val method = Regex("type\\s*:\\s*['\"](GET|POST)['\"]", RegexOption.IGNORE_CASE)
            .findAll(prefix).lastOrNull()?.groupValues?.getOrNull(1)?.uppercase() ?: "POST"
        return url to method
    }

    private fun splitArguments(value: String): List<String> = value.split(',').map { argument ->
        argument.trim().trim('\'', '"')
    }

    private fun resolveExpression(expression: String, values: Map<String, String>): String? {
        val parts = expression.split('+').map(String::trim).filter(String::isNotBlank)
        val resolved = buildString {
            for (part in parts) {
                when {
                    (part.startsWith("'") && part.endsWith("'")) || (part.startsWith("\"") && part.endsWith("\"")) ->
                        append(part.substring(1, part.length - 1))
                    part.startsWith("encodeURIComponent(") -> {
                        val name = part.substringAfter('(').substringBeforeLast(')').trim()
                        append(values[name] ?: return null)
                    }
                    values.containsKey(part) -> append(values.getValue(part))
                    part.matches(Regex("-?\\d+(?:\\.\\d+)?")) -> append(part)
                    else -> return null
                }
            }
        }
        return resolved.takeIf(String::isNotBlank)
    }

    private fun parseField(element: Element, sameName: List<Element>): NativeField? {
        val name = element.attr("name").trim()
        if (name.isBlank()) return null
        val syntheticOptions = linkedOptions(element)
        var type = when {
            syntheticOptions.isNotEmpty() -> "select"
            element.tagName() == "select" -> if (element.hasAttr("multiple")) "multiselect" else "select"
            element.tagName() == "textarea" -> "textarea"
            else -> element.attr("type").ifBlank { "text" }.lowercase()
        }
        if (type in setOf("submit", "button", "reset", "file")) return null
        val container = element.closest(".form-group,.input-group,td")
        val label = container?.selectFirst("label,.input-group-addon")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: element.attr("placeholder").trim().takeIf { it.isNotBlank() }
            ?: humanize(name)
        val sensitive = type == "password" || SENSITIVE_PARTS.any { name.lowercase().contains(it) }
        if (type == "radio") type = "select"
        if (type == "checkbox" && name.endsWith("[]")) type = "multiselect"
        val value = when {
            sensitive -> ""
            element.tagName() == "textarea" -> element.text()
            element.tagName() == "select" && element.hasAttr("multiple") -> element.select("option[selected]").joinToString(",") { it.attr("value") }
            element.tagName() == "select" && element.hasAttr("default") -> element.attr("default")
            element.tagName() == "select" -> element.selectFirst("option[selected]")?.attr("value")
                ?: element.selectFirst("option")?.attr("value").orEmpty()
            type == "multiselect" -> sameName.filter { it.hasAttr("checked") }.joinToString(",") { it.attr("value") }
            sameName.any { it.attr("type").equals("radio", true) } ->
                sameName.firstOrNull { it.hasAttr("checked") }?.attr("value") ?: sameName.firstOrNull()?.attr("value").orEmpty()
            type == "checkbox" -> if (element.hasAttr("checked")) element.attr("value").ifBlank { "1" } else "0"
            else -> element.attr("value")
        }
        val options = when {
            element.tagName() == "select" -> element.select("option").map { NativeOption(it.attr("value"), it.text().trim()) }
            syntheticOptions.isNotEmpty() -> syntheticOptions
            sameName.any { it.attr("type").equals("radio", true) } -> sameName.map { radio ->
                val optionLabel = radio.closest("label")?.text()?.trim()
                    ?: radio.parent()?.text()?.trim()
                    ?: radio.attr("value")
                NativeOption(radio.attr("value"), optionLabel)
            }
            type == "multiselect" -> sameName.map { NativeOption(it.attr("value"), it.closest("label")?.text()?.trim() ?: it.attr("value")) }
            else -> emptyList()
        }.let { options -> if (type == "select" && value.isNotBlank() && options.none { it.value == value }) options + NativeOption(value, "原始状态 ($value)") else options }
        return NativeField(
            name = name,
            label = label,
            value = value,
            type = type,
            options = options,
            sensitive = sensitive,
            required = element.hasAttr("required"),
            readOnly = element.hasAttr("readonly") || element.hasAttr("disabled"),
        )
    }

    /**
     * Bootstrap/Select2 页面常先放一个同名 hidden input，再放真正的 select。
     * 旧逻辑按 DOM 首项去重，导致支付插件在原生端被渲染成普通输入框。
     */
    private fun preferredFieldElement(elements: List<Element>): Element =
        elements.firstOrNull { it.tagName() == "select" }
            ?: elements.firstOrNull { it.attr("type").equals("radio", true) }
            ?: elements.firstOrNull { it.tagName() == "textarea" }
            ?: elements.firstOrNull { !it.attr("type").equals("hidden", true) }
            ?: elements.first()

    /** 支持 input[list]、无 name 的插件 select，以及由隐藏字段连接的 Select2 控件。 */
    private fun linkedOptions(element: Element): List<NativeOption> {
        val document = element.ownerDocument() ?: return emptyList()
        val listId = element.attr("list").trim()
        val direct = buildList {
            if (listId.isNotBlank()) add("datalist#${listId.replace(Regex("[^A-Za-z0-9_-]"), "")} option")
            if (element.attr("name").equals("plugin", true)) {
                add("select[name*=plugin] option")
                add("select[id*=plugin] option")
                add("datalist[id*=plugin] option")
            }
        }.flatMap { document.select(it) }
        val contextual = if (element.attr("name").equals("plugin", true)) {
            document.select("select").filter { select ->
                val signature = listOf(
                    select.attr("name"), select.id(), select.className(), select.attr("data-name"),
                    select.attr("data-placeholder"), select.closest(".form-group,.input-group,td,label")?.text().orEmpty(),
                ).joinToString(" ")
                signature.contains("plugin", true) || signature.contains("插件")
            }.flatMap { it.select("option") }
        } else emptyList()
        return (direct + contextual)
            .map { NativeOption(it.attr("value").ifBlank { it.text().trim() }, it.text().trim().ifBlank { it.attr("value") }) }
            .filter { it.value.isNotBlank() }
            .distinctBy { it.value }
    }

    private fun cleanCell(cell: Element): String = cell.clone().apply {
        select("script,style").remove()
    }.text().trim().take(500)

    private fun humanize(name: String): String = name
        .replace(Regex("\\[[^]]*]"), "")
        .replace('_', ' ')
        .trim()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun normalizeLabel(value: String): String = value.replace(Regex("\\s+"), "").trim()

    private fun inferFunctionLabel(functionName: String, status: String?): String? = when {
        functionName.contains("edit", true) -> "编辑"
        functionName.contains("add", true) -> "新增"
        functionName.contains("delete", true) || functionName.startsWith("del", true) -> "删除"
        functionName.contains("reset", true) -> "重置"
        functionName.contains("restore", true) || functionName.contains("recover", true) -> "恢复"
        functionName.contains("check", true) || functionName.contains("detect", true) || functionName.contains("monitor", true) -> "检测"
        functionName.contains("status", true) || functionName.startsWith("set", true) -> if (status == "0") "关闭" else "开启"
        else -> null
    }

    private val SENSITIVE_PARTS = setOf("pwd", "password", "secret", "token", "private", "certno", "appkey", "key")
    private val ACTION_WORDS = setOf(
        "新增", "添加", "编辑", "修改", "配置", "账户", "路由", "通道",
        "开启", "启用", "关闭", "停用", "禁用", "删除", "移除", "检测", "监测", "恢复", "重置", "清空", "刷新",
    )
    private val DESTRUCTIVE_WORDS = setOf("关闭", "停用", "删除", "移除", "重置", "清空", "禁用")
    private val ACTION_LABELS = mapOf(
        "delItem" to "删除", "setStatus" to "修改状态", "reset" to "重置", "check" to "检测",
        "editframe" to "编辑", "addframe" to "新增", "delProxy" to "删除", "setProxy" to "修改状态",
    )
}
