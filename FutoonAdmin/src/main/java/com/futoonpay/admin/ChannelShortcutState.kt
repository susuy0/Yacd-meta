package com.futoonpay.admin

import android.content.Context
import android.content.SharedPreferences
import com.futoonpay.admin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.json.JSONArray

internal data class ChannelScope(val account: String, val backend: String, val dialog: Long, val topic: Long) {
    val key: String get() = JSONObject().put("account", account).put("backend", backend).put("dialog", dialog.toString()).put("topic", topic.toString()).toString()
    val association: ChannelScope get() = if (dialog < 0) copy(topic = 0) else this
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
    /** A group owns a set of channels; v1 topic links are merged until the first explicit save. */
    fun channels(scope: ChannelScope): List<ChannelLink> {
        val key = scope.association
        val raw = prefs.getString("channels:" + key.key, null)
        return if (raw != null) runCatching { JSONArray(raw).objects().map { ChannelLink.parse(it) }.filter { validId(it.id) }.distinctBy { it.id } }.getOrDefault(emptyList())
        else links().filter { it.first.association == key }.map { it.second }.filter { validId(it.id) }.distinctBy { it.id }
    }
    fun replaceChannels(scope: ChannelScope, values: List<ChannelLink>) {
        require(values.all { validId(it.id) }) { "通道 ID 无效" }
        val key = scope.association
        val editor = prefs.edit().putString("channels:" + key.key, JSONArray(values.distinctBy { it.id }.map { it.json() }).toString())
        links().filter { it.first.association == key }.forEach { editor.remove("link:" + it.first.key) }
        check(editor.remove("hidden:" + scope.key).commit()) { "保存关联失败" }
    }
    fun associations(): List<Pair<ChannelScope, List<ChannelLink>>> {
        val scopes = links().map { it.first.association } + prefs.all.keys.filter { it.startsWith("channels:") }.mapNotNull { raw -> runCatching {
            val row = JSONObject(raw.removePrefix("channels:")); ChannelScope(row.getString("account"), row.getString("backend"), row.getLong("dialog"), row.getLong("topic"))
        }.getOrNull() }
        return scopes.distinct().map { it to channels(it) }.filter { it.second.isNotEmpty() }
    }
    fun hiddenScopes(): List<ChannelScope> = prefs.all.filter { it.key.startsWith("hidden:") && it.value == true }.mapNotNull { (key, _) -> runCatching {
        val row = JSONObject(key.removePrefix("hidden:")); ChannelScope(row.getString("account"), row.getString("backend"), row.getLong("dialog"), row.getLong("topic"))
    }.getOrNull() }
}
internal fun validId(value: String) = value.matches(Regex("[1-9][0-9]{0,18}")) && value.toLongOrNull() != null
internal data class ChannelShortcutState(val scope: ChannelScope? = null, val label: String = "", val link: ChannelLink? = null,
    val source: String = "已记住", val visible: Boolean = false, val loading: Boolean = false, val snapshot: ChannelSnapshot? = null,
    val error: String? = null, val invalid: Boolean = false, val window: MonitorWindow = MonitorWindow.FIVE_MINUTES,
    val channels: List<ChannelLink> = emptyList(), val telegramId: String = "", val identitySource: String = "",
    val merchant: MerchantSummary? = null, val merchantLoading: Boolean = false, val merchantError: String? = null,
    val merchantCandidates: List<MerchantSummary> = emptyList(),
    val snapshots: Map<String, ChannelSnapshot> = emptyMap(), val channelErrors: Map<String, String> = emptyMap()) {
    val targets: List<ChannelLink> get() = link?.let(::listOf) ?: channels
}

/** One controller per live chat; merchant identity and group channel associations are independent. */
internal class ChannelShortcutModel(context: Context, val bridge: NativePanelBridge) {
    val store = ChannelShortcutStore(context, bridge.accountScope())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(ChannelShortcutState())
    val state = mutable.asStateFlow()
    private val accountSession = bridge.session()
    private var generation = 0L
    private var request = 0L
    private var merchantRequest = 0L
    private var job: Job? = null
    private var merchantJob: Job? = null
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
    private fun valid(key: ChannelScope?): Boolean = !closed && active && key != null && state.value.scope == key && bridge.address() == key.backend && bridge.accountScope() == key.account && accountSession.isActive() && bridge.hasSession()
    fun current(key: ChannelScope?, token: Long): Boolean = valid(key) && generation == token
    fun bind(label: String, dialog: Long, topic: Long, telegramId: String = "", identitySource: String = "") {
        val key = ChannelScope(bridge.accountScope(), bridge.address(), dialog, topic)
        val identity = telegramId.takeIf(::validId).orEmpty()
        if (state.value.scope != key || state.value.telegramId != identity) {
            generation++; request++; merchantRequest++; job?.cancel(); merchantJob?.cancel()
            val channels = store.channels(key)
            mutable.value = ChannelShortcutState(key, label, channels.singleOrNull(), window = store.window, channels = channels, telegramId = identity, identitySource = identitySource)
        } else mutable.update { it.copy(label = label, identitySource = identitySource) }
        reloadSettings()
    }
    fun reloadSettings() {
        val key = state.value.scope ?: return
        val visible = active && store.enabled && !store.hidden(key) && key.backend.isNotBlank() && bridge.hasSession() && bridge.accountScope() == key.account && bridge.address() == key.backend && accountSession.isActive()
        val channels = store.channels(key)
        val changed = channels != state.value.channels || state.value.window != store.window
        val shown = visible && !state.value.visible
        val hidden = !visible && state.value.visible
        if (changed || hidden) { generation++; request++; job?.cancel() }
        if (hidden) { merchantRequest++; merchantJob?.cancel() }
        mutable.update { old ->
            val link = if (old.source == "已记住" && changed) channels.firstOrNull { it.id == old.link?.id } ?: channels.singleOrNull() else old.link
            old.copy(visible = visible, window = store.window, channels = channels, link = link,
                snapshot = if (changed || hidden) null else old.snapshot, snapshots = if (changed || hidden) emptyMap() else old.snapshots,
                channelErrors = if (changed || hidden) emptyMap() else old.channelErrors, error = if (changed || hidden) null else old.error,
                loading = if (changed || hidden) false else old.loading, invalid = if (changed) false else old.invalid,
                merchant = if (hidden) null else old.merchant, merchantCandidates = if (hidden) emptyList() else old.merchantCandidates, merchantLoading = if (hidden) false else old.merchantLoading)
        }
        if (visible && (changed || shown)) refresh()
        if (visible && (shown || state.value.merchant == null && state.value.telegramId.isNotBlank() && !state.value.merchantLoading && state.value.merchantError == null && state.value.merchantCandidates.isEmpty())) refreshMerchant()
    }
    fun resume() { if (closed) return; active = true; reloadSettings() }
    fun pause() { active = false; generation++; request++; merchantRequest++; job?.cancel(); merchantJob?.cancel(); mutable.update { it.copy(visible = false, loading = false, merchantLoading = false) } }
    fun close() { if (closed) return; closed = true; store.prefs.unregisterOnSharedPreferenceChangeListener(listener); scope.cancel() }
    fun associate(values: List<ChannelLink>) {
        val s = state.value; val key = s.scope ?: return
        check(valid(key)) { "账号、后台或会话已变化" }
        store.replaceChannels(key, values.map { it.copy(label = s.label) })
        generation++; request++; job?.cancel()
        mutable.update { it.copy(channels = store.channels(key), link = values.singleOrNull(), source = "已记住", snapshot = null, snapshots = emptyMap(), channelErrors = emptyMap(), error = null, invalid = false) }
        refresh()
    }
    fun select(link: ChannelLink, remember: Boolean, source: String = "仅本次") {
        require(validId(link.id)) { "通道 ID 无效" }
        val key = state.value.scope ?: return
        if (remember) { store.replaceChannels(key, listOf(link.copy(label = state.value.label))) }
        generation++; request++; job?.cancel()
        mutable.update { it.copy(link = link, source = if (remember) "已记住" else source, snapshot = null, snapshots = emptyMap(), error = null, invalid = false) }
        reloadSettings(); refresh()
    }
    fun selectAll() { if(state.value.link==null)return; generation++; request++; job?.cancel(); mutable.update { it.copy(link = null, source = "已记住", snapshot = null, snapshots = emptyMap(), error = null, invalid = false) } }
    fun fromOrder(channel: String) {
        if (!validId(channel) || state.value.scope?.let { store.channels(it).isNotEmpty() } == true) return
        select(ChannelLink(channel, "支付通道"), false, "来自订单")
    }
    fun restoreRemembered() { val key = state.value.scope ?: return; val channels = store.channels(key); if (channels.isNotEmpty()) { mutable.update { it.copy(link = channels.singleOrNull(), source = "已记住") }; reloadSettings(); refresh() } }
    fun hide() { state.value.scope?.let { store.hide(it, true) }; reloadSettings() }
    fun changeWindow(window: MonitorWindow) { store.window = window; refresh(all = state.value.link == null) }
    fun saved(name: String) {
        val s = state.value; val link = s.link ?: return; val next = link.copy(name = name.ifBlank { link.name })
        s.scope?.let { key -> val channels = store.channels(key); if (channels.any { it.id == link.id }) store.replaceChannels(key, channels.map { if (it.id == link.id) next else it }) }
        mutable.update { it.copy(link = next) }; refresh()
    }
    fun refreshMerchant() {
        val s = state.value; val key = s.scope ?: return
        if (!valid(key) || s.telegramId.isBlank()) return
        val seq = ++merchantRequest; merchantJob?.cancel()
        mutable.update { it.copy(merchantLoading = true, merchantError = null, merchant = null, merchantCandidates = emptyList()) }
        merchantJob = scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { bridge.call("merchant.lookup", JSONObject().put("column", "telegram_id").put("value", s.telegramId).toString()) }
                ensureActive()
                val data = JSONObject(raw); check(data.optJSONArray("rows") != null) { data.optString("msg", "后台未返回商户列表") }
                val rows = data.optJSONArray("rows").objects()
                check(rows.all { validId(it.optString("uid")) && it.optString("telegram_id") == s.telegramId }) { "商户 TGID 与当前会话不一致，请核对后台绑定" }
                val matches = rows.distinctBy { it.optString("uid") }.map { it.toMerchantSummary() }
                if (valid(key) && seq == merchantRequest && state.value.telegramId == s.telegramId) mutable.update { it.copy(merchant = matches.singleOrNull(), merchantCandidates = if (matches.size > 1) matches else emptyList(), merchantLoading = false, merchantError = if (matches.isEmpty()) "未找到对应商户" else null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid(key) && seq == merchantRequest) mutable.update { it.copy(merchantLoading = false, merchantError = e.message ?: "商户识别失败") } }
        }
    }
    fun chooseMerchant(uid: String) { state.value.merchantCandidates.firstOrNull { it.uid == uid }?.let { row -> mutable.update { it.copy(merchant = row, merchantCandidates = emptyList(), merchantError = null) } } }
    fun refresh(all: Boolean = false) {
        val s = state.value; val key = s.scope ?: return
        val targets = if (all) s.targets else listOfNotNull(s.link)
        if (!valid(key) || !s.visible || targets.isEmpty()) return
        val token = generation; val sequence = ++request; job?.cancel()
        mutable.update { it.copy(loading = true, error = null, snapshot = null, snapshots = emptyMap(), channelErrors = emptyMap()) }
        job = scope.launch {
            for (link in targets) {
                try {
                    val repo = repository()
                    val identity = repo.shortcutChannel(link.id)
                    val stats = repo.shortcutSnapshot(link.id, s.window)
                    ensureActive()
                    if (!current(key, token) || sequence != request) return@launch
                    mutable.update { old -> old.copy(link = if (old.link?.id == link.id) old.link?.copy(name = identity.name) else old.link, snapshot = if (s.link?.id == link.id) stats else old.snapshot, snapshots = old.snapshots + (link.id to stats), invalid = false) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (!current(key, token) || sequence != request) return@launch
                    mutable.update { it.copy(error = if (s.link != null) e.message ?: "读取失败" else null, channelErrors = it.channelErrors + (link.id to (e.message ?: "读取失败")), invalid = s.link != null && (e is ChannelUnavailableException || e is PermissionDeniedException || e is SessionExpiredException)) }
                }
            }
            if (current(key, token) && sequence == request) mutable.update { it.copy(loading = false) }
        }
    }
}
