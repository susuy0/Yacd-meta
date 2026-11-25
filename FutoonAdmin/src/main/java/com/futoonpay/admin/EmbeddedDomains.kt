package com.futoonpay.admin

import com.futoonpay.admin.ui.theme.CardColor
import com.futoonpay.admin.ui.theme.ButtonText
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import com.futoonpay.admin.ui.PayCard
import com.futoonpay.admin.ui.PaySheet
import com.futoonpay.admin.ui.PayTabs
import com.futoonpay.admin.ui.PayTopBar
import com.futoonpay.admin.ui.DataRow
import com.futoonpay.admin.ui.ListFeedback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futoonpay.admin.data.ApiResult
import com.futoonpay.admin.data.DomainAuditSummary
import com.futoonpay.admin.data.FutoonRepository
import com.futoonpay.admin.data.domainAuditStatusLabel
import com.futoonpay.admin.ui.theme.Danger
import com.futoonpay.admin.ui.theme.FutoonTheme
import com.futoonpay.admin.ui.theme.GreenDark
import com.futoonpay.admin.ui.theme.Ink
import com.futoonpay.admin.ui.theme.Line
import com.futoonpay.admin.ui.theme.Muted
import com.futoonpay.admin.ui.theme.Warning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DomainAuditState(
    val loading: Boolean = true,
    val writing: Boolean = false,
    val page: Int = 1,
    val keyword: String = "",
    val merchantId: String = "",
    val status: Int = -1,
    val total: Int = 0,
    val rows: List<DomainAuditSummary> = emptyList(),
    val message: String? = null,
    val loadError: String? = null,
)

internal class DomainAuditViewModel(
    private val repository: FutoonRepository,
    initialMerchantId: String = "",
    val lockMerchant: Boolean = false,
) : ViewModel() {
    private val lockedMerchantId=initialMerchantId
    private val _state = MutableStateFlow(DomainAuditState(merchantId = initialMerchantId))
    val state = _state.asStateFlow()
    private var feedback: String? = null

    init { load() }

    fun load(
        page: Int = _state.value.page,
        keyword: String = _state.value.keyword,
        merchantId: String = _state.value.merchantId,
        status: Int = _state.value.status,
    ) = viewModelScope.launch {
        val merchantId=if(lockMerchant)lockedMerchantId else merchantId
        _state.update { it.copy(loading = true, page = page, keyword = keyword, merchantId = merchantId, status = status, message = null, loadError = null) }
        val filters = buildMap {
            if (keyword.isNotBlank()) put("kw", keyword.trim())
            if (merchantId.isNotBlank()) put("uid", merchantId.trim())
            put("dstatus", status.toString())
        }
        runCatching { repository.domainAudits(page, filters).also { result -> check(!lockMerchant || result.rows.all{it.merchantId==lockedMerchantId}) { "后台返回了其他商户域名" } } }
            .onSuccess { result ->
                val message = feedback.also { feedback = null }
                _state.update { it.copy(loading = false, total = result.total, rows = result.rows, message = message) }
            }
            .onFailure { error -> _state.update { it.copy(loading = false, loadError = error.message ?: "域名列表加载失败", message = error.message ?: "域名列表加载失败") } }
    }

    fun add(merchantId: String, domain: String) = runAction { repository.addDomain(if(lockMerchant)lockedMerchantId else merchantId, domain) }
    fun setStatus(id: String, status: Int, remark: String, onComplete: (ApiResult) -> Unit) = runAction(onComplete) { repository.setDomainStatus(id, status, remark) }
    fun delete(id: String) = runAction { repository.deleteDomain(id) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun runAction(onComplete: (ApiResult) -> Unit = {}, action: suspend () -> ApiResult) = viewModelScope.launch {
        if (_state.value.loading) { onComplete(ApiResult(false, "请等待当前请求完成")); return@launch }
        _state.update { it.copy(loading = true, writing = true, message = null) }
        runCatching { action() }
            .onSuccess { result ->
                _state.update { it.copy(writing=false) }
                if (result.success) {
                    feedback = result.message
                    load()
                } else _state.update { it.copy(loading = false, message = result.message) }
                onComplete(result)
            }
            .onFailure { error -> _state.update { it.copy(loading = false, writing = false, message = error.message ?: "操作失败") }; onComplete(ApiResult(false, error.message ?: "操作失败")) }
    }

    class Factory(
        private val repository: FutoonRepository,
        private val initialMerchantId: String = "",
        private val lockMerchant: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DomainAuditViewModel(repository, initialMerchantId, lockMerchant) as T
    }
}

private data class PendingDomainAction(val row: DomainAuditSummary, val status: Int? = null, val delete: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DomainAuditRoot(viewModel: DomainAuditViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val mutationBusy=LocalChannelMutationBusy.current
    androidx.compose.runtime.SideEffect { mutationBusy?.invoke(state.writing) }
    androidx.compose.runtime.DisposableEffect(mutationBusy) { onDispose { mutationBusy?.invoke(false) } }
    var keyword by rememberSaveable { mutableStateOf(state.keyword) }
    var merchantId by rememberSaveable { mutableStateOf(state.merchantId) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingDomainAction?>(null) }
    var selectedDomain by remember { mutableStateOf<DomainAuditSummary?>(null) }
    var reviewLoading by remember { mutableStateOf(false) }
    var reviewError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.merchantId) { merchantId = state.merchantId }
    state.message?.let { message -> LaunchedEffect(message) { snackbar.showSnackbar(message); viewModel.consumeMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            PayTopBar("域名审核", onBack,
                actions = {
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "添加域名") }
                    if (state.loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, "刷新") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    keyword, { keyword = it }, label = { Text("域名") }, placeholder = { Text("要搜索的域名") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = com.futoonpay.admin.ui.ChatFieldColors())
            }
            item {
                OutlinedTextField(
                    merchantId, { merchantId = it }, label = { Text("商户号") }, singleLine = true, readOnly = viewModel.lockMerchant,
                    trailingIcon = { IconButton(onClick = { viewModel.load(1, keyword, merchantId, state.status) }) { Icon(Icons.Default.Search, "搜索") } },
                    modifier = Modifier.fillMaxWidth(), colors = com.futoonpay.admin.ui.ChatFieldColors())
            }
            item {
                PayTabs(listOf("-1" to "全部", "0" to "待审核", "1" to "已通过", "2" to "已拒绝"), state.status.toString()) { viewModel.load(1, keyword, merchantId, it.toInt()) }
            }
            item { Text("${state.total} 条域名记录", fontWeight = FontWeight.SemiBold) }
            itemsIndexed(state.rows, key = { index, it -> "${it.id}:$index" }) { _, row -> DomainAuditCard(row, { selectedDomain = row; reviewError = null }) { pending = it } }
            item { ListFeedback(state.loading && state.rows.isEmpty(), state.loadError, !state.loading && state.rows.isEmpty(), { viewModel.load() }) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    com.futoonpay.admin.ui.ChatOutlinedButton(shape = RoundedCornerShape(24.dp), onClick = { viewModel.load(state.page - 1, keyword, merchantId, state.status) }, enabled = state.page > 1) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null); Text("上一页")
                    }
                    Text("第 ${state.page} 页", color = Muted, fontSize = 12.sp)
                    com.futoonpay.admin.ui.ChatOutlinedButton(shape = RoundedCornerShape(24.dp),
                        onClick = { viewModel.load(state.page + 1, keyword, merchantId, state.status) },
                        enabled = state.page * FutoonRepository.DOMAIN_PAGE_SIZE < state.total && state.rows.isNotEmpty(),
                    ) { Text("下一页"); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                }
            }
        }
    }

    selectedDomain?.let { row -> PaySheet("域名审核", { selectedDomain = null }) {
        var remark by rememberSaveable(row.id) { mutableStateOf(row.remark) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PayCard {
                DataRow("域名", row.domain); DataRow("商户 UID", row.merchantId)
                DataRow("当前状态", domainAuditStatusLabel(row.status)); DataRow("提交时间", row.createdAt)
                if (row.auditedAt.isNotBlank()) DataRow("审核时间", row.auditedAt)
                if (row.projectName.isNotBlank()) DataRow("所属项目", row.projectName)
            }
            OutlinedTextField(remark, { remark = it }, label = { Text("审核备注 / 拒绝理由") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp) , colors = com.futoonpay.admin.ui.ChatFieldColors())
            reviewError?.let { Text(it, color = Danger, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fun submitReview(status: Int) { reviewLoading = true; reviewError = null; viewModel.setStatus(row.id, status, remark) { response -> reviewLoading = false; if (response.success) selectedDomain = null else reviewError = response.message } }
                com.futoonpay.admin.ui.ChatOutlinedButton(shape = RoundedCornerShape(24.dp), onClick = { submitReview(2) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = !reviewLoading && row.status != 2) { Text("拒绝", color = Danger) }
                com.futoonpay.admin.ui.ChatButton(shape = RoundedCornerShape(24.dp), onClick = { submitReview(1) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = !reviewLoading && row.status != 1) { Text(if (reviewLoading) "提交中…" else "通过审核") }
            }
        }
    } }
    if (showAdd) AddDomainDialog({ showAdd = false }, if(viewModel.lockMerchant)state.merchantId else "") { uid, domain -> viewModel.add(uid, domain); showAdd = false }
    pending?.let { action ->
        val label = when { action.delete -> "删除域名"; action.status == 1 -> "通过审核"; else -> "拒绝域名" }
        AlertDialog(shape = RoundedCornerShape(20.dp),
            onDismissRequest = { pending = null }, title = { Text(label) },
            text = { Text("${action.row.domain}\n商户 ${action.row.merchantId}\n该操作会立即更新后台审核状态。") },
            confirmButton = {
                com.futoonpay.admin.ui.ChatButton(shape = RoundedCornerShape(24.dp), onClick = {
                    if (action.delete) viewModel.delete(action.row.id) else viewModel.setStatus(action.row.id, action.status ?: 0, "") {}
                    pending = null
                }) { Text("确认") }
            },
            dismissButton = { com.futoonpay.admin.ui.ChatTextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DomainAuditCard(row: DomainAuditSummary, onReview: () -> Unit, onAction: (PendingDomainAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val color = when (row.status) { 1 -> GreenDark; 2 -> Danger; else -> Warning }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onReview), colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.domain, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("商户 ${row.merchantId} · ID ${row.id}", color = Muted, fontSize = 12.sp)
                }
                Text(
                    domainAuditStatusLabel(row.status), color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .1f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Box {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "域名操作") }
                    DropdownMenu(expanded, { expanded = false }) {
                        if (row.status != 1) DropdownMenuItem(text = { Text("通过") }, onClick = { expanded = false; onAction(PendingDomainAction(row, status = 1)) })
                        if (row.status != 2) DropdownMenuItem(text = { Text("拒绝") }, onClick = { expanded = false; onAction(PendingDomainAction(row, status = 2)) })
                        DropdownMenuItem(
                            text = { Text("删除", color = Danger) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Danger) },
                            onClick = { expanded = false; onAction(PendingDomainAction(row, delete = true)) },
                        )
                    }
                }
            }
            HorizontalDivider(color = Line)
            Text("添加时间 ${row.createdAt.ifBlank { "—" }}", color = Muted, fontSize = 12.sp)
            if (row.auditedAt.isNotBlank()) Text("审核时间 ${row.auditedAt}", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AddDomainDialog(onDismiss: () -> Unit, fixedMerchantId: String = "", onConfirm: (String, String) -> Unit) {
    var merchantId by rememberSaveable { mutableStateOf(fixedMerchantId) }
    var domain by rememberSaveable { mutableStateOf("") }
    AlertDialog(shape = RoundedCornerShape(20.dp),
        onDismissRequest = onDismiss,
        title = { Text("添加授权支付域名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(merchantId, { merchantId = it }, label = { Text("商户 ID") }, singleLine = true, readOnly = fixedMerchantId.isNotBlank() , colors = com.futoonpay.admin.ui.ChatFieldColors())
                OutlinedTextField(domain, { domain = it }, label = { Text("域名") }, supportingText = { Text("支持通配符 *") }, singleLine = true , colors = com.futoonpay.admin.ui.ChatFieldColors())
            }
        },
        confirmButton = { com.futoonpay.admin.ui.ChatButton(shape = RoundedCornerShape(24.dp), onClick = { onConfirm(merchantId.trim(), domain.trim()) }, enabled = merchantId.isNotBlank() && domain.isNotBlank()) { Text("确认添加") } },
        dismissButton = { com.futoonpay.admin.ui.ChatTextButton(onClick = onDismiss) { Text("取消") } },
    )
}
