package com.futoonpay.admin.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.BuildConfig
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.theme.*

@Composable
internal fun ModernLogin(state: AppUiState, onConnect: (String) -> Unit,
    onLogin: (String, String, String) -> Unit, onCaptcha: () -> Unit) {
    val saved = remember(state.adminUrl) { BackendAddress.fromUrl(state.adminUrl) }
    var origin by rememberSaveable { mutableStateOf(saved.origin) }
    var path by rememberSaveable { mutableStateOf(saved.path) }
    var username by rememberSaveable { mutableStateOf("") }
    // Password stays in memory rather than saved-instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var captcha by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.adminUrl) { if (state.adminUrl.isNotBlank()) { origin = saved.origin; path = saved.path } }
    LaunchedEffect(state.loginLoading, state.message) { if (!state.loginLoading && state.message != null) { password = ""; captcha = ""; visible = false } }
    val address = runCatching { composeBackendAddress(origin, path) }.getOrNull()
    val connected = state.endpointReady && address == state.adminUrl
    val busy = state.loginLoading || state.captchaLoading
    Column(Modifier.fillMaxSize().background(Mist).imePadding()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Green, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("F", color = ButtonText, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                }
                Column { Text("FUTOON PAY", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("管理端", color = Muted, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(16.dp))
            Text("登录后台", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("先填写官网地址和后台路径，再输入账号密码", color = Muted, fontSize = 13.sp)
        }
        item {
            PayCard {
                LoginField("官网域名或基础地址", origin, { origin = it; error = null }, "https://pay.example.com", KeyboardType.Uri)
                Text("包含 https://，不带路径", color = Muted, fontSize = 12.sp)
                LoginField("后台路径", path, { path = it; error = null }, "/admin", KeyboardType.Uri)
                Text("以 / 开头，可修改，保存在本机", color = Muted, fontSize = 12.sp)
                if (!connected) com.futoonpay.admin.ui.ChatOutlinedButton(shape = RoundedCornerShape(24.dp), onClick = {
                    runCatching { composeBackendAddress(origin, path) }.onSuccess(onConnect).onFailure { error = it.message }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(if (busy) "正在连接…" else "连接后台") }
            }
        }
        item {
            PayCard {
                LoginField("账号", username, { username = it }, "管理员账号", KeyboardType.Text, connected)
                Text("密码", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(password, { password = it }, singleLine = true, enabled = connected,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "显示或隐藏密码") } } , colors = com.futoonpay.admin.ui.ChatFieldColors())
                if (connected) {
                    Text("验证码", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(captcha, { captcha = it.take(8) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp) , colors = com.futoonpay.admin.ui.ChatFieldColors())
                        val image = remember(state.captcha) { state.captcha?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                        Box(Modifier.width(112.dp).height(52.dp).background(Mist, RoundedCornerShape(10.dp)).clickable(onClick = onCaptcha), contentAlignment = Alignment.Center) {
                            if (state.captchaLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else if (image != null) Image(image, "点击刷新验证码", Modifier.fillMaxSize())
                            else Text("刷新验证码", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        (error ?: state.message)?.let { item { Text(it, color = Danger, fontSize = 13.sp) } }
    }
        Column(Modifier.fillMaxWidth().background(CardColor).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            com.futoonpay.admin.ui.ChatButton(shape = RoundedCornerShape(24.dp), onClick = { onLogin(username, password, captcha) },
                enabled = connected && !busy && username.isNotBlank() && password.isNotBlank() && captcha.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (state.loginLoading) CircularProgressIndicator(Modifier.size(20.dp), color = ButtonText, strokeWidth = 2.dp) else Text("登录", fontWeight = FontWeight.SemiBold)
            }
            Text("版本 ${BuildConfig.VERSION_NAME} · ${"com.futoonpay.vinechat.beta"}", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LoginField(label: String, value: String, onChange: (String) -> Unit, placeholder: String,
    keyboard: KeyboardType, enabled: Boolean = true) {
    Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    OutlinedTextField(value, onChange, singleLine = true, enabled = enabled,
        placeholder = { Text(placeholder, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(14.dp) , colors = com.futoonpay.admin.ui.ChatFieldColors())
}

@Composable
internal fun ModernManage(onModule: (String) -> Unit, onDomains: () -> Unit, onSettings: () -> Unit) {
    val groups = listOf(
        "支付配置" to listOf(Triple("支付通道", "通道、账户配置、测试支付", "channels"), Triple("支付方式", "费率、关联通道", "payment_types"), Triple("轮询组", "选路策略、成员通道", "rolls")),
        "监测与网络" to listOf(Triple("通道监测", "成功率、检测结果、手动检测", "channel_monitor"), Triple("中转代理", "代理地址、启停", "proxies")),
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { (name, entries) ->
            item { SectionLabel(name) }
            item { PayCard { entries.forEachIndexed { i, (title, subtitle, id) ->
                MenuRow(title, subtitle, when (id) { "channels" -> Icons.Outlined.Power; "payment_types" -> Icons.Outlined.Payments; "rolls" -> Icons.Outlined.AccountTree; "channel_monitor" -> Icons.Outlined.Insights; else -> Icons.Outlined.Dns }, { onModule(id) })
                if (i != entries.lastIndex) HorizontalDivider(color = Line)
            } } }
        }
        item { SectionLabel("审核与账号") }
        item { PayCard {
            MenuRow("域名审核", "查看与处理支付域名", Icons.Outlined.Language, onDomains)
            HorizontalDivider(color = Line)
            MenuRow("设置", "后台地址、退出登录、版本", Icons.Outlined.Settings, onSettings)
        } }
        item { SectionLabel("其他业务") }
        item { PayCard {
            MenuRow("资金明细", "余额变化与交易流水", Icons.Outlined.AccountBalanceWallet, { onModule("funds") })
            HorizontalDivider(color = Line)
            MenuRow("风控记录", "请求与风险命中", Icons.Outlined.Shield, { onModule("risk") })
            HorizontalDivider(color = Line)
            MenuRow("支付黑名单", "添加与移除拦截规则", Icons.Outlined.Block, { onModule("blacklist") })
        } }
        item { SectionLabel("运营与系统") }
        item { PayCard {
            val additional = listOf("商户统计" to "merchant_stats", "5 日无流水商户" to "inactive", "分账接收方" to "profit_receivers", "分账记录" to "profit_orders", "企业付款" to "transfer", "公告管理" to "announcements", "支付与结算设置" to "payment_settings", "站点与账号设置" to "site_settings", "公众号与证书" to "integrations", "登录日志" to "login_logs")
            additional.forEachIndexed { index, (label, id) ->
                MenuRow(label, "原生管理", Icons.Outlined.Settings, { onModule(id) })
                if (index < additional.lastIndex) HorizontalDivider(color = Line)
            }
        } }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Muted, modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium); Text(subtitle, color = Muted, fontSize = 13.sp) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun DataRow(label: String, value: String) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    androidx.compose.foundation.text.selection.SelectionContainer {
        if (large) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, fontSize = 13.sp)
            Text(value.ifBlank { "—" }, fontSize = 15.sp)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.widthIn(min = 72.dp, max = 110.dp))
            Text(value.ifBlank { "—" }, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SettingsScreen(state: AppUiState, onBack: () -> Unit, onSave: (String) -> Unit, onLogout: () -> Unit) {
    var edit by rememberSaveable { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    val backend = remember(state.adminUrl) { BackendAddress.fromUrl(state.adminUrl) }
    var origin by rememberSaveable { mutableStateOf(backend.origin) }
    var path by rememberSaveable { mutableStateOf(backend.path) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { if (edit) edit = false else onBack() }
    Scaffold(containerColor = Mist, topBar = { PayTopBar(if (edit) "更换后台地址" else "设置", { if (edit) edit = false else onBack() }) }, bottomBar = {
        if (edit) Surface(color = CardColor) { Row(Modifier.imePadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.futoonpay.admin.ui.ChatOutlinedButton(shape = RoundedCornerShape(24.dp), onClick = { edit = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
            com.futoonpay.admin.ui.ChatButton(shape = RoundedCornerShape(24.dp), onClick = { runCatching { composeBackendAddress(origin, path) }.onSuccess(onSave).onFailure { error = it.message } }, modifier = Modifier.weight(2f).heightIn(min = 48.dp)) { Text("保存并重新登录") }
        } }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (edit) {
                item { Text("保存后会退出当前登录，用新地址重新登录", color = Muted, fontSize = 13.sp) }
                item { PayCard {
                    LoginField("官网域名或基础地址", origin, { origin = it; error = null }, "https://pay.example.com", KeyboardType.Uri)
                    Text("包含 https://，不带路径", color = Muted, fontSize = 12.sp)
                    LoginField("后台路径", path, { path = it; error = null }, "/admin", KeyboardType.Uri)
                    Text("以 / 开头，可修改", color = Muted, fontSize = 12.sp)
                } }
                error?.let { item { Text(it, color = Danger) } }
            } else {
                item { SectionLabel("当前账号") }
                item { PayCard { DataRow("账号", state.accountName); DataRow("登录状态", "已登录") } }
                item { SectionLabel("后台地址") }
                item { PayCard {
                    DataRow("官网地址", backend.origin); DataRow("后台路径", backend.path)
                    HorizontalDivider(color = Line)
                    com.futoonpay.admin.ui.ChatTextButton(onClick = { edit = true }, modifier = Modifier.fillMaxWidth()) { Text("更换后台地址 ›") }
                    Text("更换后需要重新登录", color = Muted, fontSize = 12.sp)
                } }
                item { SectionLabel("版本信息") }
                item { PayCard { DataRow("应用版本", BuildConfig.VERSION_NAME); DataRow("包名", "com.futoonpay.vinechat.beta") } }
                item { com.futoonpay.admin.ui.ChatOutlinedButton(shape = RoundedCornerShape(24.dp), onClick = { logout = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("退出登录", color = Danger) } }
            }
        }
    }
    if (logout) AlertDialog(shape = RoundedCornerShape(20.dp), onDismissRequest = { logout = false }, title = { Text("退出登录") }, text = { Text("后台地址保留在本机，下次登录无需重复填写。") },
        confirmButton = { com.futoonpay.admin.ui.ChatTextButton(onClick = onLogout) { Text("退出登录", color = Danger) } }, dismissButton = { com.futoonpay.admin.ui.ChatTextButton(onClick = { logout = false }) { Text("取消") } })
}
