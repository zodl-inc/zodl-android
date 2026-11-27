package com.flexa.spend.main.main_screen

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.flexa.core.Flexa
import com.flexa.core.entity.Account
import com.flexa.core.entity.AppAccount
import com.flexa.core.entity.CommerceSession
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.Notification
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.SseEvent
import com.flexa.core.entity.error.ApiException
import com.flexa.core.shared.ApiErrorHandler
import com.flexa.core.shared.Brand
import com.flexa.core.shared.ConnectionState
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.shared.SelectedAsset
import com.flexa.spend.R
import com.flexa.spend.Spend
import com.flexa.spend.containsAuthorization
import com.flexa.spend.coveredByFlexaAccount
import com.flexa.spend.domain.CommerceSessionWorker
import com.flexa.spend.domain.ISpendInteractor
import com.flexa.spend.hasAnotherAsset
import com.flexa.spend.isClosed
import com.flexa.spend.isCompleted
import com.flexa.spend.isCurrent
import com.flexa.spend.isValid
import com.flexa.spend.requiresApproval
import com.flexa.spend.toBrandSession
import com.flexa.spend.toTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val ZERO = "0"
private const val RETRY_COUNT = 5
private const val RETRY_DELAY = 1000L
private const val COMPLETE_SESSION_TIMEOUT = 60_000L

class SpendViewModel(
    private val interactor: ISpendInteractor = Spend.interactor,
    private val selectedAsset: StateFlow<SelectedAsset?> = MutableStateFlow(null),
) : ViewModel() {

    companion object {
        val eventFlow = MutableSharedFlow<Event>()
    }

    val confirmViewModelStore = ViewModelStore()
    private val _selectedBrand = MutableStateFlow<Brand?>(null)
    val selectedBrand = _selectedBrand
    private val _progressState = MutableStateFlow<String?>(null)
    val progressState = _progressState.asStateFlow()
    private val _progress = MutableStateFlow(false)
    val progress: StateFlow<Boolean> = _progress
    private val _timeout = MutableStateFlow(false)
    val timeout: StateFlow<Boolean> = _timeout
    private val _unitOfAccount = MutableStateFlow("iso4217/USD")
    val unitOfAccount = _unitOfAccount.asStateFlow()
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account
    private val _notifications = mutableStateListOf<Notification>()
    val notifications: List<Notification> = _notifications
    private val mutex = Mutex()

    private val _commerceSession = MutableStateFlow<CommerceSession?>(null)
    val commerceSession = _commerceSession
        .onStart {
            listenConnection()
            listenFlexaAppAccounts()
            listenCommerceSession()
            listenTransactionSent()
            listenTransactionFailed()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(3000),
            initialValue = null
        )

    var sheetScreen by mutableStateOf<SheetScreen>(SheetScreen.Assets())
    val errorHandler = ApiErrorHandler()
    val openLegacyCard = MutableStateFlow(false)

    override fun onCleared() {
        cancelTimeout()
        confirmViewModelStore.clear()
        super.onCleared()
    }

    internal fun setBrand(brand: Brand?) {
        _selectedBrand.value = brand
    }

    internal fun stopProgress() {
        _progressState.value = null
        _progress.value = false
    }

    internal fun deleteCommerceSessionData() {
        deleteLastSessionId()
        _commerceSession.value = null
        viewModelScope.launch {
            eventFlow.emit(Event.CommerceSessionUpdate(null))
        }
    }

    private var brandSessionId = MutableStateFlow<String?>(null)

    internal fun createCommerceSession(
        brandId: String,
        amount: String,
        paymentAssetId: String
    ) {
        viewModelScope.launch {
            flow {
                val unitOfAccount = interactor.getUnitOfAccount()
                emit(
                    interactor.createCommerceSession(
                        brandId, amount, unitOfAccount, paymentAssetId
                    )
                )
            }
                .filter { it.isValid() }
                .catch { ex ->
                    stopProgress()
                    when (ex) {
                        is ApiException -> errorHandler.setApiError(ex)
                        else -> errorHandler.setError(ex)
                    }
                }
                .retryWhen { _, attempt ->
                    delay(RETRY_DELAY)
                    attempt < RETRY_COUNT
                }
                .onStart { startBrandProgress() }
                .onEach { session ->
                    brandSessionId.value = session.id
                    interactor.deleteOutdatedSessions()
                    initCloseSessionTimeout()
                }
                .collect { session ->
                    saveBrandSession(session)
                    val cs = session.copy(isLegacy = true)
                    saveLastSessionId(cs.id)
                    _commerceSession.emit(CommerceSession(data = cs))
                }
        }
    }

    private var timeoutJob: Job? = null
    internal fun initCloseSessionTimeout() {
        timeoutJob = viewModelScope.launch {
            _timeout.value = false
            delay(COMPLETE_SESSION_TIMEOUT)
            if (isActive) {
                _timeout.value = true
            }
        }
    }

    internal fun cancelTimeout() {
        _timeout.value = false
        timeoutJob?.cancel()
    }

    internal fun closeCommerceSession(context: Context, sessionId: String) {
        deleteLastSessionId()
        stopProgress()
        cancelTimeout()
        CommerceSessionWorker.execute(context, sessionId)
    }

    private var approvedSessionId: String? = null
    internal fun approveCommerceSession(sessionId: String) {
        if (approvedSessionId == sessionId) return
        approvedSessionId = sessionId
        viewModelScope.launch {
            flow { emit(interactor.approveCommerceSession(sessionId)) }
                .catch { ex ->
                    when (ex) {
                        is ApiException -> errorHandler.setApiError(ex)
                        else -> errorHandler.setError(ex)
                    }
                }
                .retryWhen { _, attempt ->
                    delay(RETRY_DELAY)
                    attempt < RETRY_COUNT
                }
                .onStart {
                    _progress.value = true
                    _progressState.value = Flexa.context?.getString(R.string.processing) + "..."
                }
                .collect { }
        }
    }

    internal fun removeNotification(notification: Notification) {
        viewModelScope.launch {
            mutex.withLock {
                _notifications.remove(notification)
            }
            runCatching { interactor.deleteNotification(notification.id ?: "") }
        }
    }

    internal fun updateAccountData() {
        viewModelScope.launch {
            getAccount(useCached = false)
        }
    }

    internal suspend fun getAccount(useCached: Boolean = true) {
        flow {
            if (useCached) emit(interactor.getAccountCached())
            emit(interactor.getAccount().apply {
                _unitOfAccount.value = interactor.getUnitOfAccount()
            })
        }.retryWhen { _, attempt ->
            delay(FlexaConstants.RETRY_DELAY)
            attempt < FlexaConstants.RETRY_COUNT
        }.catch { Log.e(null, "getAccount: ", it) }
            .filter { account -> account != null }
            .onEach { _unitOfAccount.value = interactor.getUnitOfAccount() }
            .collect { account ->
                _account.value = account
                _account.value?.let { eventFlow.emit(Event.Account(it)) }
                mutex.withLock {
                    account?.notifications?.let { n ->
                        _notifications.clear()
                        _notifications.addAll(n)
                    }
                }
            }
    }

    private var brandProgressJob: Job? = null
    private fun startBrandProgress() {
        brandProgressJob = viewModelScope.launch {
            _progress.value = true
            _progressState.value = ""
            delay(1000)
            _progressState.value = Flexa.context?.getString(R.string.signing) + "..."
        }
    }

    private suspend fun isLegacy(
        commerceSession: CommerceSession.Data?
    ): Boolean {
        val brandSession = interactor.getBrandSession(commerceSession?.id ?: "")
        return brandSession?.legacy == true
    }

    private var listenEventsJob: Job? = null
    private fun listenEvents() {
        listenEventsJob?.cancel()
        listenEventsJob = viewModelScope.launch {
            interactor.listenEvents(interactor.getLastEventId())
                .flowOn(Dispatchers.IO)
                .retryWhen { _, attempt ->
                    delay(RETRY_DELAY)
                    isActive
                }
                .catch { Log.e(null, "listenEvents: ", it) }
                .filter { event -> // filter current session
                    when (event) {
                        is SseEvent.Session -> {
                            val cs = event.session
                            val isCurrent = _commerceSession.value?.isCurrent(cs) ?: true
                            isCurrent
                        }

                        else -> true
                    }
                }
                .onEach { event ->
                    when (event) {
                        is SseEvent.Session -> {
                            if (event.session.isClosed()) {
                                getAccount(useCached = false)
                            }
                            if (event.session.isCompleted()) {
                                interactor.saveLastEventId(null)
                            }
                        }

                        else -> event.eventId?.let { interactor.saveLastEventId(it) }
                    }
                }
                .collect { event ->
                    when (event) {
                        is SseEvent.Session -> {
                            when {
                                event.session.isValid() ->
                                    event.session.data?.let { emitCommerceSession(it) }

                                event.session.isClosed() &&
                                        event.session.isCurrent(commerceSession.value) -> {
                                    stopProgress()
                                    cancelTimeout()
                                    deleteCommerceSessionData()
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun listenCommerceSession() {
        viewModelScope.launch {
            _commerceSession
                .filter { it?.isValid() == true }
                .map { it!! }
                .onEach { cs -> eventFlow.emit(Event.CommerceSessionUpdate(cs.data)) }
                .onEach { cs -> cs.data?.id?.let { saveLastSessionId(it) } }
                .onEach { cs -> if (cs.isCompleted()) stopProgress() }
                .onEach { cs ->
                    val coveredByFlexaAccount = cs.data?.coveredByFlexaAccount() == true
                    val selectedAssetId = selectedAsset.value?.asset?.assetId ?: ""
                    val hasAnotherAsset = cs.data?.hasAnotherAsset(selectedAssetId) == true
                    val completed = cs.isCompleted()
                    if (!coveredByFlexaAccount && !completed && hasAnotherAsset) {
                        val sessionId = cs.data?.id ?: ""
                        patchCommerceSession(sessionId, selectedAssetId)
                    }
                }
                .retryWhen { _, attempt ->
                    delay(RETRY_DELAY)
                    attempt < RETRY_COUNT
                }
                .catch { }
                .collect { cs ->
                    val legacy = cs.data?.isLegacy ?: false
                    val containsAuthorization = cs.containsAuthorization()

                    val inputAmountClosed = selectedBrand.value == null
                    val showBrandPaymentCard =
                        (legacy && !containsAuthorization && inputAmountClosed)
                                || (legacy && containsAuthorization)

                    val sendTransactionRequest = brandSessionId.value == cs.data?.id
                    val requiresApproval = cs.requiresApproval()
                    when {
                        legacy && requiresApproval -> cs.data?.id?.let {
                            approveCommerceSession(it)
                        }

                        legacy && sendTransactionRequest -> {
                            brandSessionId.value = null
                            cs.toTransaction()?.let { transaction ->
                                Log.d(null, "CS>>>: onTransactionRequest ${cs.data?.id}")
                                Spend.onTransactionRequest?.invoke(Result.success(transaction))
                            }
                        }
                    }

                    if (containsAuthorization) {
                        stopProgress()
                        cancelTimeout()
                    }
                    if (showBrandPaymentCard) {
                        openLegacyCard.value = true
                    }
                }
        }
    }

    private suspend fun emitCommerceSession(session: CommerceSession.Data) {
        val updatedSessionEvent = CommerceSession(
            data = session.copy(isLegacy = isLegacy(session))
        )
        _commerceSession.emit(updatedSessionEvent)
    }

    private var listenConnectionJob: Job? = null
    private fun listenConnection() {
        if (listenConnectionJob?.isActive == true) {
            listenConnectionJob?.cancel()
        }
        listenConnectionJob = viewModelScope.launch {
            interactor.getConnectionListener()
                ?.distinctUntilChanged()
                ?.filter { it is ConnectionState.Available }
                ?.collect {
                    getAccount()
                    checkLastSession()
                    listenEvents()
                }
        }
    }

    private fun listenFlexaAppAccounts() {
        viewModelScope.launch {
            eventFlow.emit(Event.AppAccountsUpdate(interactor.getLocalAppAccounts(), true))

            Flexa.appAccounts.collect { appAccounts ->
                runCatching {
                    val putAccount = interactor.putAccounts(appAccounts)
                    putAccount.accounts
                }.onSuccess { res ->
                    eventFlow.emit(Event.AppAccountsUpdate(res))
                }
            }
        }
    }

    private suspend fun patchCommerceSession(
        sessionId: String,
        assetId: String
    ) {
        interactor.patchCommerceSession(
            commerceSessionId = sessionId,
            paymentAssetId = assetId
        )
    }

    private fun listenTransactionSent() {
        if (Flexa.context != null) {
            Spend.transactionSent = { id, txSignature ->
                viewModelScope.launch {
                    interactor.getBrandSession(id)?.let { transaction ->
                        flow {
                            emit(
                                interactor.confirmTransaction(
                                    transaction.transactionId,
                                    txSignature
                                )
                            )
                        }
                            .retryWhen { _, attempt ->
                                delay(RETRY_DELAY)
                                attempt < RETRY_COUNT
                            }
                            .onStart {
                                brandProgressJob?.cancel()
                                _progress.value = true
                                _progressState.value =
                                    Flexa.context?.getString(R.string.sending) + "..."
                            }
                            .catch { Log.e(null, "listenTransactionSent: ", it) }
                            .collect { }
                    }
                }
            }
        }
    }

    private fun listenTransactionFailed() {
        if (Flexa.context != null) {
            Spend.transactionFailed = { id ->
                viewModelScope.launch {
                    if (id == commerceSession.value?.data?.id) {
                        deleteCommerceSessionData()
                        stopProgress()
                        cancelTimeout()
                    }
                }
            }
        }
    }

    private suspend fun saveLastSessionId(sessionId: String?) {
        interactor.saveLastSessionId(sessionId)
    }

    private fun deleteLastSessionId() {
        viewModelScope.launch { interactor.saveLastSessionId(null) }
    }

    private suspend fun checkLastSession() {
        flow {
            val id = interactor.getLastSessionId()
            emit(id?.let { interactor.getCommerceSession(it) })
        }
            .retryWhen { _, attempt ->
                delay(RETRY_DELAY)
                attempt < RETRY_COUNT
            }
            .catch { Log.e(null, "checkLastSession: ", it) }
            .filter { it != null }
            .filter { it.isValid() }
            .collect { cs ->
                val updatedSessionEvent = CommerceSession(
                    data = cs?.copy(isLegacy = isLegacy(cs))
                )
                if (updatedSessionEvent.isClosed()) {
                    getAccount(useCached = false)
                }
                if (updatedSessionEvent.data?.isLegacy == false && updatedSessionEvent.isCompleted()) {
                    _commerceSession.emit(null)
                } else {
                    _commerceSession.emit(updatedSessionEvent)
                }
            }
    }

    private suspend fun saveBrandSession(session: CommerceSession.Data?) {
        session?.toBrandSession()?.let { interactor.saveBrandSession(it) }
    }
}

sealed class SheetScreen {
    data class Assets(val amount: String? = null) : SheetScreen()
    class AssetDetails(val asset: SelectedAsset) : SheetScreen()
    data object PlacesToPay : SheetScreen()
    data object Locations : SheetScreen()
    class PaymentDetails(val session: StateFlow<CommerceSession?>) : SheetScreen()
}

sealed class Event {
    class AppAccountsUpdate(
        val accounts: List<AppAccount>,
        val cached: Boolean = false
    ) : Event()

    class ExchangeRatesUpdate(val items: List<ExchangeRate>) : Event()
    class OneTimeKeysUpdate(val items: List<OneTimeKey>) : Event()
    class Account(val account: com.flexa.core.entity.Account) : Event()
    class CommerceSessionUpdate(val session: CommerceSession.Data?) : Event()
}
