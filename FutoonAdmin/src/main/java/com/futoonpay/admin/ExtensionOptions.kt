package com.futoonpay.admin

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val panelTabIds = listOf("recognition", "orders", "merchants", "settlements", "more")
val panelTabNames = mapOf("recognition" to "识别", "orders" to "订单", "merchants" to "商户", "settlements" to "结算", "more" to "更多")

data class PanelOptions(
    val height: Int = 85, val defaultTab: String = "orders", val tabOrder: List<String> = panelTabIds,
    val remember: Boolean = true, val full: Boolean = false,
    val image: String = "", val entrySize: Int = 40, val entryRadius: Int = 20, val entryOpacity: Int = 100,
)
data class AppearanceOptions(val theme: String = "telegram", val accent: String = "", val font: Int = 100,
    val radius: Int = 18, val opacity: Int = 100, val dim: Int = 0)
data class BusinessReplyTemplate(val id: String = UUID.randomUUID().toString(), val title: String, val body: String)
data class GroupAutomationRule(
    val id: String = UUID.randomUUID().toString(), val title: String = "补单案例", val dialog: Long,
    val groupName: String = "", val topic: Long = 0, val sender: Long, val keyword: String = "补单案例",
    val button: String = "补单案例", val action: String = "click", val draft: String = "",
    val enabled: Boolean = false, val since: Long = 0, val cooldown: Int = 5,
) {
    fun acceptsEvent(dialog: Long, topic: Long, sender: Long, text: String, messageDate: Long, now: Long): Boolean =
        enabled && this.dialog == dialog && dialog < 0 && (this.topic == 0L || this.topic == topic) && this.sender == sender &&
            messageDate >= since && messageDate <= now + 60 && now - messageDate <= 300 &&
            (keyword.isBlank() || text.contains(keyword, ignoreCase = true))
}

data class ExtensionOptions(val panel: PanelOptions = PanelOptions(), val appearance: AppearanceOptions = AppearanceOptions(),
    val templates: List<BusinessReplyTemplate> = defaultBusinessTemplates, val rules: List<GroupAutomationRule> = emptyList()) {
    fun json(): JSONObject = JSONObject().put("panel", JSONObject().put("height", panel.height).put("defaultTab", panel.defaultTab)
        .put("tabOrder", JSONArray(panel.tabOrder)).put("remember", panel.remember).put("full", panel.full).put("image", panel.image)
        .put("entrySize", panel.entrySize).put("entryRadius", panel.entryRadius).put("entryOpacity", panel.entryOpacity))
        .put("appearance", JSONObject().put("theme", appearance.theme).put("accent", appearance.accent).put("font", appearance.font)
            .put("radius", appearance.radius).put("opacity", appearance.opacity).put("dim", appearance.dim))
        .put("templates", JSONArray(templates.map { JSONObject().put("id", it.id).put("title", it.title).put("body", it.body) }))
        .put("rules", JSONArray(rules.map { JSONObject().put("id", it.id).put("title", it.title).put("dialog", it.dialog.toString())
            .put("groupName", it.groupName).put("topic", it.topic.toString()).put("sender", it.sender.toString()).put("keyword", it.keyword)
            .put("button", it.button).put("action", it.action).put("draft", it.draft).put("enabled", it.enabled).put("since", it.since).put("cooldown", it.cooldown) }))
    companion object {
        @JvmStatic fun parse(value: JSONObject?): ExtensionOptions {
            if (value == null) return ExtensionOptions()
            val p = value.optJSONObject("panel") ?: JSONObject()
            val a = value.optJSONObject("appearance") ?: JSONObject()
            val order = p.optJSONArray("tabOrder")?.let { r -> (0 until r.length()).map { r.getString(it) } } ?: panelTabIds
            require(order.size == 5 && order.toSet() == panelTabIds.toSet()) { "业务 Tab 必须完整且不能重复" }
            val panel = PanelOptions(p.optInt("height", 85), p.optString("defaultTab", "orders"), order, p.optBoolean("remember", true), p.optBoolean("full"),
                p.optString("image"), p.optInt("entrySize", 40), p.optInt("entryRadius", 20), p.optInt("entryOpacity", 100))
            require(panel.height in 50..100 && panel.defaultTab in panelTabIds && panel.entrySize in 40..96 && panel.entryRadius in 0..48 && panel.entryOpacity in 25..100) { "悬浮窗尺寸或透明度不正确" }
            require(panel.image.length <= 1024) { "图片路径过长" }
            val appearance = AppearanceOptions(a.optString("theme", "telegram"), a.optString("accent"), a.optInt("font", 100), a.optInt("radius", 18), a.optInt("opacity", 100), a.optInt("dim"))
            require(appearance.theme in listOf("telegram", "light", "dark") && (appearance.accent.isBlank() || appearance.accent.matches(Regex("#[0-9a-fA-F]{6}"))) &&
                appearance.font in 80..150 && appearance.radius in 0..36 && appearance.opacity in 50..100 && appearance.dim in 0..60) { "外观设置不正确" }
            val templates = value.optJSONArray("templates")?.let { r -> (0 until r.length()).map { i -> val t = r.getJSONObject(i)
                BusinessReplyTemplate(t.getString("id"), t.getString("title"), t.getString("body")).also { validateBusinessTemplate(it) }
            } } ?: defaultBusinessTemplates
            require(templates.size <= 100 && templates.map { it.id }.distinct().size == templates.size) { "回复模板过多或标识重复" }
            val rules = value.optJSONArray("rules")?.let { r -> (0 until r.length()).map { i -> val t = r.getJSONObject(i)
                GroupAutomationRule(t.getString("id"), t.optString("title", "群规则"), t.getString("dialog").toLong(), t.optString("groupName"), t.optLong("topic"), t.getString("sender").toLong(),
                    t.optString("keyword"), t.optString("button"), t.optString("action", "click"), t.optString("draft"), t.optBoolean("enabled"), t.optLong("since"), t.optInt("cooldown", 5)).also { rule ->
                    require(rule.id.isNotBlank() && rule.title.isNotBlank() && rule.title.length <= 80 && rule.groupName.length <= 120 && rule.dialog < 0 && rule.topic >= 0 && rule.sender > 0 && rule.cooldown in 2..3600 && rule.since >= 0) { "请填写有效的群、发送者和规则名称" }
                    require(rule.keyword.length <= 300 && rule.button.length <= 100 && rule.draft.length <= 4096 && rule.action in listOf("click", "draft")) { "自动化规则内容不正确" }
                    require(if (rule.action == "click") rule.button.isNotBlank() else rule.draft.isNotBlank() && rule.keyword.isNotBlank()) { "点击动作需要按钮全名；草稿动作需要关键词和文案" }
                }
            } } ?: emptyList()
            require(rules.size <= 100 && rules.map { it.id }.distinct().size == rules.size) { "自动化规则过多或标识重复" }
            return ExtensionOptions(panel, appearance, templates, rules)
        }
    }
}

val businessReplyVariables = listOf("系统订单号", "商户订单号", "上游订单号", "商户ID", "金额", "币种", "订单状态", "创建时间", "完成时间", "支付方式", "通道ID", "商户名称", "TelegramID", "会话名称")
val defaultBusinessTemplates = listOf(
    BusinessReplyTemplate("order-status", "订单查询结果", "订单查询结果\n系统订单号：{系统订单号}\n商户订单号：{商户订单号}\n金额：{金额} {币种}\n当前状态：{订单状态}"),
    BusinessReplyTemplate("payment-proof", "索要付款凭证", "请提供订单 {系统订单号}（{金额} {币种}）的完整付款凭证，以便进一步核实。"),
    BusinessReplyTemplate("supplement", "补单协助", "请协助核实补单\n系统订单号：{系统订单号}\n商户订单号：{商户订单号}\n金额：{金额} {币种}\n当前状态：{订单状态}"),
    BusinessReplyTemplate("callback", "回调异常", "请协助核实订单 {系统订单号} 的回调情况。商户订单号：{商户订单号}，当前状态：{订单状态}。"),
    BusinessReplyTemplate("refund", "退款核实", "请协助核实订单 {系统订单号} 的退款进度，金额：{金额} {币种}，当前状态：{订单状态}。"),
    BusinessReplyTemplate("merchant", "商户信息", "商户ID：{商户ID}\n商户名称：{商户名称}\nTelegram ID：{TelegramID}"),
)
fun validateBusinessTemplate(template: BusinessReplyTemplate) {
    require(template.id.isNotBlank() && template.title.trim().length in 1..80 && template.body.isNotBlank() && template.body.length <= 4096) { "请填写模板名称和 4096 字以内的正文" }
    val unknown = Regex("\\{([^{}\\n]+)\\}").findAll(template.body).map { it.groupValues[1] }.filterNot { it in businessReplyVariables }.distinct().toList()
    require(unknown.isEmpty()) { "未知变量：${unknown.joinToString()}" }
}
fun renderBusinessReply(template: String, values: Map<String, String>): String =
    Regex("\\{([^{}\\n]+)\\}").replace(template) { values[it.groupValues[1]]?.ifBlank { "未提供" } ?: "未提供" }
