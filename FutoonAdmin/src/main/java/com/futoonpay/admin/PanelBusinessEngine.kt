package com.futoonpay.admin

import com.futoonpay.admin.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Original 1.3.4 repository, forms, validation and module catalogue adapted to the host panel. */
class PanelBusinessEngine internal constructor(private val api: FutoonApiClient) {
    constructor(session: PanelSession): this(FutoonApiClient(PanelCookieJar(session,session.address().toHttpUrl()),session.address().toHttpUrl()))
    private val repository=FutoonRepository(api)
    private data class DocumentState(val module: String,val path: String,val document: NativeDocument)
    private data class FormState(val module: String,val path: String,val form: NativeForm,val uid: String="",val accountForm: NativeForm?=null,val routeForm: NativeForm?=null,val primarySaved: Boolean=false,val expires: Long=System.currentTimeMillis()+600000)
    private data class ActionState(val state: DocumentState,val action: NativeAction,val expires: Long=System.currentTimeMillis()+600000)
    private val batches=linkedMapOf<String,Pair<Long,BatchRequest>>()
    private var currentOrders=listOf<OrderSummary>()
    private var currentSettlements=listOf<SettlementSummary>()
    private var currentPaymentRows=listOf<PaymentBatchRow>()
    private var currentPaymentModule=""
    private val documents=linkedMapOf<String,DocumentState>()
    private val forms=linkedMapOf<String,FormState>()
    private val actions=linkedMapOf<String,ActionState>()
    private val paymentModules=setOf("channels","payment_types","rolls","proxies","channel_monitor","weixin_apps")
    private val catalogueModules=setOf("funds","merchant_stats","risk","blacklist","inactive","payment_plugins","user_groups","group_purchase","announcements","payment_settings","site_settings","login_logs","account_settings")
    fun supports(op: String)=op in setOf("orders.list","order.detail","order.action","orders.metrics","module.read","module.editor","module.plugins","module.accountFields","module.form","module.inspect","module.action","form.save","merchant.profile","merchant.status","merchant.group","merchant.balance","settlements.list","settlement.status","settlement.info","domains.list","domain.action","blacklist.add","blacklist.remove","channel.test","channel.stats","batch.notify","batch.options","batch.run")
    @Throws(Exception::class) fun execute(op: String,p: JSONObject): JSONObject = runBlocking {
        require(supports(op)) { "不支持的业务操作" }
        when(op) {
            "orders.list" -> {
                val filters=orderFilters(p)
                val rows=repository.orders(page(p),filters)
                currentOrders=rows.rows
                val metrics=orderPageMetrics(rows.rows)
                JSONObject().put("metrics",JSONObject().put("count",metrics.count).put("successful",metrics.successful).put("rate",metrics.rate).put("amounts",JSONObject(metrics.amounts.mapValues {it.value.toPlainString()}))).put("rows",JSONArray(rows.rows.map(::orderJson))).put("total",rows.total).put("pageSize",30)
            }
            "orders.metrics" -> {
                val rows=repository.orders(page(p),orderFilters(p)).rows
                val stats=repository.orderChannelStats(rows)
                JSONObject().put("warning",stats.message).put("channels",JSONArray(stats.stats.values.map { s -> JSONObject().put("id",s.channelId).put("name",s.channelName).put("rate",s.fiveMinuteRate).put("thirtyMinutes",s.thirtyMinuteRate).put("oneHour",s.oneHourRate).put("oneDay",s.oneDayRate).put("source",s.source.label).put("note",s.note) }))
            }
            "order.detail" -> detailJson(repository.orderDetail(orderId(p.getString("id"))))
            "order.action" -> {
                val id=orderId(p.getString("id")); val action=p.getString("action")
                result(when(action) {
                    "setStatus" -> { val status=p.getInt("status"); require(status in 0..3);repository.setOrderStatus(id,status) }
                    "refund" -> repository.manualRefund(id,amount(p.getString("money")),p.optString("remark"))
                    "apirefund" -> repository.apiRefund(id,amount(p.getString("money")),p.getString("paypwd"),p.optString("remark"))
                    else -> { require(action in setOf("notify","queryChannelOrder","freeze","unfreeze")); repository.orderAction(action,id) }
                })
            }
            "module.read" -> readModule(p)
            "module.editor" -> editor(p)
            "module.form" -> {
                val key=p.getString("ticket"); val f=forms[key] ?: error("表单已过期，请刷新")
                if(f.module in paymentModules)prepareEditor(f.module,f.path,f.form) else formJson(key,f)
            }
            "module.plugins" -> {
                val type=p.getString("type");val options=repository.paymentPlugins(type)
                forms[p.optString("ticket")]?.let { state ->
                    require(state.module=="channels")
                    forms[p.getString("ticket")]=state.copy(form=state.form.copy(fields=state.form.fields.filterNot {it.name.startsWith("account::")}.map {f->when(f.name){"type"->f.copy(value=type);"plugin"->f.copy(value="",type="select",options=options);else->f}}),accountForm=null,primarySaved=false)
                }
                JSONObject().put("options",JSONArray(options.map(::optionJson)))
            }
            "module.accountFields" -> {
                val ticket=p.getString("ticket");val state=forms[ticket] ?: error("表单已过期")
                require(state.module=="channels");val plugin=p.getString("plugin")
                require(state.form.fields.firstOrNull {it.name=="plugin"}?.options?.any {it.value==plugin}==true){"支付插件无效"}
                val id=state.form.fields.firstOrNull {it.name=="id"}?.value.orEmpty()
                val account=repository.pluginAccountForm(plugin,id)
                val updated=state.copy(form=state.form.copy(fields=state.form.fields.filterNot {it.name.startsWith("account::")}.map {if(it.name=="plugin")it.copy(value=plugin) else it}+account?.fields.orEmpty().filter {it.type!="hidden"}.map {it.copy(name="account::${it.name}")}),accountForm=account,primarySaved=false)
                forms[ticket]=updated;formJson(ticket,updated)
            }
            "module.inspect" -> {
                val a=actionState(p.getString("ticket"))
                require(editorAction(a.action) || a.action.endpoint.startsWith("futoon://")) { "此操作需要核对后提交" }
                if(a.action.endpoint.startsWith("futoon://")) {
                    val uri=java.net.URI(a.action.endpoint)
                    JSONObject().put("navigate",if(uri.host=="channels")"channels" else "channel").put("id",query(a.action.endpoint,"id")).put("type",query(a.action.endpoint,"type"))
                } else {
                    require(a.state.module in paymentModules) { "后台未提供可编辑的表单" }
                    prepareEditor(a.state.module,a.state.path,repository.paymentEditor(a.state.module,a.action,a.state.document))
                }
            }
            "module.action" -> {
                val a=actionState(p.getString("ticket"))
                require(!editorAction(a.action) && !a.action.endpoint.startsWith("futoon://")) { "请先打开编辑表单" }
                val r=if(a.state.module in paymentModules)repository.runPaymentAction(a.state.module,a.action)
                    else if(a.action.method.equals("POST",true))api.postAction(a.action.endpoint,a.state.path,a.action.fields)
                    else api.getAction(a.action.endpoint,a.state.path,a.action.fields)
                actions.remove(p.getString("ticket"));result(r)
            }
            "form.save" -> saveForm(p)
            "merchant.profile" -> {val uid=id(p.getString("uid"));registerForm("merchant","uset.php?my=edit&uid=$uid",repository.merchantProfile(uid),uid)}
            "merchant.status" -> {val type=p.getString("type");require(type in setOf("status","pay","settle"));result(repository.setMerchantStatus(id(p.getString("uid")),type,p.getBoolean("enabled")))}
            "merchant.group" -> result(repository.setMerchantGroup(id(p.getString("uid")),p.getString("gid").also {require(it.matches(Regex("[0-9]{1,10}")))},p.optString("endtime")))
            "merchant.balance" -> {require(p.optString("currency","CNY")=="CNY");val direction=p.getString("direction");require(direction in setOf("add","deduct"));result(repository.rechargeMerchant(id(p.getString("uid")),if(direction=="add")"add" else "reduce",amount(p.getString("amount"))))}
            "settlements.list" -> {
                val r=repository.settlements(page(p),mapOf("uid" to p.optString("uid"),"type" to "0","dstatus" to p.optString("status","-1")))
                currentSettlements=r.rows
                JSONObject().put("total",r.total).put("rows",JSONArray(r.rows.map {s -> JSONObject().put("id",s.id).put("uid",s.merchantId).put("username",s.username).put("type",s.payType).put("account",s.account).put("money",s.amount).put("realmoney",s.actualAmount).put("usdt_count",s.usdtAmount).put("usdt_rate",s.usdtRate).put("addtime",s.createdAt).put("endtime",s.completedAt).put("status",s.status).put("result",s.result).put("currency",s.currency)}))
            }
            "settlement.status" -> {val status=p.getInt("status");require(status in (if(repository.modernBackend)setOf(1,6) else (0..3).toSet()));if(repository.modernBackend)require(currentSettlements.firstOrNull {it.id==p.getString("id")}?.canSetModernStatus(status)==true){"当前结算状态不允许此操作，请刷新"};result(if(status==3)repository.failSettlementWithReason(id(p.getString("id")),p.getString("reason"),id(p.getString("uid")),p.optString("usdt_rate"),p.optString("usdt_count")) else repository.setSettlementStatus(id(p.getString("id")),status,id(p.getString("uid")),p.optString("usdt_rate"),p.optString("usdt_count")))}
            "settlement.info" -> result(repository.saveSettlementInfo(id(p.getString("id")),p.getString("pay_type"),p.getString("pay_account"),p.getString("pay_name")))
            "domains.list" -> {val r=repository.domainAudits(page(p),mapOf("uid" to p.optString("uid"),"dstatus" to p.optString("status","-1")));JSONObject().put("total",r.total).put("rows",JSONArray(r.rows.map { d -> JSONObject().put("id",d.id).put("uid",d.merchantId).put("domain",d.domain).put("status",d.status).put("addtime",d.createdAt).put("remark",d.remark)}))}
            "domain.action" -> result(when(p.getString("action")){"add"->repository.addDomain(id(p.getString("uid")),p.getString("domain"));"delete"->repository.deleteDomain(id(p.getString("id")));else->{val status=p.getInt("status");require(status in 0..2);repository.setDomainStatus(id(p.getString("id")),status,p.optString("remark"))}})
            "blacklist.add" -> {require(p.getString("content").isNotBlank());result(repository.addBlacklist(p.getString("type"),p.getString("content"),p.optString("reason"),p.optString("days","0"),p.optString("remark")))}
            "blacklist.remove" -> result(repository.deleteBlacklist(id(p.getString("id"))))
            "channel.test" -> {val r=repository.testPayment(id(p.getString("id")),p.optString("name","测试订单"),amount(p.getString("amount")));JSONObject().put("code",if(r.success)0 else -1).put("msg",r.message).put("trade_no",r.tradeNo).put("qrcode",r.qrContent).put("qr_image",paymentQr(r.qrContent)?.let {bitmap->val bytes=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();"data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)} ?: "")}
            "channel.stats" -> {val stats=repository.channelStatistics(listOf(id(p.getString("id"))));JSONObject().put("rows",JSONArray(stats.values.map {s->JSONObject().put("id",s.channelId).put("5分钟成功率",s.fiveMinuteRate).put("30分钟成功率",s.thirtyMinuteRate).put("1小时成功率",s.oneHourRate).put("24小时成功率",s.oneDayRate).put("来源",s.source.label)}))}
            "batch.options" -> {
                val module=p.getString("module");val ids=p.getJSONArray("ids");require(ids.length() in 1..100)
                val selected=(0 until ids.length()).map {ids.getString(it)}.toSet()
                val requests=when(module) {
                    "orders" -> {
                        val rows=selected.map {id -> currentOrders.firstOrNull {it.tradeNo==id} ?: repository.orderDetail(orderId(id)).let {d->OrderSummary(d.tradeNo,d.merchantTradeNo,d.merchantId,d.domain,d.name,d.amount,d.paidAmount,d.merchantIncome,d.paymentType,d.channelId,d.plugin,d.createdAt,d.completedAt,d.status,d.settlementStatus,d.currency)} }
                        orderBatchOptions(rows)
                    }
                    "settlements" -> {require(currentSettlements.map {it.id}.containsAll(selected)){"结算记录已变化，请刷新"};settlementBatchOptions(currentSettlements.filter {it.id in selected}, repository.modernBackend)}
                    else -> {require(module==currentPaymentModule && currentPaymentRows.map {it.id}.containsAll(selected)){"配置记录已变化，请刷新"};paymentBatchOptions(module,currentPaymentRows.filter {it.id in selected})}
                }
                JSONObject().put("options",JSONArray(requests.map {b->val ticket=UUID.randomUUID().toString();batches[ticket]=System.currentTimeMillis()+600000 to b;JSONObject().put("ticket",ticket).put("title",b.title).put("warning",b.warning).put("targets",JSONArray(b.commands.map {it.label}))}))
            }
            "batch.run" -> {
                val ticket=p.getString("ticket");val (expires,request)=batches.remove(ticket) ?: error("批量操作已过期，请重新选择")
                require(expires>System.currentTimeMillis()){"批量操作已过期"}
                val outcomes=repository.runBatch(request.commands)
                JSONObject().put("title",request.title).put("results",JSONArray(outcomes.map {r->JSONObject().put("id",r.command.id).put("label",r.command.label).put("status",r.status.label).put("message",r.message)}))
            }
            "batch.notify" -> {
                val arr=p.getJSONArray("orders");require(arr.length() in 1..100)
                val commands=(0 until arr.length()).map {orderId(arr.getString(it))}.distinct().map {BatchCommand(it,it,BatchKind.ORDER_ACTION,mapOf("action" to "notify"))}
                val outcomes=repository.runBatch(commands)
                JSONObject().put("results",JSONArray(outcomes.map {r->JSONObject().put("id",r.command.id).put("ok",r.status==BatchOutcomeStatus.SUCCESS).put("message",r.message)})).put("requested",commands.size)
            }
            else -> error("未知业务操作")
        }
    }
    private fun orderFilters(p: JSONObject): Map<String,String> = buildMap {
        val status=p.optString("status","-1");require(status in setOf("-1","0","1","2","3"));put("dstatus",status);put("type","0")
        for(key in listOf("uid","channel"))if(p.optString(key).isNotBlank())put(key,id(p.getString(key)))
        for(key in listOf("starttime","endtime"))if(p.optString(key).isNotBlank())put(key,p.getString(key).also {java.time.LocalDate.parse(it)})
        require(!containsKey("starttime")||!containsKey("endtime")||getValue("starttime")<=getValue("endtime")){"开始日期不能晚于结束日期"}
        if(p.optString("column").isNotBlank()){val c=p.getString("column");require(c in setOf("trade_no","out_trade_no","api_trade_no","uid","name","money","realmoney","getmoney","domain","buyer","ip"));put("column",c);put("value",p.optString("value"))}
    }
    private suspend fun readModule(p: JSONObject): JSONObject {
        val module=p.getString("module");require(!repository.modernBackend || OfficialBackendFeatures.available(module)){"新版后台已移除此模块"};require(module in paymentModules || module in catalogueModules){"模块无效"}
        if(module in paymentModules) {
            val roll=RollGroupTab.values().firstOrNull {it.key==p.optString("tab")} ?: RollGroupTab.ALL
            val channel=PaymentChannelTab.values().firstOrNull {it.key==p.optString("tab")} ?: PaymentChannelTab.ALL
            val loaded=repository.paymentDocument(module,page(p),p.optString("query"),roll,channel,p.optString("type"))
            val query=p.optString("query").trim()
            val doc=if(query.isBlank()||module=="channel_monitor")loaded else loaded.copy(tables=loaded.tables.map {table->
                val indices=table.rows.indices.filter {i->table.rows[i].any {it.contains(query,true)}}
                table.copy(rows=indices.map {table.rows[it]},rowActions=indices.map {table.rowActions.getOrNull(it).orEmpty()})
            })
            return documentJson(DocumentState(module,"",doc))
        }
        val definition=ModuleCatalog.find(module)
        return when(val source=definition.source) {
            is ModuleSource.Ajax -> {
                val filters=if(source.searchField.isNotBlank() && p.optString("query").isNotBlank())mapOf(source.searchField to p.getString("query")) else emptyMap()
                val r=repository.genericPage(source.endpoint,source.referer,source.idField,source.fields,page(p),filters)
                JSONObject().put("total",r.total).put("rows",JSONArray(r.rows.map { row -> JSONObject(row.values).put("id",row.id) }))
            }
            is ModuleSource.Documents -> {
                val index=p.optInt("section",0);require(index in source.pages.indices)
                val target=source.pages[index]
                documentJson(DocumentState(module,target.path,repository.nativeDocument(target.path))).put("sections",JSONArray(source.pages.mapIndexed {i,s->JSONObject().put("index",i).put("title",s.title)})).put("section",index)
            }
        }
    }
    private fun documentJson(state: DocumentState): JSONObject {
        documents[state.module]=state
        trimTickets()
        val doc=state.document
        currentPaymentModule=state.module
        currentPaymentRows=doc.tables.flatMap {table->table.rows.mapIndexed {i,row->
            val rowActions=table.rowActions.getOrNull(i).orEmpty()
            val index=table.headers.indexOfFirst {it.equals("ID",true)||it=="编号"}
            val id=row.getOrNull(index).orEmpty().ifBlank {rowActions.firstNotNullOfOrNull {query(it.endpoint,"id").takeIf(String::isNotBlank) ?: it.fields["id"]}.orEmpty()}
            PaymentBatchRow(id,row.take(2).joinToString(" · "),rowActions)
        }}.filter {it.id.isNotBlank()}
        val tables=JSONArray(doc.tables.map {t -> JSONObject().put("title",t.title).put("headers",JSONArray(t.headers)).put("rows",JSONArray(t.rows.mapIndexed { i,cells ->
            val rowActions=t.rowActions.getOrNull(i).orEmpty()
            val idIndex=t.headers.indexOfFirst {it.trim().lowercase() in setOf("id","通道id","编号","轮询组id","代理id")}
            val id=cells.getOrNull(idIndex)?.trim().orEmpty().ifBlank {rowActions.firstNotNullOfOrNull {query(it.endpoint,"id").takeIf(String::isNotBlank)}.orEmpty()}
            JSONObject().put("cells",JSONArray(cells.mapIndexed {i,v->nativeVisibleValue(t.headers.getOrNull(i).orEmpty(),v)})).put("id",id).put("actions",JSONArray(rowActions.map { actionJson(state,it) }))
        }))})
        val out=JSONObject().put("tables",tables).put("notices",JSONArray(doc.notices)).put("forms",JSONArray(doc.forms.map {f->registerForm(state.module,state.path,f)})).put("actions",JSONArray(doc.actions.map {actionJson(state,it)}))
        if(state.module=="channel_monitor") {
            val rows=doc.tables.flatMap {it.toChannelMonitorItems()}
            out.put("rows",JSONArray(rows.map {r -> JSONObject().put("id",r.id).put("name",r.name).put("status",r.status).put("result",r.result).put("lasttime",r.lastChecked).put("success_rate_5min",percent(r.fiveMinuteRate)).put("success_rate_30min",percent(r.thirtyMinuteRate)).put("success_rate_1hour",percent(r.oneHourRate)).put("success_rate_24hour",percent(r.oneDayRate)).put("fields",JSONArray(r.fields.map {JSONObject().put("label",it.first).put("value",nativeVisibleValue(it.first,it.second))})).put("actions",JSONArray(r.actions.map {actionJson(state,it)}))})).put("total",rows.size)
        }
        return out
    }
    private fun actionJson(state: DocumentState,a: NativeAction): JSONObject {
        val ticket=UUID.randomUUID().toString();actions[ticket]=ActionState(state,a)
        return JSONObject().put("ticket",ticket).put("label",a.label).put("editor",editorAction(a)||a.endpoint.startsWith("futoon://")).put("destructive",a.destructive)
    }
    private fun actionState(ticket: String)=actions[ticket]?.takeIf {it.expires>System.currentTimeMillis()} ?: error("操作已过期，请刷新记录")
    private fun editorAction(a: NativeAction)=listOf("新增","添加","编辑","修改","配置","账户","路由").any {a.label.contains(it)}
    private suspend fun editor(p: JSONObject): JSONObject {
        val module=p.getString("module");require(module in paymentModules)
        val state=documents[module] ?: run {readModule(JSONObject().put("module",module));documents.getValue(module)}
        val id=p.optString("id");val kind=p.optString("editor","detail")
        if(kind=="add") {
            val template=state.document.forms.filter {f->when(module){"channels"->f.fields.any {it.name=="plugin"}&&f.fields.any {it.name=="rate"};"payment_types"->f.fields.any {it.name=="showname"}&&f.fields.any {it.name=="device"};"rolls"->f.fields.any {it.name=="kind"}&&f.fields.any {it.name=="type"};else->f.fields.size>=3}}.maxByOrNull {it.fields.size}
            if(template==null) {val a=state.document.actions.firstOrNull {it.label.contains("新增")||it.label.contains("添加")} ?: error("后台没有提供新增表单");return prepareEditor(module,state.path,repository.paymentEditor(module,a,state.document))}
            val fields=template.fields.map {f->f.copy(value=when(f.name){"action"->"add";"id","name","showname","rate","plugin","daytop","paymin","paymax"->"";else->if(f.sensitive)"" else f.value})}
            val suffix=when(module){"channels"->"Channel";"payment_types"->"PayType";"rolls"->"Roll";"weixin_apps"->"Weixin";else->""}
            return prepareEditor(module,state.path,template.copy(title="新增配置",action=if(suffix.isBlank())template.action else "ajax_pay.php?act=save$suffix",method="POST",fields=fields))
        }
        val checked=id(id)
        val endpoint=when(kind){"route"->"rollInfo";"account"->"channelInfo";else->when(module){"channels"->"getChannel";"payment_types"->"getPayType";"rolls"->"getRoll";"weixin_apps"->"getWeixin";else->"getProxy"}}
        return prepareEditor(module,state.path,repository.paymentEditor(module,NativeAction(if(kind=="route")"编辑路由" else "编辑配置","ajax_pay.php?act=$endpoint&id=$checked"),state.document))
    }
    /** Business flow ported from PaymentAdminViewModel: save primary, then account/member configuration. */
    private suspend fun prepareEditor(module: String,path: String,original: NativeForm): JSONObject {
        var form=original
        var account: NativeForm?=null;var routes: NativeForm?=null
        val id=form.fields.firstOrNull {it.name=="id"}?.value.orEmpty()
        if(module=="channels" && form.action.contains("saveChannel") && !form.action.contains("saveChannelInfo")) {
            val type=form.fields.firstOrNull {it.name=="type"}?.value.orEmpty()
            val plugin=form.fields.firstOrNull {it.name=="plugin"}?.value.orEmpty()
            val options=repository.paymentPlugins(type)
            form=form.copy(fields=form.fields.map {if(it.name=="plugin")it.copy(type="select",options=options) else it})
            if(plugin.isNotBlank()) {
                account=if(id.isNotBlank())repository.paymentEditor("channels",NativeAction("账户配置","ajax_pay.php?act=channelInfo&id=$id"),documents[module]?.document ?: error("请刷新列表")) else repository.pluginAccountForm(plugin,id)
                form=form.copy(fields=form.fields+account?.fields.orEmpty().filter {it.type!="hidden"}.map {it.copy(name="account::${it.name}")})
            }
        }
        if(module=="rolls" && form.action.contains("saveRoll") && !form.action.contains("saveRollInfo")) {
            routes=if(id.isNotBlank())repository.paymentEditor("rolls",NativeAction("成员通道","ajax_pay.php?act=rollInfo&id=$id"),documents[module]?.document ?: error("请刷新列表")) else {
                val channels=repository.paymentDocument("channels").tables.flatMap {it.toPaymentChannelItems()}
                NativeForm("成员通道","ajax_pay.php?act=saveRollInfo&id=","POST",listOf(NativeField("list[0][channel]","成员通道","","select",listOf(NativeOption("","不使用"))+channels.map {NativeOption(it.id,it.name)}),NativeField("list[0][weight]","权重","1","number")))
            }
            form=form.copy(fields=form.fields+routes.fields.filter {it.name.startsWith("list[")})
        }
        if(module=="payment_types" && form.methodBindingField()!=null) {
            val channels=repository.configurationChannels()
            form=form.copy(fields=form.fields.map {f->if(f.name in METHOD_BINDING_NAMES && f.type=="multiselect")f.copy(options=(channels.filter {it.typeId==id}.map {NativeOption(it.id,"${it.name} #${it.id}")}+f.options+selectedChannelIds(f.value).map {NativeOption(it,"原有通道 #$it")}).distinctBy {it.value}) else f})
        }
        trimTickets();val ticket=UUID.randomUUID().toString();val state=FormState(module,path,form,accountForm=account,routeForm=routes);forms[ticket]=state
        return formJson(ticket,state)
    }
    private fun registerForm(module: String,path: String,form: NativeForm,uid: String=""): JSONObject {
        trimTickets();val ticket=UUID.randomUUID().toString();val state=FormState(module,path,form,uid);forms[ticket]=state;return formJson(ticket,state)
    }
    private fun formJson(ticket: String,s: FormState): JSONObject {
        val route=s.form.action.contains("saveRollInfo")||s.routeForm!=null
        return JSONObject().put("ticket",ticket).put("title",s.form.title).put("kind",if(route)"route" else "form").put("fields",JSONArray(s.form.fields.filter {it.type!="hidden"}.map {f->JSONObject().put("name",f.name).put("label",f.label).put("value",f.value).put("type",f.type).put("options",JSONArray(f.options.map(::optionJson))).put("sensitive",f.sensitive).put("required",f.required).put("readOnly",f.readOnly)})).put("routeOptions",JSONArray(s.form.fields.firstOrNull {it.name.endsWith("[channel]") }?.options.orEmpty().map(::optionJson)))
    }
    private suspend fun saveForm(p: JSONObject): JSONObject {
        val ticket=p.getString("ticket");val state=forms[ticket]?.takeIf {it.expires>System.currentTimeMillis()} ?: error("编辑表单已过期，请重新加载")
        val form=state.form;val raw=p.getJSONObject("values");val allowed=form.fields.filter {!it.readOnly&&it.type!="hidden"}.associateBy {it.name}
        val routeOptions=form.fields.firstOrNull {it.name.endsWith("[channel]")}?.options.orEmpty().map {it.value}.toSet()
        val values=form.fields.associate {it.name to it.value}.toMutableMap()
        val it=raw.keys();while(it.hasNext()) {val k=it.next();val v=raw.optString(k)
            val route=Regex("list\\[([0-9]{1,2})]\\[(channel|weight)]").matchEntire(k)
            if((form.action.contains("saveRollInfo")||state.routeForm!=null) && route!=null) {require(route.groupValues[1].toInt()<100);if(k.endsWith("[channel]"))require(v in routeOptions){"路由通道已失效，请重新加载"};values[k]=v;continue}
            val f=allowed[k] ?: continue
            require(!f.required||v.isNotBlank()){ "${f.label}不能为空" }
            if(f.type=="select" && v.isNotBlank())require(f.options.any {it.value==v}){"${f.label}选项已变化，请重新加载"}
            if(f.type=="multiselect")require(selectedChannelIds(v).all {id->f.options.any {it.value==id}}){"通道选项无效"}
            if(!f.sensitive||v.isNotBlank())values[k]=v
        }
        val composite=state.accountForm!=null || state.routeForm!=null
        val primary=form.copy(fields=form.fields.filterNot {it.name.startsWith("account::")||(state.routeForm!=null&&it.name.startsWith("list["))})
        var recordId=primary.fields.firstOrNull {it.name=="id"}?.value.orEmpty()
        if(!state.primarySaved) {
            val primaryValues=values.filterKeys {!it.startsWith("account::")&&!(state.routeForm!=null&&it.startsWith("list["))}
            val primaryResult=when {state.uid.isNotBlank()->repository.saveMerchantProfile(state.uid,primary,primaryValues);state.module in paymentModules->repository.submitPaymentForm(state.module,primary,primaryValues);else->repository.submitNativeForm(state.path,primary,primaryValues)}
            if(!primaryResult.success) {if(primaryResult.partial)forms.remove(ticket);return result(primaryResult)}
            recordId=recordId.ifBlank {primaryResult.recordId}
            if(composite) {
                if(recordId.isBlank()) {forms.remove(ticket);return result(ApiResult(false,"基本信息已创建，后台没有返回 ID；请回列表完成后续配置，避免重复创建",partial=true))}
                val fields=form.fields.map {f->when(f.name){"id"->f.copy(value=recordId);"action"->f.copy(value="edit");else->f}}+if(form.fields.none {it.name=="id"})listOf(NativeField("id","ID",recordId,"hidden")) else emptyList()
                forms[ticket]=state.copy(form=form.copy(fields=fields),primarySaved=true)
            }
        }
        state.accountForm?.let {account->
            val fields=account.fields.associate {it.name to it.value}.toMutableMap().apply {putAll(values.filterKeys {it.startsWith("account::")}.mapKeys {it.key.removePrefix("account::")})}
            val r=repository.submitPaymentForm(state.module,account.copy(action="ajax_pay.php?act=saveChannelInfo&id=$recordId"),fields)
            if(!r.success)return result(r.copy(partial=true,message="基本信息已保存，账户配置失败：${r.message}"))
        }
        state.routeForm?.let {route->
            val r=repository.submitPaymentForm(state.module,route.copy(action="ajax_pay.php?act=saveRollInfo&id=$recordId"),values.filterKeys {it.startsWith("list[")})
            if(!r.success)return result(r.copy(partial=true,message="基本信息已保存，成员配置失败：${r.message}"))
        }
        forms.remove(ticket);return result(ApiResult(true,"保存成功",recordId))
    }
    private fun trimTickets() {val now=System.currentTimeMillis();batches.entries.removeAll {it.value.first<=now};while(batches.size>30)batches.remove(batches.keys.first());forms.entries.removeAll {it.value.expires<=now};actions.entries.removeAll {it.value.expires<=now};while(forms.size>200)forms.remove(forms.keys.first());while(actions.size>2000)actions.remove(actions.keys.first())}
    private fun optionJson(o: NativeOption)=JSONObject().put("value",o.value).put("label",o.label)
    private fun result(r: ApiResult)=JSONObject().put("code",if(r.success)0 else -1).put("msg",r.message).put("partial",r.partial).put("id",r.recordId)
    private fun page(p: JSONObject)=p.optInt("page",1).also {require(it in 1..100000)}
    private fun id(s: String)=s.also {require(it.matches(Regex("[1-9][0-9]{0,19}"))){"记录 ID 无效"}}
    private fun orderId(s: String)=s.also {require(it.matches(Regex("[A-Za-z0-9_-]{4,128}"))){"订单号无效"}}
    private fun amount(s: String)=s.also {require(it.matches(Regex("[0-9]{1,14}(\\.[0-9]{1,2})?"))&&it.toBigDecimal()>java.math.BigDecimal.ZERO){"请输入大于 0、最多两位小数的金额"}}
    private fun percent(v: Double?)=v?.let {"$it%"}.orEmpty()
    private fun query(s: String,key: String)=Regex("(?:[?&])${Regex.escape(key)}=([^&#]+)").find(s)?.groupValues?.get(1)?.let {java.net.URLDecoder.decode(it,"UTF-8")}.orEmpty()
    private fun orderJson(o: OrderSummary)=JSONObject().put("trade_no",o.tradeNo).put("out_trade_no",o.merchantTradeNo).put("uid",o.merchantId).put("username",o.merchantName).put("money",o.amount).put("realmoney",o.paidAmount).put("getmoney",o.merchantIncome).put("typeshowname",o.paymentType).put("channel",o.channelId).put("plugin",o.plugin).put("addtime",o.createdAt).put("endtime",o.completedAt).put("status",o.status).put("settle",o.settlementStatus).put("currency",o.currency).put("name",o.name).put("domain",o.domain)
    private fun detailJson(o: OrderDetail)=JSONObject().put("trade_no",o.tradeNo).put("out_trade_no",o.merchantTradeNo).put("api_trade_no",o.apiTradeNo).put("uid",o.merchantId).put("money",o.amount).put("realmoney",o.paidAmount).put("getmoney",o.merchantIncome).put("typeshowname",o.paymentType).put("channel",o.channelId).put("channel_name",o.channelName).put("plugin",o.plugin).put("name",o.name).put("addtime",o.createdAt).put("endtime",o.completedAt).put("buyer",o.buyer).put("domain",o.domain).put("ip",o.ip).put("param",o.parameter).put("status",o.status).put("settle",o.settlementStatus).put("notify_count",o.notifyCount).put("currency",o.currency)
}
