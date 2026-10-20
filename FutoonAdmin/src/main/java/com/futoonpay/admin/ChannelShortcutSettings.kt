package com.futoonpay.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.data.MonitorWindow
import com.futoonpay.admin.ui.theme.*

interface ChannelChatToolsHostBridge : ChatToolsHostBridge {
    fun channelAccountScope(): String
    fun channelBackendAddress(): String
}

@Composable internal fun ChannelShortcutSettings(host: ChannelChatToolsHostBridge?, modifier: Modifier) {
    if (host == null) { Text("请从 Futoonpay 设置打开", modifier.padding(16.dp)); return }
    val context=LocalContext.current
    val store=remember(host){ChannelShortcutStore(context,host.channelAccountScope())}
    var revision by remember{mutableIntStateOf(0)}
    var error by remember{mutableStateOf<String?>(null)}
    val links=remember(revision){store.associations().filter{it.first.account==host.channelAccountScope()}}
    val hidden=remember(revision){store.hiddenScopes().filter{it.account==host.channelAccountScope()}}
    var enabled by remember(revision){mutableStateOf(store.enabled)}
    var window by remember(revision){mutableStateOf(store.window)}
    fun change(action:()->Unit){try{action();revision++;error=null}catch(e:Exception){error=e.message?:"保存失败，请重试"}}
    LazyColumn(modifier.fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("会话快捷栏");Text("自动识别商户，并管理群内多个通道",color=Muted,fontSize=12.sp)};Switch(enabled,{value->change{store.enabled=value;enabled=value}})}}
        error?.let{item{Text(it,color=Danger)}}
        item{Text("默认成功率时间窗");Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(MonitorWindow.FIVE_MINUTES,MonitorWindow.HALF_HOUR,MonitorWindow.HOUR).forEach{w->FilterChip(window==w,{change{store.window=w;window=w}},label={Text(w.label)},modifier=Modifier.heightIn(min=48.dp))}};Row{listOf(MonitorWindow.SIX_HOURS,MonitorWindow.DAY).forEach{w->FilterChip(window==w,{change{store.window=w;window=w}},label={Text(w.label)},modifier=Modifier.heightIn(min=48.dp))}}}
        item{HorizontalDivider(color=Line);Text("已记住的关联（${links.size}）");Text("通道按账号、后台和群保存，群内话题共用；商户按私信或话题自动识别。回到会话点“切换”管理多个通道。",fontSize=12.sp,color=Muted)}
        items(links,key={it.first.key}){(scope,channels)->Column{Text(channels.firstOrNull()?.label?.ifBlank{"会话 ${scope.dialog}"}?:"会话 ${scope.dialog}");Text(channels.joinToString("\n"){"${it.name} #${it.id}"});Text(scope.backend,color=Muted,fontSize=12.sp);Row{TextButton(onClick={change{store.hide(scope,!store.hidden(scope))}}){Text(if(store.hidden(scope))"开启此会话" else "隐藏此会话")};TextButton(onClick={change{store.replaceChannels(scope,emptyList())}}){Text("解除关联",color=Danger)}};HorizontalDivider(color=Line)}}
        item{Text("已隐藏的入口（${hidden.size}）")}
        items(hidden,key={it.key}){scope->Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("会话 ${scope.dialog} · 话题 ${scope.topic}",fontSize=13.sp);Text(scope.backend,color=Muted,fontSize=12.sp)};TextButton(onClick={change{store.hide(scope,false)}}){Text("恢复")}}}
        if(links.isEmpty()&&hidden.isEmpty())item{Text("进入相关会话，选择通道后可在这里管理。",color=Muted)}
    }
}
