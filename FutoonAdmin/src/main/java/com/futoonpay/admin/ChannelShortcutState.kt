package com.futoonpay.admin

import android.content.Context
import android.content.SharedPreferences
import com.futoonpay.admin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

internal data class ChannelScope(val account: String, val backend: String, val dialog: Long, val topic: Long) {
    val key: String get() = JSONObject().put("account", account).put("backend", backend).put("dialog", dialog.toString()).put("topic", topic.toString()).toString()
}
internal data class ChannelLink(val id: String, val name: String, val label: String = "", val time: Long = System.currentTimeMillis()) {
    fun json() = JSONObject().put("id", id).put("name", name).put("label", label).put("time", time)
    companion object { fun parse(value: JSONObject) = ChannelLink(value.getString("id"), value.optString("name"), value.optString("label"), value.optLong("time")) }
}
internal class ChannelShortcutStore(context: Context, account: String) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("futoon_channel_" + account.substringAfterLast(':'), Context.MODE_PRIVATE)
    var enabled: Boolean get() = prefs.getBoolean("enabled", true); set(v) { check(prefs.edit().putBoolean("enabled", v).commit()) }
    var window: MonitorWindow get() = MonitorWindow.entries.firstOrNull { it.key == prefs.getString("window", "5min") } ?: MonitorWindow.FIVE_MINUTES
        set(v) { check(prefs.edit().putString("window", v.key).commit()) }
    fun hidden(scope: ChannelScope) = prefs.getBoolean("hidden:" + scope.key, false)
    fun hide(scope: ChannelScope, hidden: Boolean) { check(prefs.edit().putBoolean("hidden:" + scope.key, hidden).commit()) }
    fun link(scope: ChannelScope): ChannelLink? = prefs.getString("link:" + scope.key, null)?.let { runCatching { ChannelLink.parse(JSONObject(it)) }.getOrNull() }
    fun remember(scope: ChannelScope, value: ChannelLink) { check(prefs.edit().putString("link:" + scope.key, value.json().toString()).remove("hidden:" + scope.key).commit()) }
    fun remove(scope: ChannelScope) { check(prefs.edit().remove("link:" + scope.key).commit()) }
    fun links(): List<Pair<ChannelScope, ChannelLink>> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith("link:") || value !is String) null else runCatching {
            val row = JSONObject(key.removePrefix("link:")); ChannelScope(row.getString("account"), row.getString("backend"), row.getLong("dialog"), row.getLong("topic")) to ChannelLink.parse(JSONObject(value))
        }.getOrNull()
    }
    fun hiddenScopes(): List<ChannelScope> = prefs.all.filter { it.key.startsWith("hidden:") && it.value == true }.mapNotNull { (key, _) -> runCatching {
        val row = JSONObject(key.removePrefix("hidden:")); ChannelScope(row.getString("account"), row.getString("backend"), row.getLong("dialog"), row.getLong("topic"))
    }.getOrNull() }
}
internal data class ChannelShortcutState(val scope: ChannelScope? = null, val label: String = "", val link: ChannelLink? = null,
    val source: String = "已记住", val visible: Boolean = false, val loading: Boolean = false, val snapshot: ChannelSnapshot? = null,
    val error: String? = null, val invalid: Boolean = false, val window: MonitorWindow = MonitorWindow.FIVE_MINUTES)

/** One controller per live ChatActivity. Temporary selections never survive leaving the chat. */
internal class ChannelShortcutModel(context: Context, val bridge: NativePanelBridge) {
    val store = ChannelShortcutStore(context, bridge.accountScope())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(ChannelShortcutState())
    val state = mutable.asStateFlow()
    private val accountSession = bridge.session()
    private var generation = 0L
    private var request = 0L
    private var job: Job? = null
    private var active = false
    private var closed = false
    private var environment: EmbeddedEnvironment? = null
    private var environmentAddress = ""
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> if (!closed) scope.launch { reloadSettings() } }
    init { store.prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun repository(): FutoonRepository {
        val address = bridge.address()
        if (environment == null || address != environmentAddress) { environment = EmbeddedEnvironment(bridge); environmentAddress = address }
        return environment!!.repository
    }
    fun current(key: ChannelScope?, token: Long): Boolean = !closed && active && key != null && state.value.scope == key && generation == token && bridge.address() == key.backend && bridge.accountScope() == key.account && accountSession.isActive()
    fun bind(label: String, dialog: Long, topic: Long) {
        val key = ChannelScope(bridge.accountScope(), bridge.address(), dialog, topic)
        if (state.value.scope != key) {
            generation++; request++; job?.cancel()
            mutable.value = ChannelShortcutState(key, label, store.link(key), window = store.window)
            reloadSettings(); if (active) refresh()
        } else { mutable.update { it.copy(label = label) }; reloadSettings() }
    }
    fun reloadSettings() {
        val key = state.value.scope ?: return
        val visible = active && store.enabled && !store.hidden(key) && key.backend.isNotBlank() && bridge.hasSession() && bridge.accountScope() == key.account && bridge.address() == key.backend
        val changedWindow = state.value.window != store.window
        val nextLink = if (state.value.source == "已记住") store.link(key) else state.value.link
        val changedLink = state.value.link?.id != nextLink?.id
        if (changedWindow || changedLink) { generation++; request++; job?.cancel() }
        mutable.update { old -> old.copy(visible = visible, window = store.window, link = nextLink, snapshot = if (changedWindow || changedLink) null else old.snapshot, error = if (changedWindow || changedLink) null else old.error, loading = if (changedWindow || changedLink) false else old.loading) }
        if (visible && (changedWindow || changedLink)) refresh()
    }
    fun resume() { if (closed) return; active = true; reloadSettings(); if (state.value.snapshot == null && state.value.link != null) refresh() }
    fun pause() { active = false; generation++; request++; job?.cancel(); mutable.update { it.copy(loading = false) } }
    fun close() { if (closed) return; closed = true; store.prefs.unregisterOnSharedPreferenceChangeListener(listener); scope.cancel() }
    fun select(link: ChannelLink, remember: Boolean, source: String = "仅本次") {
        val key = state.value.scope ?: return
        generation++; request++; job?.cancel()
        if (remember) store.remember(key, link.copy(label = state.value.label))
        mutable.update { it.copy(link = link, source = if (remember) "已记住" else source, snapshot = null, error = null, invalid = false) }
        reloadSettings(); refresh()
    }
    fun fromOrder(channel: String) {
        if (!channel.matches(Regex("[1-9][0-9]*"))) return
        // A persisted association takes priority; looking at another order cannot silently replace it.
        if (state.value.scope?.let(store::link) != null) return
        select(ChannelLink(channel, "支付通道"), false, "来自订单")
    }
    fun restoreRemembered() { val key = state.value.scope ?: return; store.link(key)?.let { select(it, false, "已记住") } }
    fun hide() { state.value.scope?.let { store.hide(it, true) }; reloadSettings() }
    fun changeWindow(window: MonitorWindow) { store.window = window; mutable.update { it.copy(window = window, snapshot = null) }; refresh() }
    fun saved(name: String) { val s = state.value; val link = s.link ?: return; val next = link.copy(name = name.ifBlank { link.name }); if (s.source == "已记住") s.scope?.let { store.remember(it, next) }; mutable.update { it.copy(link = next) }; refresh() }
    fun refresh() {
        val s = state.value; val key = s.scope ?: return; val link = s.link ?: return
        if (!active || !s.visible || closed) return
        val token = generation; val sequence = ++request; job?.cancel()
        mutable.update { it.copy(loading = true, error = null, snapshot = null) }
        job = scope.launch {
            try {
                val repo = repository()
                val identity = repo.shortcutChannel(link.id)
                ensureActive()
                if (!current(key, token) || sequence != request) return@launch
                mutable.update { it.copy(link = link.copy(name = identity.name), invalid = false) }
                val stats = repo.shortcutSnapshot(link.id, s.window)
                ensureActive()
                if (current(key, token) && sequence == request) mutable.update { it.copy(snapshot = stats, loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current(key, token) && sequence == request) mutable.update { it.copy(loading = false, error = e.message ?: "读取失败", invalid = e is ChannelUnavailableException || e is PermissionDeniedException || e is SessionExpiredException) }
            }
        }
    }
}
