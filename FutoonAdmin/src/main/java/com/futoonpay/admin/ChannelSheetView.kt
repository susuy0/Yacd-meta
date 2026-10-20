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
import androidx.compose.foundation.lazy.LazyRow
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

internal val LocalChannelMutationBusy = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }

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
        if (busy) { android.widget.Toast.makeText(context,"正在处理，请稍候",android.widget.Toast.LENGTH_SHORT).show(); return false }
        if (dirty) { discard = true; return false }
        return true
    }
    fun forceClose() { allowClose = true }
    internal fun requestDismiss() { if (requestClose()) host.closeSheet() }
    internal fun dismissAfterSave() { dirty = false; allowClose = true; host.closeSheet() }
    internal fun openPanel(kind: String, id: String = "", order: String = "") {
        val key = model.state.value.scope ?: return
        allowClose = true; host.closeSheet()
        runCatching { model.bridge.call("ui.channel.open", JSONObject().put("kind",kind).put("id",id).put("order",order).put("dialog",key.dialog).put("topic",key.topic).toString()) }.onFailure { android.widget.Toast.makeText(context, it.message ?: "会话已变化", android.widget.Toast.LENGTH_LONG).show() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!closed) composition.prepare() }
    override fun onMeasure(w: Int, h: Int) { if (!closed) composition.prepare(); super.onMeasure(w, if (panelHeight > 0) MeasureSpec.makeMeasureSpec(minOf(panelHeight, if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) panelHeight else MeasureSpec.getSize(h)),MeasureSpec.EXACTLY) else h) }
    fun close() { if (closed) return; closed = true; registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear(); composition.close() }
}

@Composable private fun ShortcutSheet(view: ChannelSheetView, initial: String, close: () -> Unit) {
    val model = view.model
    val state by model.state.collectAsState()
    val link = state.link
    var page by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().background(CardColor).imePadding()) {
        if (page !in setOf("edit", "merchant-edit", "merchant-domains")) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when(page) { "orders" -> "通道订单"; "stats" -> "成功率"; "merchant-orders" -> "商户订单"; "merchant-settlements" -> "商户结算"; "merchant-select" -> "选择当前商户"; "edit-select" -> "选择要编辑的通道"; else -> "选择支付通道" }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if(page.startsWith("merchant")) Text(state.merchant?.let{"${it.username} · 商户 ${it.uid}"}?:"Telegram ID ${state.telegramId}",color=Muted,fontSize=13.sp)
                    else if (link != null) Text("${link.name} #${link.id}", color = Muted, fontSize = 13.sp)
                }
                TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text("关闭") }
            }
            HorizontalDivider(color = Line)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                page == "select" -> ChannelSelector(view, close)
                page == "edit-select" -> ChannelEditorChoice(state.channels) { selected -> model.select(selected,false);page="edit" }
                page.startsWith("merchant") -> MerchantShortcutContent(view,page)
                state.targets.isEmpty() -> Text("请先选择支付通道", Modifier.padding(16.dp))
                page == "stats" -> ChannelStatistics(model)
                page == "orders" -> ScopedShortcutOrders(view)
                page == "edit" && link != null -> ShortcutEditor(view, link)
            }
        }
    }
}

@Composable private fun ChannelSelector(view: ChannelSheetView, close: () -> Unit) {
    val model = view.model
    val state by model.state.collectAsState()
    val original = remember { state.channels }
    var query by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf<List<ConfigurationChannel>>(emptyList()) }
    var selected by remember { mutableStateOf(original.map { it.id }.toSet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    SideEffect { view.dirty=selected!=original.map{it.id}.toSet() }
    LaunchedEffect(retry) {
        loading=true;error=null
        try { choices=model.repository().configurationChannels() }
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message?:"读取通道失败"}
        finally { if(currentCoroutineContext().isActive)loading=false }
    }
    Column(Modifier.fillMaxSize()) {
        Text(if((state.scope?.dialog?:0)<0) "关联到当前群，所有话题共用，可勾选多个通道。" else "关联到当前会话，可勾选多个通道。",Modifier.padding(16.dp),color=Muted,fontSize=13.sp)
        OutlinedTextField(query,{query=it},placeholder={Text("搜索通道名称或 ID")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(loading)item{CircularProgressIndicator()}
            error?.let{item{Text(it,color=Danger);TextButton(onClick={retry++}){Text("重试")}}}
            val rows=choices.filter{query.isBlank()||it.id.contains(query,true)||it.name.contains(query,true)}
            items(rows,key={it.id}){row->
                fun toggle(){selected=if(row.id in selected)selected-row.id else selected+row.id}
                Row(Modifier.fillMaxWidth().heightIn(min=62.dp).clickable{toggle()}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                    Checkbox(row.id in selected,{toggle()});Column{Text(row.name);Text("#${row.id} · ${row.typeId}",color=Muted,fontSize=12.sp)}
                }
            }
            original.filter{old->choices.none{it.id==old.id}&&old.id in selected}.forEach{old->item(key="missing:"+old.id){Row(verticalAlignment=Alignment.CenterVertically){Text("${old.name} #${old.id} · 后台未返回",Modifier.weight(1f),color=Warning);TextButton(onClick={selected=selected-old.id}){Text("移除")}}}}
            if(!loading&&error==null&&rows.isEmpty())item{Text("没有匹配的通道",color=Muted)}
            failure?.let{item{Text(it,color=Danger)}}
        }
        Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            val allowed=!loading&&error==null
            OutlinedButton(enabled=allowed&&selected.size==1,onClick={
                val row=choices.firstOrNull{it.id in selected}
                if(row!=null){model.select(ChannelLink(row.id,row.name),false);view.dismissAfterSave()}
            },modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("仅本次使用")}
            Button(enabled=allowed,onClick={try{
                val saved=selected.mapNotNull{id->choices.firstOrNull{it.id==id}?.let{ChannelLink(it.id,it.name)}?:original.firstOrNull{it.id==id}}
                model.associate(saved);view.dismissAfterSave()
            }catch(e:Exception){failure=e.message?:"保存关联失败"}},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("保存关联（${selected.size}）")}
        }
    }
}

@Composable private fun ChannelEditorChoice(channels:List<ChannelLink>,choose:(ChannelLink)->Unit){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("编辑前请选择具体通道",color=Muted)}
        items(channels,key={it.id}){row->OutlinedButton(onClick={choose(row)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("${row.name} #${row.id}")}}
    }
}

@Composable private fun ChannelFilters(model:ChannelShortcutModel){
    val state by model.state.collectAsState()
    if(state.channels.size>1)LazyRow(Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        item{FilterChip(state.link==null,{model.selectAll()},label={Text("全部通道（${state.channels.size}）")},modifier=Modifier.heightIn(min=48.dp))}
        items(state.channels,key={it.id}){row->FilterChip(state.link?.id==row.id,{model.select(row,false)},label={Text(row.name+" #"+row.id)},modifier=Modifier.heightIn(min=48.dp))}
    }
}

@Composable private fun ChannelStatistics(model: ChannelShortcutModel) {
    val state by model.state.collectAsState()
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model,lifecycle,state.targets.map{it.id},state.window) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh(all=true); while(isActive){delay(30_000);if(!model.state.value.loading)model.refresh(all=true)} } }
    Column(Modifier.fillMaxSize()) {
        ChannelFilters(model)
        FlowWindows(state.window,model::changeWindow)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(state.loading) item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let{item{Text("读取失败：$it",color=Danger)}}
            if(state.link==null) items(state.channels,key={it.id}){row->
                val stats=state.snapshots[row.id]
                PayCard{Text("${row.name} #${row.id}",fontWeight=FontWeight.SemiBold);Text(stats?.rateText?:"—",fontSize=28.sp);Text("近${state.window.label} · 总订单 ${stats?.total?:"—"} · 成功 ${stats?.successful?:"—"}",color=Muted);state.channelErrors[row.id]?.let{Text(it,color=Danger)}}
            }
            val data=state.snapshot
            if(state.link!=null)item{Text(data?.rateText?:"—",fontSize=36.sp,fontWeight=FontWeight.SemiBold);Text("近${state.window.label} · 官方通道监测",color=Muted)}
            if(state.link!=null)item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("总订单 ${data?.total?:"—"}");Text("成功 ${data?.successful?:"—"}");Text("处理中 ${data?.processing?:"—"}")}}
            if(data?.total?.let{it in 1..9}==true)item{Text("样本少：${data.total} 笔，谨慎判断",color=Warning)}
            item{Text("成功率趋势",fontWeight=FontWeight.Medium);Text("后台尚未提供此时间窗的完整趋势数据",color=Muted)}
            item{Text("最近订单",fontWeight=FontWeight.Medium);Text("请从通道订单查看；不以当前页样本代替整个时间窗。",color=Muted)}
            item{Text(data?.updated?.let{"更新于 "+java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(it))}?:"缺失字段显示 —；未以 0% 或 100% 代替。",color=Muted,fontSize=12.sp)}
        }
        TextButton(onClick={model.refresh(all=true)},enabled=!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("刷新统计 · 面板内每30秒更新")}
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FlowWindows(window:MonitorWindow,onChange:(MonitorWindow)->Unit){FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(MonitorWindow.FIVE_MINUTES,MonitorWindow.HALF_HOUR,MonitorWindow.HOUR,MonitorWindow.SIX_HOURS,MonitorWindow.DAY).forEach{item->FilterChip(selected=window==item,onClick={onChange(item)},label={Text(item.label)},modifier=Modifier.heightIn(min=48.dp))}}}

@Composable private fun ScopedShortcutOrders(view:ChannelSheetView,merchantId:String=""){
    val shortcut by view.model.state.collectAsState()
    val ids=if(merchantId.isNotBlank())emptySet()else shortcut.targets.map{it.id}.toSet()
    val environment=remember{EmbeddedEnvironment(view.model.bridge)}
    val vm:AppViewModel=viewModel(key="orders:"+merchantId+":"+ids.joinToString(","),factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel> create(c:Class<T>):T=AppViewModel(environment,loadOnStart=false,lockedChannelId=ids.singleOrNull().orEmpty(),lockedMerchantId=merchantId,allowedChannelIds=ids) as T})
    val state by vm.state.collectAsState()
    LaunchedEffect(vm){vm.loadOrders(1,OrderSearchFilters(merchantId=merchantId,channelId=ids.singleOrNull().orEmpty()))}
    val nav=remember(view){shortcutNavigation(view)}
    val mutationBusy=remember(view){{value:Boolean->view.busy=value}}
    Column(Modifier.fillMaxSize()){
        if(merchantId.isBlank()){ChannelFilters(view.model);if(ids.size>1)Text("全部关联通道 · 按通道分组分页",Modifier.padding(horizontal=16.dp),color=Muted,fontSize=12.sp)}
        CompositionLocalProvider(LocalPanelNavigation provides nav,LocalChannelMutationBusy provides mutationBusy){Box(Modifier.weight(1f)){OrdersScreen(state,vm)}}
        BatchDialog(state.batch,vm::startBatch,vm::closeBatch,vm::retryBatch)
    }
}

private fun shortcutNavigation(view:ChannelSheetView)=object:PanelNavigation{
    override fun orders(channelId:String,merchantId:String){view.openPanel(if(merchantId.isNotBlank())"merchant-orders" else "channel-orders",if(merchantId.isNotBlank())merchantId else channelId)}
    override fun settlements(merchantId:String){view.openPanel("merchant-settlements",merchantId)}
    override fun merchants(merchantId:String){view.openPanel("merchant",merchantId)}
    override fun domains(merchantId:String){view.openPanel("merchant-domains",merchantId)}
    override fun module(id:String,channelId:String,channelType:String){view.openPanel("channel",channelId)}
    override fun evidence(order:OrderSummary){val raw=JSONObject().put("trade_no",order.tradeNo).put("out_trade_no",order.merchantTradeNo).put("uid",order.merchantId).put("money",order.amount).put("currency",order.currency);view.openPanel("evidence",order=raw.toString())}
}

@Composable private fun MerchantShortcutContent(view:ChannelSheetView,kind:String){
    val state by view.model.state.collectAsState()
    val merchant=state.merchant
    if(kind=="merchant-select"){
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp)){
            item{Text("TG ${state.telegramId} 对应多个商户，请核对后选择。",color=Warning)}
            items(state.merchantCandidates,key={it.uid}){row->OutlinedButton(onClick={view.model.chooseMerchant(row.uid);view.dismissAfterSave()},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("${row.username} · 商户 ${row.uid}")}}
        };return
    }
    if(merchant==null){Text(state.merchantError?:"正在识别商户…",Modifier.padding(16.dp));return}
    val environment=remember{EmbeddedEnvironment(view.model.bridge)}
    if(kind=="merchant-orders"){ScopedShortcutOrders(view,merchant.uid);return}
    if(kind=="merchant-domains"){
        val vm:DomainAuditViewModel=viewModel(key="domains:"+merchant.uid,factory=DomainAuditViewModel.Factory(environment.repository,merchant.uid,true))
        val busy=remember(view){{value:Boolean->view.busy=value}}
        CompositionLocalProvider(LocalChannelMutationBusy provides busy){DomainAuditRoot(vm){view.requestDismiss()}};return
    }
    val vm:AppViewModel=viewModel(key=kind+":"+merchant.uid,factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel> create(c:Class<T>):T=AppViewModel(environment,loadOnStart=false,lockedMerchantId=merchant.uid) as T})
    val original by vm.state.collectAsState()
    if(kind=="merchant-settlements"){
        LaunchedEffect(vm){vm.loadSettlements(1,SettlementSearchFilters(merchantId=merchant.uid))}
        SideEffect{view.busy=original.batch?.running==true||original.refreshing&&!original.settlementsLoading}
        CompositionLocalProvider(LocalPanelNavigation provides remember(view){shortcutNavigation(view)}){SettlementsScreen(original,vm)}
        BatchDialog(original.batch,vm::startBatch,vm::closeBatch,vm::retryBatch);return
    }
    var loaded by remember{mutableStateOf(false)}
    LaunchedEffect(vm){vm.openMerchantEditor(merchant.uid)}
    val form=original.merchantEditor
    LaunchedEffect(form,original.merchantEditorLoading){
        if(form!=null)loaded=true
        if(loaded&&form==null&&!original.merchantEditorLoading&&!original.merchantEditorSaving){view.model.refreshMerchant();view.dismissAfterSave()}
    }
    SideEffect{
        val baseline=original.merchantEditorOriginal?.fields.orEmpty().associate{it.name to it.value}
        view.dirty=form?.fields.orEmpty().any{!it.readOnly&&it.type!="hidden"&&(!it.sensitive||it.value.isNotBlank())&&baseline[it.name]!=it.value}
        view.busy=original.merchantEditorSaving
        view.discardAction={vm.closeMerchantEditor()}
    }
    MerchantProfileScreen(original,{view.dismissAfterSave()},vm::editMerchantField,vm::saveMerchantEditor,{vm.openMerchantEditor(merchant.uid)})
}

@Composable private fun ShortcutEditor(view:ChannelSheetView,link:ChannelLink){
    val repo=remember{view.model.repository()}
    val drafts=remember{FormDraftStore(view.context,view.model.bridge.accountScope())}
    val vm:PaymentAdminViewModel=viewModel(factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel> create(c:Class<T>):T=PaymentAdminViewModel(repo,PaymentAdminCatalog.find("channels"),drafts=drafts,loadOnStart=false) as T})
    val state by vm.state.collectAsState()
    var loaded by remember{mutableStateOf(false)}
    var baseline by remember{mutableStateOf<Map<String,String>?>(null)}
    var submitted by remember{mutableStateOf(false)}
    var submittedName by remember{mutableStateOf(link.name)}
    var verifying by remember{mutableStateOf(false)}
    var verified by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(vm){vm.openShortcutEditor(link.id)}
    LaunchedEffect(state.editor,state.accountLoading,state.pluginLoading){
        val form=state.editor
        if(form!=null&&!state.accountLoading&&!state.pluginLoading&&baseline==null){baseline=form.fields.associate{it.name to it.value};loaded=true}
        if(form!=null) view.dirty=baseline?.let{old->form.fields.any{it.type!="hidden"&&it.value!=old[it.name].orEmpty()}}?:false
        if(loaded&&submitted&&form==null&&!state.writeUncertain){view.model.saved(submittedName);view.dismissAfterSave()}
    }
    SideEffect{view.busy=submitted&&state.loading;view.discardAction={state.editor?.let{drafts.remove(repo.backendAddress,it)};vm.closeEditor()}}
    LaunchedEffect(state.primarySaved){if(state.primarySaved){state.editor?.fields?.firstOrNull{it.name=="name"}?.value?.let(view.model::saved)}}
    Column(Modifier.fillMaxSize()){
        Text("${link.name} #${link.id}",Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),fontSize=13.sp,color=Muted)
        if(state.primarySaved)Text("基本信息已保存；只重试账户配置",Modifier.padding(horizontal=16.dp),color=Warning)
        if(state.writeUncertain)Text("保存结果未知，请先核对后台配置，勿重复提交。密钥状态无法从掩码判断。",Modifier.padding(16.dp),color=Warning)
        state.message?.let{Text(it,Modifier.padding(horizontal=16.dp),color=Danger)}
        if(state.writeUncertain) TextButton(enabled=!verifying,onClick={scope.launch {
            verifying=true
            try {
                val document=state.document ?: error("请关闭后重新读取通道配置")
                val latest=repo.paymentEditor("channels", NativeAction("编辑通道", "ajax_pay.php?act=getChannel&id=${link.id}"), document)
                val current=latest.fields.associate{it.name to it.value}
                val fields=state.editor?.fields.orEmpty().filter{!it.sensitive && it.type!="hidden" && !it.name.startsWith("account::")}
                val matching=fields.count{current[it.name]==it.value}
                verified="已重新读取：基本信息 ${matching}/${fields.size} 项与本次填写一致。账户配置及密钥请在后台核对；当前仍禁止重复提交。"
            } catch(e:CancellationException){throw e}
            catch(e:Exception){verified="核对失败：${e.message}"}
            finally{if(currentCoroutineContext().isActive)verifying=false}
        }}){Text(if(verifying)"核对中…" else "核对当前配置")}
        verified?.let{Text(it,Modifier.padding(horizontal=16.dp),color=Muted,fontSize=12.sp)}

        val form=state.editor
        when{
            form!=null->Box(Modifier.weight(1f)){PaymentEditorScreen(form.copy(fields=form.fields.map{if(state.loading||(state.primarySaved&&!it.name.startsWith("account::")))it.copy(readOnly=true)else it}),state.loading||state.pluginLoading||state.accountLoading,{view.requestDismiss()},vm::updateEditorField,{f,v->submittedName=v["name"].orEmpty().ifBlank{submittedName};submitted=true;vm.submit(f,v)}, {},snackbar,state.routeChannels,state.routeStats,vm::appendRoute,uncertain=state.writeUncertain)}
            state.loading->Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            else->Column(Modifier.padding(16.dp)){Text(state.loadError?:"无法读取通道编辑表单",color=Danger);TextButton(onClick={vm.openShortcutEditor(link.id)}){Text("重试")};TextButton(onClick={view.requestDismiss()}){Text("关闭")}}
        }
    }
}
