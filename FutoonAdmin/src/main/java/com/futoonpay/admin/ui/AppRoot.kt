package com.futoonpay.admin.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.data.DashboardStats
import com.futoonpay.admin.data.MerchantSearchFilters
import com.futoonpay.admin.data.OrderSearchFilters
import com.futoonpay.admin.data.SettlementSearchFilters
import com.futoonpay.admin.ui.theme.Green
import com.futoonpay.admin.ui.theme.GreenDark
import com.futoonpay.admin.ui.theme.Ink
import com.futoonpay.admin.ui.theme.Line
import com.futoonpay.admin.ui.theme.Muted

data class AppLaunchTarget(
    val tab: MainTab? = null,
    val channelId: String = "",
    val merchantId: String = "",
)

@Composable
fun AppRoot(viewModel: AppViewModel, launchTarget: AppLaunchTarget = AppLaunchTarget()) {
    val state by viewModel.state.collectAsState()
    when (state.authStage) {
        AuthStage.CHECKING -> LoadingScreen()
        AuthStage.LOGIN -> ModernLogin(state, viewModel::connectBackend, viewModel::login, viewModel::refreshCaptcha)
        AuthStage.AUTHENTICATED -> ModernMainShell(state, viewModel, launchTarget)
    }
    if (state.authStage == AuthStage.AUTHENTICATED) BatchDialog(state.batch, viewModel::startBatch, viewModel::closeBatch, viewModel::retryBatch)
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark()
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = GreenDark, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun LoginScreen(
    state: AppUiState,
    onConnect: (String) -> Unit,
    onLogin: (String, String, String) -> Unit,
    onRefreshCaptcha: () -> Unit,
) {
    var adminUrl by rememberSaveable { mutableStateOf(state.adminUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var captcha by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.adminUrl) {
        if (state.adminUrl.isNotBlank()) adminUrl = state.adminUrl
    }
    val endpointMatches = state.endpointReady && adminUrl.trim().trimEnd('/') == state.adminUrl.trimEnd('/')

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(42.dp)
                Spacer(Modifier.width(12.dp))
                Text("FUTOON PAY", fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = .3.sp)
            }
            Spacer(Modifier.height(52.dp))
            Text("登录管理中心", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("查看收款、结算与商户业务数据", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(30.dp))
            OutlinedTextField(
                value = adminUrl,
                onValueChange = { adminUrl = it },
                label = { Text("后台地址（含管理目录）") },
                placeholder = { Text("https://example.com/admin/") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                supportingText = { Text("地址仅保存在本机加密存储中") },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onConnect(adminUrl) },
                enabled = adminUrl.isNotBlank() && !state.captchaLoading && !state.loginLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (state.captchaLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (endpointMatches) "重新连接后台" else "连接后台并加载验证码")
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("管理员账号") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                enabled = endpointMatches,
                shape = RoundedCornerShape(10.dp),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, enabled = endpointMatches,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = captcha, onValueChange = { captcha = it.take(8) },
                    label = { Text("验证码") }, singleLine = true,
                    enabled = endpointMatches,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.width(10.dp))
                CaptchaImage(state.captcha, state.captchaLoading, onRefreshCaptcha)
            }
            state.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = { onLogin(username, password, captcha) },
                enabled = endpointMatches && !state.loginLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (state.loginLoading) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("继续", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(GreenDark))
                Spacer(Modifier.width(8.dp))
                Text("账号与密码仅用于本次登录，不保存在设备中", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CaptchaImage(bytes: ByteArray?, loading: Boolean, onRefresh: () -> Unit) {
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(
        modifier = Modifier.width(130.dp).height(54.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onRefresh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            bitmap != null -> Image(bitmap, contentDescription = "点击刷新验证码", modifier = Modifier.fillMaxSize())
            else -> Text("刷新验证码", color = Muted, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(state: AppUiState, viewModel: AppViewModel, launchTarget: AppLaunchTarget) {
    var selected by rememberSaveable { mutableStateOf(launchTarget.tab ?: MainTab.DASHBOARD) }
    var launchApplied by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.refreshing, launchTarget, launchApplied) {
        if (!state.refreshing && !launchApplied) {
            launchApplied = true
            when (launchTarget.tab) {
                MainTab.ORDERS -> viewModel.loadOrders(
                    1,
                    OrderSearchFilters(channelId = launchTarget.channelId, merchantId = launchTarget.merchantId),
                )
                MainTab.SETTLEMENTS -> viewModel.loadSettlements(
                    1,
                    SettlementSearchFilters(merchantId = launchTarget.merchantId),
                )
                MainTab.MERCHANTS -> viewModel.loadMerchants(
                    1,
                    MerchantSearchFilters(column = "uid", value = launchTarget.merchantId),
                )
                else -> Unit
            }
        }
    }
    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(selected.title, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        if (selected == MainTab.DASHBOARD) Text("业务概览", color = Muted, fontSize = 11.sp)
                    }
                },
                actions = {
                    if (state.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = {
                        when (selected) {
                            MainTab.DASHBOARD -> viewModel.refreshAll(true)
                            MainTab.ORDERS -> viewModel.loadOrders(state.orderPage)
                            MainTab.SETTLEMENTS -> viewModel.loadSettlements(state.settlementPage)
                            MainTab.MERCHANTS -> viewModel.loadMerchants(state.merchantPage)
                            MainTab.MORE -> viewModel.refreshAll()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Ink),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab, onClick = { selected = tab },
                        icon = { Icon(tab.icon(), contentDescription = tab.title) }, label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                MainTab.DASHBOARD -> DashboardScreen(state.dashboard)
                MainTab.ORDERS -> OrdersScreen(state, viewModel)
                MainTab.SETTLEMENTS -> SettlementsScreen(state, viewModel)
                MainTab.MERCHANTS -> MerchantsScreen(state, viewModel)
                MainTab.MORE -> MoreScreen(viewModel::logout)
            }
        }
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.DASHBOARD -> Icons.Default.Dashboard
    MainTab.ORDERS -> Icons.Default.ReceiptLong
    MainTab.SETTLEMENTS -> Icons.Default.AccountBalanceWallet
    MainTab.MERCHANTS -> Icons.Default.Groups
    MainTab.MORE -> Icons.Default.MoreHoriz
}

@Composable
private fun DashboardScreen(stats: DashboardStats?) {
    if (stats == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("今日交易额", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("¥${stats.todayTotal}", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = Line)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.todayByType.take(3).forEach { (name, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(name, color = Muted, fontSize = 11.sp, maxLines = 1)
                                Text("¥$value", color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("订单", stats.orderCount, Icons.Default.ReceiptLong, Modifier.weight(1f))
                MetricCard("商户", stats.merchantCount, Icons.Default.Groups, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("商户余额", "¥${stats.merchantBalance}", Icons.Default.Payments, Modifier.weight(1f))
                MetricCard("昨日结算", "¥${stats.yesterdaySettled}", Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
            }
        }
        item { Text("最近流水", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp)) }
        items(stats.recentDailyTotals.take(7)) { (date, value) ->
            Row(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(date, color = Muted, fontSize = 13.sp)
                Text("¥$value", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier, colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(15.dp)) {
            Icon(icon, null, tint = GreenDark, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(12.dp))
            Text(value.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun BrandMark(size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(Green), contentAlignment = Alignment.Center) {
        Text("富", color = Ink, fontWeight = FontWeight.Bold, fontSize = (size.value * .48f).sp)
    }
}
