package com.flexa.spend.main.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.entity.CommerceSession
import com.flexa.core.entity.error.ApiException
import com.flexa.core.shared.ApiErrorHandler
import com.flexa.core.shared.FlexaConstants.Companion.RETRY_COUNT
import com.flexa.core.shared.FlexaConstants.Companion.RETRY_DELAY
import com.flexa.core.shared.SelectedAsset
import com.flexa.core.shared.Transaction
import com.flexa.spend.Spend
import com.flexa.spend.coveredByFlexaAccount
import com.flexa.spend.domain.ISpendInteractor
import com.flexa.spend.isCompleted
import com.flexa.spend.toBrandSession
import com.flexa.spend.toTransaction
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class ConfirmViewModel(
    internal val session: StateFlow<CommerceSession?>,
    private val interactor: ISpendInteractor = Spend.interactor
) : ViewModel() {

    private val _payProgress = MutableStateFlow(false)
    var payProgress = _payProgress.asStateFlow()

    private val _patchProgress = MutableStateFlow(false)
    val patchProgress: StateFlow<Boolean> = _patchProgress

    private var _completed = MutableStateFlow(false)
    var completed = _completed.asStateFlow()

    private val _transaction = MutableStateFlow<Transaction?>(null)
    val transaction = _transaction.asStateFlow()

    private val _showBalanceRestrictions = MutableStateFlow(false)
    val showBalanceRestrictions = _showBalanceRestrictions.asStateFlow()

    val errorHandler = ApiErrorHandler()

    init {
        listenCommerceSession()
        listenSelectedAsset()
    }

    override fun onCleared() {
        clear()
        super.onCleared()
    }

    fun clear() {
        _transaction.value = null
        _completed.value = false
    }

    fun showBalanceRestrictions(show: Boolean) {
        _showBalanceRestrictions.value = show
    }

    fun startProgress() {
        _payProgress.value = true
    }

    fun payNow() {
        viewModelScope.launch {
            session.value?.let { commerceSession ->
                saveSession(commerceSession.data)
                _payProgress.value = true
                val transaction = commerceSession.toTransaction()
                _transaction.value = transaction
            }
        }
    }

    private var previousSelectedAsset: SelectedAsset? = null
    fun setPreviouslySelectedAsset(selectedAsset: SelectedAsset? = null) {
        previousSelectedAsset = selectedAsset
    }

    private fun listenCommerceSession() {
        viewModelScope.launch {
            session
                .filter { it?.data != null }
                .map { it?.data!! }
                .filter { session -> !isLegacy(session) }
                .collect { session ->
                    when {
                        session.isCompleted() -> {
                            _payProgress.value = false
                            _completed.value = true
                        }

                        isInitiated(session) -> _payProgress.value = true
                        else -> _patchProgress.value = false
                    }
                }
        }
    }

    private fun listenSelectedAsset() {
        viewModelScope.launch {
            Spend.selectedAsset
                .drop(1)
                .collect {
                    it?.let { asset ->
                        session.value?.data?.id?.let { sessionId ->
                            val covered =
                                session.value?.data?.coveredByFlexaAccount() == true
                            val completed = session.value?.isCompleted() == true
                            if (!covered && !completed) {
                                patchCommerceSession(sessionId, asset)
                            }
                        }
                    }
                }
        }
    }

    private var rollBackPatch = false

    @OptIn(FlowPreview::class)
    private fun patchCommerceSession(commerceSessionId: String, selectedAsset: SelectedAsset) {
        viewModelScope.launch {
            flow {
                emit(
                    interactor.patchCommerceSession(
                        commerceSessionId, selectedAsset.asset.assetId
                    )
                )
            }.retryWhen { _, attempt ->
                delay(RETRY_DELAY)
                attempt < RETRY_COUNT
            }
                .timeout(3000.milliseconds)
                .onStart { _patchProgress.value = true }
                .catch { ex ->
                    rollBackPatch = true
                    _patchProgress.value = false
                    previousSelectedAsset?.let { sa -> Spend.selectedAsset(sa) }
                    when (ex) {
                        is ApiException -> errorHandler.setApiError(ex)
                        else -> errorHandler.setError(ex)
                    }
                }
                .collect {
                    if (rollBackPatch) _patchProgress.value = false
                    rollBackPatch = false
                }
        }
    }

    private suspend fun isLegacy(
        commerceSession: CommerceSession.Data?
    ): Boolean {
        val brandSession = interactor.getBrandSession(commerceSession?.id ?: "")
        return brandSession?.legacy == true
    }

    private suspend fun isInitiated(
        commerceSession: CommerceSession.Data?
    ): Boolean {
        val brandSession = interactor.getBrandSession(commerceSession?.id ?: "")
        return brandSession != null
    }

    private suspend fun saveSession(session: CommerceSession.Data?) {
        session?.toBrandSession(legacy = false)?.let { interactor.saveBrandSession(it) }
    }
}
