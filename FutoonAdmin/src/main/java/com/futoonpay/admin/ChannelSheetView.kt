package com.futoonpay.admin

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.*
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** Independently owns the AndroidX tree in Telegram's ownerless BottomSheet window. */
class ChannelSheetView internal constructor(context: Context, internal val model: ChannelShortcutModel, private val kind: String, private val host: ChannelSheetHost) : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val compose = ComposeView(context)
    private val composition = PanelComposition(compose, registry)
    private var palette by mutableStateOf(FutoonPalette())
    private var closed = false
    private var panelHeight = 0
    private var allowClose = false
    internal var dirty by mutableStateOf(false)
    internal var busy by mutableStateOf(false)
    internal var discard by mutableStateOf(false)
    internal var discardAction: () -> Unit = {}
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry get() = saved.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher { requestDismiss() }
    init {
        saved.performAttach(); saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED
        setViewTreeLifecycleOwner(this); setViewTreeViewModelStoreOwner(this); setViewTreeSavedStateRegistryOwner(this)
        compose.id = android.view.View.generateViewId(); compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides this) { FutoonTheme(palette) { PanelOverlayHost {
            ShortcutSheet(this, kind) { requestDismiss() }
            if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") }, text = { Text("已保存的部分不会撤销；其余修改和本机表单草稿将被清除。") },
                confirmButton = { TextButton(onClick = { discardAction(); dirty = false; discard = false; allowClose = true; host.closeSheet() }) { Text("放弃修改", color = Danger) } },
                dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
        } } } }
        addView(compose, LayoutParams(-1,-1))
    }
    fun setThemeColors(json: String) { if (!closed) { palette = FutoonPalette.parse(json); setBackgroundColor(palette.surface.toArgb()) } }
    fun setPanelHeight(pixels: Int) { panelHeight = pixels; requestLayout() }
    fun onShown() { if (!closed) registry.currentState = Lifecycle.State.RESUMED }
    fun onHidden() { if (!closed) registry.currentState = Lifecycle.State.CREATED }
    fun handleBack(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    fun requestClose(): Boolean {
        if (closed || allowClose) return true
        if (busy) { android.widget.Toast.makeText(context,"正在保存，请稍候",android.widget.Toast.LENGTH_SHORT).show(); return false }
        if (dirty) { discard = true; return false }
        return true
    }
    fun forceClose() { allowClose = true }
    internal fun requestDismiss() { if (requestClose()) host.closeSheet() }
    internal fun dismissAfterSave() { dirty = false; allowClose = true; host.closeSheet() }
    internal fun openPanel(kind: String, id: String = "", order: String = "") {
        val key = model.state.value.scope ?: return
        allowClose = true; host.closeSheet()
        model.bridge.call("ui.channel.open", JSONObject().put("kind",kind).put("id",id).put("order",order).put("dialog",key.dialog).put("topic",key.topic).toString())
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!closed) composition.prepare() }
    override fun onMeasure(w: Int, h: Int) { if (!closed) composition.prepare(); super.onMeasure(w, if (panelHeight > 0) MeasureSpec.makeMeasureSpec(minOf(panelHeight, if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) panelHeight else MeasureSpec.getSize(h)),MeasureSpec.EXACTLY) else h) }
    fun close() { if (closed) return; closed = true; registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear(); composition.close() }
}

@Composable private fun ShortcutSheet(view: ChannelSheetView, initial: String, close: () -> Unit) {
    val model = view.model
    val state by model.state.collectAsState()
    val link = state.link
    Column(Modifier.fillMaxSize().background(CardColor).imePadding()) {
        if (initial != "edit") {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when(initial) { "orders" -> "通道订单"; "stats" -> "成功率"; else -> "选择支付通道" }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (link != null) Text("${link.name} #${link.id}", color = Muted, fontSize = 13.sp)
                }
                TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text("关闭") }
            }
            HorizontalDivider(color = Line)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                initial == "select" -> ChannelSelector(view, close)
                link == null -> Text("请先选择支付通道", Modifier.padding(16.dp))
                initial == "stats" -> ChannelStatistics(model)
                initial == "orders" -> ShortcutOrders(view, link)
                initial == "edit" -> ShortcutEditor(view, link)
            }
        }
    }
}

@Composable private fun ChannelSelector(view: ChannelSheetView, close: () -> Unit) {
    val model = view.model
    val state by model.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf<List<ConfigurationChannel>>(emptyList()) }
    var selected by remember { mutableStateOf(state.link?.id.orEmpty()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(retry) {
        loading = true; error = null
        try { choices = model.repository().configurationChannels() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "读取通道失败" }
        finally { if (currentCoroutineContext().isActive) loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        Text("关联范围：${state.label}\n${state.scope?.account} · ${state.scope?.backend}\n话题 ${state.scope?.topic ?: 0}", Modifier.padding(16.dp), color = Muted, fontSize = 12.sp)
        OutlinedTextField(query,{query=it},placeholder={Text("搜索通道名称或 ID")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if (loading) item { CircularProgressIndicator() }
            error?.let { item { Text(it,color=Danger); TextButton(onClick={retry++}){Text("重试")} } }
            if (state.source != "已记住" && state.scope?.let(model.store::link) != null) item {
                TextButton(onClick={model.restoreRemembered();close()}) { Text("恢复为已记住的通道") }
            }
            val rows=choices.filter { query.isBlank() || it.id.contains(query,true) || it.name.contains(query,true) }
            items(rows,key={it.id}) { row ->
                Row(Modifier.fillMaxWidth().heightIn(min=62.dp).background(if(selected==row.id) MaterialTheme.colorScheme.primaryContainer else CardColor).clickable{selected=row.id}.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected==row.id,{selected=row.id})
                    Column { Text(row.name,fontSize=15.sp); Text("#${row.id} · ${row.typeId}",color=Muted,fontSize=12.sp) }
                }
            }
            if(!loading && error==null && rows.isEmpty()) item{Text("没有匹配的通道",color=Muted)}
            failure?.let{item{Text(it,color=Danger)}}
        }
        Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            fun choose(remember:Boolean) { choices.firstOrNull{it.id==selected}?.let { row ->
                try { model.select(ChannelLink(row.id,row.name),remember);close() } catch(e:Exception){failure=e.message?:"保存关联失败"}
            } }
            OutlinedButton(onClick={choose(false)},enabled=!loading&&choices.any{it.id==selected},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("仅本次使用")}
            Button(onClick={choose(true)},enabled=!loading&&choices.any{it.id==selected},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("记住此会话")}
        }
    }
}

@Composable private fun ChannelStatistics(model: ChannelShortcutModel) {
    val state by model.state.collectAsState()
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model,lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh(); while(isActive){delay(30_000);model.refresh()} } }
    Column(Modifier.fillMaxSize()) {
        FlowWindows(state.window,model::changeWindow)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(state.loading) item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let{item{Text("读取失败：$it",color=Danger)}}
            val data=state.snapshot
            item{Text(data?.rateText?:"—",fontSize=36.sp,fontWeight=FontWeight.SemiBold);Text("近${state.window.label} · 官方通道监测",color=Muted)}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("总订单 ${data?.total?:"—"}");Text("成功 ${data?.successful?:"—"}");Text("处理中 ${data?.processing?:"—"}")}}
            if(data?.total?.let{it in 1..9}==true)item{Text("样本少：${data.total} 笔，谨慎判断",color=Warning)}
            item{Text("成功率趋势",fontWeight=FontWeight.Medium);Text("后台尚未提供此时间窗的完整趋势数据",color=Muted)}
            item{Text("最近订单",fontWeight=FontWeight.Medium);Text("请从通道订单查看；不以当前页样本代替整个时间窗。",color=Muted)}
            item{Text(data?.updated?.let{"更新于 "+java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(it))}?:"缺失字段显示 —；未以 0% 或 100% 代替。",color=Muted,fontSize=12.sp)}
        }
        TextButton(onClick=model::refresh,enabled=!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("刷新统计 · 面板内每30秒更新")}
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FlowWindows(window:MonitorWindow,onChange:(MonitorWindow)->Unit){FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(MonitorWindow.FIVE_MINUTES,MonitorWindow.HALF_HOUR,MonitorWindow.HOUR,MonitorWindow.SIX_HOURS,MonitorWindow.DAY).forEach{item->FilterChip(selected=window==item,onClick={onChange(item)},label={Text(item.label)},modifier=Modifier.heightIn(min=48.dp))}}}

@Composable private fun ShortcutOrders(view:ChannelSheetView,link:ChannelLink){
    val environment=remember{EmbeddedEnvironment(view.model.bridge)}
    val vm:AppViewModel=viewModel(factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel> create(c:Class<T>):T=AppViewModel(environment,loadOnStart=false,lockedChannelId=link.id) as T})
    val state by vm.state.collectAsState()
    LaunchedEffect(link.id){vm.loadOrders(1,OrderSearchFilters(channelId=link.id))}
    val nav=remember{object:PanelNavigation{
        override fun orders(channelId:String,merchantId:String){}
        override fun settlements(merchantId:String){view.openPanel("merchant",merchantId)}
        override fun merchants(merchantId:String){view.openPanel("merchant",merchantId)}
        override fun domains(merchantId:String){view.openPanel("merchant",merchantId)}
        override fun module(id:String,channelId:String,channelType:String){view.openPanel("channel",channelId)}
        override fun evidence(order:OrderSummary){val raw=JSONObject().put("trade_no",order.tradeNo).put("out_trade_no",order.merchantTradeNo).put("uid",order.merchantId).put("money",order.amount).put("currency",order.currency);view.openPanel("evidence",order=raw.toString())}
    }}
    CompositionLocalProvider(LocalPanelNavigation provides nav){OrdersScreen(state,vm)}
}

@Composable private fun ShortcutEditor(view:ChannelSheetView,link:ChannelLink){
    val repo=remember{view.model.repository()}
    val drafts=remember{FormDraftStore(view.context,view.model.bridge.accountScope())}
    val vm:PaymentAdminViewModel=viewModel(factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel> create(c:Class<T>):T=PaymentAdminViewModel(repo,PaymentAdminCatalog.find("channels"),drafts=drafts,loadOnStart=false) as T})
    val state by vm.state.collectAsState()
    var loaded by remember{mutableStateOf(false)}
    var baseline by remember{mutableStateOf<Map<String,String>?>(null)}
    var submitted by remember{mutableStateOf(false)}
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(vm){vm.openShortcutEditor(link.id)}
    LaunchedEffect(state.editor,state.accountLoading,state.pluginLoading){
        val form=state.editor
        if(form!=null&&!state.accountLoading&&!state.pluginLoading&&baseline==null){baseline=form.fields.associate{it.name to it.value};loaded=true}
        if(form!=null) view.dirty=baseline?.let{old->form.fields.any{it.type!="hidden"&&it.value!=old[it.name].orEmpty()}}?:false
        if(loaded&&submitted&&form==null&&!state.writeUncertain){view.model.saved(link.name);view.dismissAfterSave()}
    }
    SideEffect{view.busy=submitted&&state.loading;view.discardAction={state.editor?.let{drafts.remove(repo.backendAddress,it)};vm.closeEditor()}}
    LaunchedEffect(state.primarySaved){if(state.primarySaved){state.editor?.fields?.firstOrNull{it.name=="name"}?.value?.let(view.model::saved)}}
    Column(Modifier.fillMaxSize()){
        Text("${link.name} #${link.id}",Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),fontSize=13.sp,color=Muted)
        if(state.primarySaved)Text("基本信息已保存；只重试账户配置",Modifier.padding(horizontal=16.dp),color=Warning)
        if(state.writeUncertain)Text("保存结果未知，请先核对后台配置，勿重复提交。密钥状态无法从掩码判断。",Modifier.padding(16.dp),color=Warning)
        state.message?.let{Text(it,Modifier.padding(horizontal=16.dp),color=Danger)}
        val form=state.editor
        when{
            form!=null->Box(Modifier.weight(1f)){PaymentEditorScreen(form.copy(fields=form.fields.map{if(state.loading||(state.primarySaved&&!it.name.startsWith("account::")))it.copy(readOnly=true)else it}),state.loading||state.pluginLoading||state.accountLoading,{view.requestDismiss()},vm::updateEditorField,{f,v->submitted=true;vm.submit(f,v)}, {},snackbar,state.routeChannels,state.routeStats,vm::appendRoute,uncertain=state.writeUncertain)}
            state.loading->Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            else->Column(Modifier.padding(16.dp)){Text(state.loadError?:"无法读取通道编辑表单",color=Danger);TextButton(onClick={vm.openShortcutEditor(link.id)}){Text("重试")};TextButton(onClick={view.requestDismiss()}){Text("关闭")}}
        }
    }
}
