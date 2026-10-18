package com.futoonpay.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.futoonpay.admin.PanelEnvironment as FutoonApplication
import com.futoonpay.admin.data.ApiResult
import com.futoonpay.admin.data.BatchProgress
import com.futoonpay.admin.data.BatchRequest
import com.futoonpay.admin.data.DashboardStats
import com.futoonpay.admin.data.FutoonRepository
import com.futoonpay.admin.data.MerchantSummary
import com.futoonpay.admin.data.MerchantFlowSummary
import com.futoonpay.admin.data.MerchantSearchFilters
import com.futoonpay.admin.data.OrderDetail
import com.futoonpay.admin.data.OrderChannelStat
import com.futoonpay.admin.data.OrderSummary
import com.futoonpay.admin.data.OrderSearchFilters
import com.futoonpay.admin.data.SessionExpiredException
import com.futoonpay.admin.data.SettlementSearchFilters
import com.futoonpay.admin.data.SettlementSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStage { CHECKING, LOGIN, AUTHENTICATED }
enum class MainTab(val title: String) { DASHBOARD("概览"), ORDERS("订单"), SETTLEMENTS("结算"), MERCHANTS("商户"), MORE("管理") }

data class AppUiState(
    val batch: BatchProgress? = null,
    val authStage: AuthStage = AuthStage.CHECKING,
    val adminUrl: String = "",
    val accountName: String = "管理员",
    val endpointReady: Boolean = false,
    val captcha: ByteArray? = null,
    val captchaLoading: Boolean = false,
    val loginLoading: Boolean = false,
    val refreshing: Boolean = false,
    val ordersLoading: Boolean = false,
    val ordersError: String? = null,
    val settlementsLoading: Boolean = false,
    val settlementsError: String? = null,
    val merchantsLoading: Boolean = false,
    val merchantsError: String? = null,
    val message: String? = null,
    val dashboard: DashboardStats? = null,
    val orders: List<OrderSummary> = emptyList(),
    val orderTotal: Int = 0,
    val orderPage: Int = 1,
    val orderSearch: OrderSearchFilters = OrderSearchFilters(),
    val orderDetailTradeNo: String = "",
    val orderDetail: OrderDetail? = null,
    val orderDetailLoading: Boolean = false,
    val orderChannelStats: Map<String, OrderChannelStat> = emptyMap(),
    val orderChannelStatsLoading: Boolean = false,
    val orderChannelStatsMessage: String? = null,
    val orderChannelStatsUpdatedAt: Long = 0L,
    val settlements: List<SettlementSummary> = emptyList(),
    val settlementTotal: Int = 0,
    val settlementPage: Int = 1,
    val settlementSearch: SettlementSearchFilters = SettlementSearchFilters(),
    val merchants: List<MerchantSummary> = emptyList(),
    val merchantTotal: Int = 0,
    val merchantPage: Int = 1,
    val merchantSearch: MerchantSearchFilters = MerchantSearchFilters(),
    val merchantFlows: Map<String, MerchantFlowSummary> = emptyMap(),
    val merchantFlowLoading: Boolean = false,
    val merchantFlowError: String? = null,
    val merchantDetailFlow: MerchantFlowSummary? = null,
    val merchantDetailLoading: Boolean = false,
    val merchantDetailError: String? = null,
    val merchantBalanceUpdating: Boolean = false,
    val merchantEditorUid: String = "",
    val merchantEditor: com.futoonpay.admin.data.NativeForm? = null,
    val merchantEditorLoading: Boolean = false,
    val merchantEditorSaving: Boolean = false,
    val merchantEditorError: String? = null,
)

data class OrderCommand(
    val action: String,
    val tradeNo: String,
    val status: Int? = null,
    val amount: String = "",
    val paymentPassword: String = "",
    val remark: String = "",
)

data class SettlementCommand(
    val action: String,
    val id: String,
    val result: String = "",
    val payType: String = "",
    val account: String = "",
    val accountName: String = "",
    val merchantId: String = "",
    val usdtRate: String = "",
    val usdtAmount: String = "",
    val status: Int? = null,
)

class AppViewModel(
    private val application: FutoonApplication,
    private val repositoryOverride: FutoonRepository? = null,
    loadOnStart: Boolean = true,
) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val repository: FutoonRepository get() = repositoryOverride ?: application.repository
    private var orderChannelStatsGeneration = 0L
    private var ordersGeneration = 0L
    private var settlementsGeneration = 0L
    private var merchantsGeneration = 0L
    private var merchantFlowsGeneration = 0L
    private var merchantDetailGeneration = 0L
    private var merchantEditorGeneration = 0L
    private var originalMerchantEditor: com.futoonpay.admin.data.NativeForm? = null
    private var batchJob: kotlinx.coroutines.Job? = null

    fun offerBatch(request: BatchRequest) {
        if (_state.value.batch?.running == true || _state.value.refreshing || _state.value.merchantEditorSaving || _state.value.merchantBalanceUpdating) return
        if (request.commands.isEmpty()) return
        _state.update { it.copy(batch = BatchProgress(request.copy(commands = request.commands.distinctBy { command -> command.id }))) }
    }

    fun startBatch() {
        val current = _state.value.batch ?: return
        if (current.started || _state.value.refreshing || _state.value.merchantBalanceUpdating || _state.value.merchantEditorSaving) return
        val request = current.request
        _state.update { it.copy(batch = current.copy(started = true, running = true), refreshing = true) }
        batchJob = viewModelScope.launch {
            try {
                repository.runBatch(request.commands) { results -> _state.update {
                    if (it.batch?.request === request) it.copy(batch = it.batch.copy(results = results)) else it
                } }
            } finally {
                _state.update { if (it.batch?.request === request) it.copy(batch = it.batch.copy(running = false), refreshing = false) else it }
            }
        }
    }

    fun closeBatch() {
        val batch = _state.value.batch ?: return
        if (batch.running) return
        _state.update { it.copy(batch = null) }
        if (batch.results.any { it.status in setOf(com.futoonpay.admin.data.BatchOutcomeStatus.SUCCESS, com.futoonpay.admin.data.BatchOutcomeStatus.UNCERTAIN) }) {
            when (batch.request.module) { "orders" -> loadOrders(_state.value.orderPage); "settlements" -> loadSettlements(_state.value.settlementPage); "merchants" -> loadMerchants(_state.value.merchantPage) }
        }
    }

    fun retryBatch() {
        val batch = _state.value.batch ?: return
        if (batch.running || batch.retryable.isEmpty()) return
        _state.update { it.copy(batch = BatchProgress(batch.request.copy(commands = batch.retryable, warning = "仅重新执行明确失败或未执行的记录。结果待核对项不会重试。请核对后确认。"))) }
    }

    fun openMerchantEditor(uid: String) = viewModelScope.launch {
        if (_state.value.merchantEditorSaving) return@launch
        val generation = ++merchantEditorGeneration
        originalMerchantEditor = null
        _state.update { it.copy(merchantEditorUid = uid, merchantEditor = null, merchantEditorLoading = true, merchantEditorError = null) }
        runCatching { repository.merchantProfile(uid) }.onSuccess { form ->
            if (generation != merchantEditorGeneration) return@onSuccess
            originalMerchantEditor = form
            _state.update { it.copy(merchantEditor = form, merchantEditorLoading = false) }
        }.onFailure { error ->
            if (generation == merchantEditorGeneration) _state.update { it.copy(merchantEditorLoading = false, merchantEditorError = error.userMessage()) }
            handleFailure(error)
        }
    }

    fun editMerchantField(name: String, value: String) {
        if (_state.value.merchantEditorSaving) return
        _state.update { state -> state.copy(merchantEditor = state.merchantEditor?.let { form -> form.copy(fields = form.fields.map { if (it.name == name && !it.readOnly && it.type != "hidden") it.copy(value = value) else it }) }, merchantEditorError = null) }
    }

    fun closeMerchantEditor() {
        if (_state.value.merchantEditorSaving) return
        merchantEditorGeneration++
        originalMerchantEditor = null
        _state.update { it.copy(merchantEditorUid = "", merchantEditor = null, merchantEditorLoading = false, merchantEditorError = null) }
    }

    fun saveMerchantEditor() = viewModelScope.launch {
        if (_state.value.merchantEditorSaving) return@launch
        val original = originalMerchantEditor ?: return@launch
        val snapshot = _state.value
        val values = snapshot.merchantEditor?.fields?.associate { it.name to it.value } ?: return@launch
        _state.update { it.copy(merchantEditorSaving = true, merchantEditorError = null) }
        runCatching { repository.saveMerchantProfile(snapshot.merchantEditorUid, original, values) }.onSuccess { result ->
            _state.update { it.copy(merchantEditorSaving = false, merchantEditorError = if (result.success) null else result.message) }
            if (result.success) {
                closeMerchantEditor()
                _state.update { it.copy(message = "商户资料已保存") }
                loadMerchants(snapshot.merchantPage, snapshot.merchantSearch)
            }
        }.onFailure { error ->
            _state.update { it.copy(merchantEditorSaving = false, merchantEditorError = error.userMessage()) }
            handleFailure(error)
        }
    }

    init {
        if (loadOnStart) bootstrap()
        viewModelScope.launch {
            application.sessionExpired.collect { expired ->
                if (expired) {
                    batchJob?.cancel()
                    _state.update { AppUiState(authStage = AuthStage.LOGIN, adminUrl = it.adminUrl, endpointReady = it.endpointReady, message = "登录已过期，请重新登录") }
                    if (loadOnStart) refreshCaptcha()
                }
            }
        }
    }

    private fun bootstrap() = viewModelScope.launch {
        if (!application.hasConfiguredEndpoint) {
            _state.update { it.copy(authStage = AuthStage.LOGIN, endpointReady = false) }
            return@launch
        }
        _state.update { it.copy(adminUrl = application.adminBaseUrl, endpointReady = true) }
        val valid = runCatching { repository.sessionIsValid() }.getOrDefault(false)
        if (valid) {
            _state.update { it.copy(authStage = AuthStage.AUTHENTICATED) }
            loadOrders()
        } else {
            _state.update { it.copy(authStage = AuthStage.LOGIN) }
            refreshCaptcha()
        }
    }

    fun connectBackend(rawUrl: String) = viewModelScope.launch {
        _state.update { it.copy(captchaLoading = true, loginLoading = false, message = null) }
        application.configureEndpoint(rawUrl)
            .onSuccess { normalized ->
                _state.update {
                    AppUiState(
                        authStage = AuthStage.LOGIN,
                        adminUrl = normalized,
                        endpointReady = true,
                        captchaLoading = true,
                    )
                }
                refreshCaptcha()
            }
            .onFailure { error ->
                _state.update {
                    it.copy(endpointReady = application.hasConfiguredEndpoint, captchaLoading = false, message = error.userMessage())
                }
            }
    }

    fun refreshCaptcha() = viewModelScope.launch {
        if (!application.hasConfiguredEndpoint) {
            _state.update { it.copy(endpointReady = false, message = "请先填写并连接后台地址") }
            return@launch
        }
        _state.update { it.copy(captchaLoading = true, message = null) }
        runCatching { repository.captcha() }
            .onSuccess { bytes -> _state.update { it.copy(captcha = bytes, captchaLoading = false) } }
            .onFailure { error -> _state.update { it.copy(captchaLoading = false, message = error.userMessage()) } }
    }

    fun login(username: String, password: String, captcha: String) = viewModelScope.launch {
        if (username.isBlank() || password.isBlank() || captcha.isBlank()) {
            _state.update { it.copy(message = "请填写用户名、密码和验证码") }
            return@launch
        }
        _state.update { it.copy(loginLoading = true, message = null) }
        runCatching { repository.login(username.trim(), password, captcha.trim()) }
            .onSuccess { result ->
                if (result.success) {
                    application.markAuthenticated()
                    _state.update { it.copy(authStage = AuthStage.AUTHENTICATED, accountName = username.trim(), loginLoading = false, message = null) }
                    loadOrders()
                } else {
                    _state.update { it.copy(loginLoading = false, message = result.message) }
                    refreshCaptcha()
                }
            }
            .onFailure { error ->
                _state.update { it.copy(loginLoading = false, message = error.userMessage()) }
                refreshCaptcha()
            }
    }

    fun refreshAll(forceDashboard: Boolean = false) = viewModelScope.launch {
        // Load modules independently: one failed endpoint must not blank every page.
        _state.update { it.copy(refreshing = true, message = null) }
        coroutineScope {
            val dashboard = async { runCatching { repository.dashboard(forceDashboard) } }
            val orders = loadOrders(1, _state.value.orderSearch)
            val settlements = loadSettlements(1, _state.value.settlementSearch)
            val merchants = loadMerchants(1, _state.value.merchantSearch)
            dashboard.await().onSuccess { result -> _state.update { it.copy(dashboard = result) } }.onFailure(::handleFailure)
            orders.join(); settlements.join(); merchants.join()
        }
        _state.update { it.copy(refreshing = false) }
    }

    fun loadOrders(page: Int = 1, search: OrderSearchFilters = _state.value.orderSearch) = viewModelScope.launch {
        val generation = ++ordersGeneration
        orderChannelStatsGeneration += 1
        _state.update { it.copy(refreshing = true, ordersLoading = true, ordersError = null, message = null, orderSearch = search) }
        runCatching { repository.orders(page, search.toRequest()) }
            .onSuccess { result ->
                if (generation != ordersGeneration) return@onSuccess
                _state.update {
                    it.copy(
                        refreshing = false, ordersLoading = false, ordersError = null, orders = result.rows, orderTotal = result.total, orderPage = page,
                        orderChannelStats = emptyMap(), orderChannelStatsMessage = null,
                    )
                }
                loadOrderChannelStats(result.rows)
            }
            .onFailure { error ->
                if (generation != ordersGeneration) return@onFailure
                _state.update { it.copy(ordersLoading = false, orderChannelStatsLoading = false, ordersError = error.userMessage()) }; handleFailure(error)
            }
    }

    fun loadOrderChannelStats(orders: List<OrderSummary> = _state.value.orders) = viewModelScope.launch {
        if (orders.isEmpty()) {
            orderChannelStatsGeneration += 1
            _state.update {
                it.copy(orderChannelStats = emptyMap(), orderChannelStatsLoading = false, orderChannelStatsMessage = null)
            }
            return@launch
        }
        val generation = ++orderChannelStatsGeneration
        _state.update { it.copy(orderChannelStatsLoading = true, orderChannelStatsMessage = null) }
        runCatching { repository.orderChannelStats(orders) }
            .onSuccess { result ->
                if (generation != orderChannelStatsGeneration) return@onSuccess
                _state.update {
                    it.copy(
                        orderChannelStats = result.stats,
                        orderChannelStatsLoading = false,
                        orderChannelStatsMessage = result.message.takeIf { result.fallbackUsed },
                        orderChannelStatsUpdatedAt = System.currentTimeMillis(),
                    )
                }
            }
            .onFailure { error ->
                if (generation != orderChannelStatsGeneration) return@onFailure
                _state.update {
                    it.copy(
                        orderChannelStatsLoading = false,
                        orderChannelStatsMessage = "通道统计加载失败：${error.userMessage()}",
                    )
                }
            }
    }

    fun loadOrderDetail(tradeNo: String) = viewModelScope.launch {
        _state.update {
            it.copy(orderDetailTradeNo = tradeNo, orderDetail = null, orderDetailLoading = true, message = null)
        }
        runCatching { repository.orderDetail(tradeNo) }
            .onSuccess { detail -> _state.update { it.copy(orderDetail = detail, orderDetailLoading = false) } }
            .onFailure { error ->
                _state.update { it.copy(orderDetailLoading = false, message = error.userMessage()) }
            }
    }

    fun dismissOrderDetail() = _state.update {
        it.copy(orderDetailTradeNo = "", orderDetail = null, orderDetailLoading = false)
    }

    fun loadSettlements(page: Int = 1, search: SettlementSearchFilters = _state.value.settlementSearch) = viewModelScope.launch {
        val generation = ++settlementsGeneration
        _state.update { it.copy(refreshing = true, settlementsLoading = true, settlementsError = null, message = null, settlementSearch = search) }
        runCatching { repository.settlements(page, search.toRequest()) }
            .onSuccess { result ->
                if (generation != settlementsGeneration) return@onSuccess
                _state.update { it.copy(refreshing = false, settlementsLoading = false, settlementsError = null, settlements = result.rows, settlementTotal = result.total, settlementPage = page) }
            }
            .onFailure { error ->
                if (generation != settlementsGeneration) return@onFailure
                _state.update { it.copy(settlementsLoading = false, settlementsError = error.userMessage()) }; handleFailure(error)
            }
    }

    fun loadMerchants(page: Int = 1, search: MerchantSearchFilters = _state.value.merchantSearch) = viewModelScope.launch {
        val generation = ++merchantsGeneration
        merchantFlowsGeneration += 1
        _state.update { it.copy(refreshing = true, merchantsLoading = true, merchantsError = null, message = null, merchantSearch = search) }
        runCatching { repository.merchants(page, search.toRequest()) }
            .onSuccess { result ->
                if (generation != merchantsGeneration) return@onSuccess
                _state.update {
                    it.copy(
                        refreshing = false, merchantsLoading = false, merchantsError = null, merchants = result.rows, merchantTotal = result.total,
                        merchantPage = page, merchantFlows = emptyMap(), merchantFlowError = null,
                    )
                }
                loadMerchantFlows(result.rows.map { it.uid })
            }
            .onFailure { error ->
                if (generation != merchantsGeneration) return@onFailure
                _state.update { it.copy(merchantsLoading = false, merchantFlowLoading = false, merchantsError = error.userMessage()) }; handleFailure(error)
            }
    }

    fun loadMerchantFlows(merchantIds: List<String> = _state.value.merchants.map { it.uid }) = viewModelScope.launch {
        val generation = ++merchantFlowsGeneration
        val ids = merchantIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) {
            _state.update { it.copy(merchantFlows = emptyMap(), merchantFlowLoading = false, merchantFlowError = null) }
            return@launch
        }
        _state.update { it.copy(merchantFlowLoading = true, merchantFlowError = null) }
        runCatching { repository.merchantFlows(ids) }
            .onSuccess { flows ->
                if (generation != merchantFlowsGeneration) return@onSuccess
                _state.update { it.copy(merchantFlows = flows, merchantFlowLoading = false, merchantFlowError = null) }
            }
            .onFailure { error ->
                if (generation != merchantFlowsGeneration) return@onFailure
                _state.update { it.copy(merchantFlowLoading = false, merchantFlowError = error.userMessage()) }
            }
    }

    fun loadMerchantDetailFlow(uid: String, days: Int) = viewModelScope.launch {
        val generation = ++merchantDetailGeneration
        _state.update { it.copy(merchantDetailLoading = true, merchantDetailFlow = null, merchantDetailError = null) }
        runCatching { repository.merchantFlows(listOf(uid), days = days) }.onSuccess { result ->
            if (generation == merchantDetailGeneration) _state.update { it.copy(merchantDetailLoading = false, merchantDetailFlow = result[uid]) }
        }.onFailure { error -> if (generation == merchantDetailGeneration) _state.update { it.copy(merchantDetailLoading = false, merchantDetailError = error.userMessage()) } }
    }

    fun runOrderAction(command: OrderCommand, onComplete: (com.futoonpay.admin.data.ApiResult) -> Unit = {}) = viewModelScope.launch {
        if (_state.value.refreshing) { onComplete(com.futoonpay.admin.data.ApiResult(false, "请等待当前操作完成")); return@launch }
        _state.update { it.copy(refreshing = true, message = null) }
        runCatching {
            when (command.action) {
                "setStatus" -> repository.setOrderStatus(command.tradeNo, command.status ?: 0)
                "refund" -> repository.manualRefund(command.tradeNo, command.amount, command.remark)
                "apirefund" -> repository.apiRefund(command.tradeNo, command.amount, command.paymentPassword, command.remark)
                else -> repository.orderAction(command.action, command.tradeNo)
            }
        }
            .onSuccess { result ->
                _state.update { it.copy(refreshing = false, message = result.message) }
                if (result.success) loadOrders(_state.value.orderPage)
                onComplete(result)
            }
            .onFailure { handleFailure(it); onComplete(com.futoonpay.admin.data.ApiResult(false, it.userMessage())) }
    }

    fun runSettlementAction(command: SettlementCommand, onComplete: (com.futoonpay.admin.data.ApiResult) -> Unit = {}) = viewModelScope.launch {
        if (_state.value.refreshing) { onComplete(com.futoonpay.admin.data.ApiResult(false, "请等待当前操作完成")); return@launch }
        _state.update { it.copy(refreshing = true, message = null) }
        runCatching {
            when (command.action) {
                "setStatus" -> {
                val targetStatus = command.status ?: command.result.toIntOrNull() ?: return@runCatching ApiResult(false, "请选择结算状态")
                if (targetStatus == 3 && command.status != null && command.result.isBlank()) return@runCatching ApiResult(false, "请填写失败理由")
                if (targetStatus == 3 && command.status != null) return@runCatching repository.failSettlementWithReason(command.id, command.result, command.merchantId, command.usdtRate, command.usdtAmount)
                val updated = repository.setSettlementStatus(
                    command.id,
                    targetStatus,
                    command.merchantId,
                    command.usdtRate,
                    command.usdtAmount,
                )
                updated
                }
                "settle_setresult" -> repository.settlementAction(command.action, command.id, mapOf("result" to command.result))
                "settle_save" -> repository.saveSettlementInfo(command.id, command.payType, command.account, command.accountName)
                else -> repository.settlementAction(command.action, command.id)
            }
        }.onSuccess { result ->
            _state.update { it.copy(refreshing = false, message = result.message) }
            if (result.success) loadSettlements(_state.value.settlementPage)
            onComplete(result)
        }.onFailure { handleFailure(it); onComplete(com.futoonpay.admin.data.ApiResult(false, it.userMessage())) }
    }

    fun setMerchantStatus(uid: String, type: String, enabled: Boolean) = viewModelScope.launch {
        if (_state.value.refreshing) return@launch
        _state.update { it.copy(refreshing = true, message = null) }
        runCatching { repository.setMerchantStatus(uid, type, enabled) }
            .onSuccess { result ->
                _state.update { it.copy(refreshing = false, message = result.message) }
                if (result.success) loadMerchants(_state.value.merchantPage)
            }.onFailure(::handleFailure)
    }

    fun rechargeMerchant(uid: String, operation: String, amount: String, onComplete: (ApiResult) -> Unit = {}) = viewModelScope.launch {
        if (_state.value.batch?.running == true) { onComplete(ApiResult(false, "请等待批量操作完成")); return@launch }
        if (_state.value.merchantBalanceUpdating) {
            onComplete(ApiResult(false, "余额操作正在提交，请勿重复点击"))
            return@launch
        }
        _state.update { it.copy(merchantBalanceUpdating = true) }
        _state.update { it.copy(refreshing = true, message = null) }
        runCatching { repository.rechargeMerchant(uid, operation, amount) }
            .onSuccess { result ->
                _state.update { it.copy(refreshing = false, merchantBalanceUpdating = false, message = result.message) }
                if (result.success) loadMerchants(_state.value.merchantPage)
                onComplete(result)
            }.onFailure {
                _state.update { state -> state.copy(merchantBalanceUpdating = false) }
                handleFailure(it)
                onComplete(ApiResult(false, it.userMessage()))
            }
    }

    fun setMerchantGroup(uid: String, gid: String, endTime: String) = viewModelScope.launch {
        _state.update { it.copy(refreshing = true, message = null) }
        runCatching { repository.setMerchantGroup(uid, gid, endTime) }
            .onSuccess { result ->
                _state.update { it.copy(refreshing = false, message = result.message) }
                if (result.success) loadMerchants(_state.value.merchantPage)
            }.onFailure(::handleFailure)
    }

    fun logout() = viewModelScope.launch {
        batchJob?.cancel()
        repository.logout()
        _state.value = AppUiState(
            authStage = AuthStage.LOGIN,
            adminUrl = application.adminBaseUrl,
            endpointReady = application.hasConfiguredEndpoint,
        )
        refreshCaptcha()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun handleFailure(error: Throwable) {
        if (error is SessionExpiredException) {
            _state.update { it.copy(authStage = AuthStage.LOGIN, refreshing = false, message = error.message) }
            refreshCaptcha()
        } else {
            _state.update { it.copy(refreshing = false, message = error.userMessage()) }
        }
    }

    private fun Throwable.userMessage() = message?.takeIf { it.isNotBlank() } ?: "网络请求失败，请稍后重试"

    private data class Snapshot(
        val dashboard: DashboardStats,
        val orders: com.futoonpay.admin.data.PagedResult<OrderSummary>,
        val settlements: com.futoonpay.admin.data.PagedResult<SettlementSummary>,
        val merchants: com.futoonpay.admin.data.PagedResult<MerchantSummary>,
    )

    class Factory(private val application: FutoonApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(application) as T
    }
}
