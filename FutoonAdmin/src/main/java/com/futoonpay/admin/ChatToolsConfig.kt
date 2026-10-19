package com.futoonpay.admin

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatTag(val id: String = UUID.randomUUID().toString(), val text: String, val telegramIds: List<String>)
data class QuickReply(val id: String = UUID.randomUUID().toString(), val title: String, val body: String)
data class ReplyCategory(val id: String = UUID.randomUUID().toString(), val title: String, val replies: List<QuickReply> = emptyList())
data class ReplyTab(val id: String = UUID.randomUUID().toString(), val title: String, val categories: List<ReplyCategory> = emptyList())

/** Local chat preferences, independent of payment backend credentials and financial operations. */
data class ChatToolsConfig(val tags: List<ChatTag> = emptyList(), val tabs: List<ReplyTab> = emptyList(), val openingTemplate: String = defaultOpeningTemplate, val extensions: ExtensionOptions = ExtensionOptions()) {
    private val tagIndex by lazy { buildMap<String, MutableList<String>> { tags.forEach { tag -> tag.telegramIds.forEach { id -> getOrPut(id) { mutableListOf() }.add(tag.text) } } } }
    fun tagFor(telegramId: String): String = tagIndex[telegramId]?.distinct()?.joinToString(" · ").orEmpty()
    fun json(): String = JSONObject().put("version", 1).put("openingTemplate", openingTemplate).put("extensions", extensions.json())
        .put("tags", JSONArray(tags.map { tag -> JSONObject().put("id", tag.id).put("text", tag.text).put("telegramIds", JSONArray(tag.telegramIds)) }))
        .put("tabs", JSONArray(tabs.map { tab -> JSONObject().put("id", tab.id).put("title", tab.title).put("categories", JSONArray(tab.categories.map { category ->
            JSONObject().put("id", category.id).put("title", category.title).put("replies", JSONArray(category.replies.map { reply -> JSONObject().put("id", reply.id).put("title", reply.title).put("body", reply.body) }))
        })) })).toString()

    companion object {
        @JvmStatic fun canonicalId(raw: String): String {
            val value = raw.trim()
            require(value.matches(Regex("[0-9]{1,19}")) && value.toLongOrNull()?.let { it > 0 } == true) { "Telegram ID 必须是完整的正整数" }
            return value.toLong().toString()
        }
        @JvmStatic fun parseIds(raw: String): List<String> {
            val ids = raw.split(Regex("[,，;；\\s]+")).filter { it.isNotBlank() }.map(::canonicalId).distinct()
            require(ids.isNotEmpty()) { "请填写生效的 Telegram ID" }
            return ids
        }
        @JvmStatic fun parse(raw: String): ChatToolsConfig {
            if (raw.isBlank()) return ChatToolsConfig()
            val root = JSONObject(raw)
            require(root.optInt("version", 1) == 1) { "设置版本较新，请使用新版客户端" }
            fun rows(value: JSONArray?) = (0 until (value?.length() ?: 0)).map { value!!.getJSONObject(it) }
            fun id(row: JSONObject) = row.getString("id").also { require(it.isNotBlank()) }
            fun title(row: JSONObject, key: String) = row.getString(key).trim().also { require(it.isNotEmpty()) }
            val tags = rows(root.optJSONArray("tags")).map { row ->
                val ids = row.getJSONArray("telegramIds")
                ChatTag(id(row), title(row, "text"), (0 until ids.length()).map { canonicalId(ids.getString(it)) }.distinct())
            }
            val tabs = rows(root.optJSONArray("tabs")).map { tab -> ReplyTab(id(tab), title(tab, "title"), rows(tab.optJSONArray("categories")).map { category ->
                ReplyCategory(id(category), title(category, "title"), rows(category.optJSONArray("replies")).map { reply -> QuickReply(id(reply), title(reply, "title"), reply.getString("body").also { require(it.isNotBlank()) }) })
            }) }
            val ids = tags.map { it.id } + tabs.flatMap { listOf(it.id) + it.categories.flatMap { c -> listOf(c.id) + c.replies.map { r -> r.id } } }
            require(ids.distinct().size == ids.size) { "设置中存在重复项目标识" }
            val openingTemplate = root.optString("openingTemplate", defaultOpeningTemplate)
            validateOpeningTemplate(openingTemplate)
            return ChatToolsConfig(tags, tabs, openingTemplate, ExtensionOptions.parse(root.optJSONObject("extensions")))
        }
    }
}
