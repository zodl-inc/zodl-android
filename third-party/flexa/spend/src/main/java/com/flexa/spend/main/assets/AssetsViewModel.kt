package com.flexa.spend.main.assets

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.Flexa
import com.flexa.core.entity.Account
import com.flexa.core.entity.AppAccount
import com.flexa.core.entity.AvailableAsset
import com.flexa.core.entity.CommerceSession
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.getAssetIds
import com.flexa.core.getUnitOfAccount
import com.flexa.core.nonZeroAssets
import com.flexa.core.shared.ApiErrorHandler
import com.flexa.core.shared.Asset
import com.flexa.core.shared.SelectedAsset
import com.flexa.core.toAssetKey
import com.flexa.core.toBalanceBundle
import com.flexa.core.toCurrencySign
import com.flexa.core.toDate
import com.flexa.spend.MockFactory
import com.flexa.spend.R
import com.flexa.spend.Spend
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.domain.ISpendInteractor
import com.flexa.spend.getExpireTimeMills
import com.flexa.spend.main.main_screen.Event
import com.flexa.spend.main.main_screen.SpendViewModel.Companion.eventFlow
import com.flexa.spend.toFeeBundle
import com.flexa.spend.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

class AssetsViewModel(
    private val interactor: ISpendInteractor = Spend.interactor,
    private val selectedAsset: StateFlow<SelectedAsset?> = MutableStateFlow(null),
) : ViewModel() {

    private val _appAccounts = if (interactor is FakeInteractor)
        mutableStateListOf(MockFactory.getMockConfig().first())
    else
        mutableStateListOf()
    val appAccounts: List<AppAccount> = _appAccounts

    private val _assets = if (interactor is FakeInteractor)
        mutableStateListOf(MockFactory.getMockSelectedAsset())
    else
        mutableStateListOf()
    val assets: List<SelectedAsset> = _assets

    private val _selectedAssetBundle = MutableStateFlow(selectedAsset.value)
    val selectedAssetBundle = _selectedAssetBundle.asStateFlow()

    private val _commerceSession = MutableStateFlow<CommerceSession.Data?>(null)
    val commerceSession = _commerceSession.asStateFlow()
    private val _sessionFee = MutableStateFlow<SessionFee?>(null)
    val sessionFee = _sessionFee.asStateFlow()

    private val _account = MutableStateFlow<Account?>(null)
    val account = _account.asStateFlow()
    private val _assetsScreen = MutableStateFlow<AssetsScreen>(AssetsScreen.Assets)
    val assetsScreen = _assetsScreen.asStateFlow()
    var filtered = MutableStateFlow(false)
    val assetsState = MutableStateFlow<AssetsState>(AssetsState.Retrieving)
    var filterValue = 0.0
    private val errorHandler = ApiErrorHandler()
    val duration = MutableStateFlow<Duration>(Duration.ZERO)
    private val mutex = Mutex()

    init {
        subscribeEventsFlow()
        subscribeSelectedAsset()
    }

    internal fun setScreen(screen: AssetsScreen) {
        _assetsScreen.value = screen
    }

    internal fun setSelectedAsset(accountId: String, asset: AvailableAsset) {
        if (accountId != selectedAsset.value?.accountId || asset != selectedAsset.value?.asset) {
            Spend.selectedAsset(SelectedAsset(accountId, asset))
        }
    }

    internal fun poll() {
        pollExchangeRates()
        pollOneTimeKeys()
    }

    private fun subscribeSelectedAsset() {
        viewModelScope.launch {
            selectedAsset.collect { sa ->
                _selectedAssetBundle.value =
                    _assets.firstOrNull {
                        it.accountId == sa?.accountId && it.asset.assetId == sa.asset.assetId
                    }
            }
        }
    }

    private fun subscribeEventsFlow() {
        viewModelScope.launch {
            eventFlow.collect { event ->
                when (event) {
                    is Event.AppAccountsUpdate -> {
                        compileAccountsAssets(event.accounts)
                        if (!event.cached) unsubscribePoll(0L) { poll() }
                    }

                    is Event.ExchangeRatesUpdate -> {
                        compileAccountsAssets(interactor.getLocalAppAccounts())
                        updateFeeBundle()
                    }

                    is Event.OneTimeKeysUpdate -> {
                        compileAccountsAssets(interactor.getLocalAppAccounts())
                    }

                    is Event.Account -> _account.value = event.account

                    is Event.CommerceSessionUpdate -> {
                        _commerceSession.value = event.session
                        updateFeeBundle()
                    }
                }
            }
        }
    }

    private suspend fun updateFeeBundle() {
        val commerceSession = this@AssetsViewModel._commerceSession.value
        if (commerceSession == null) {
            _sessionFee.value = null
        } else {
            runCatching {
                val feeAssetId = commerceSession.transaction()?.fee?.asset ?: ""
                val asset = interactor.getDbAssetsById(feeAssetId).firstOrNull()
                val rate = interactor.getDbExchangeRate(feeAssetId)
                val feeAmount = commerceSession.transaction()
                    ?.fee?.amount?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val ratePrice = rate?.price?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val price = ratePrice?.multiply(feeAmount) ?: BigDecimal.ZERO
                val priceString = price.setScale(2, RoundingMode.DOWN)?.toPlainString()
                val amount = getFeeAmountLabel(feeAmount, rate, asset)
                val equivalentLabel = if (price < BigDecimal(0.01)) {
                    Flexa.context?.getString(
                        R.string.fee_less_than,
                        rate?.unitOfAccount?.toCurrencySign() ?: ""
                    )
                } else {
                    "${rate?.unitOfAccount?.toCurrencySign() ?: ""}${priceString ?: ""}"
                }
                _sessionFee.emit(
                    SessionFee(
                        equivalent = price,
                        equivalentLabel = equivalentLabel
                            ?: "${rate?.unitOfAccount?.toCurrencySign() ?: ""}${priceString ?: ""}",
                        amount = feeAmount,
                        amountPriceLabel = commerceSession.transaction()?.fee?.price?.label ?: "",
                        amountLabel = amount
                    )
                )
            }.onFailure { Log.e(null, "updateFeeBundle: ", it) }
        }
    }

    private fun getFeeAmountLabel(
        feeAmount: BigDecimal,
        rate: ExchangeRate?,
        asset: Asset?
    ) = "${
        feeAmount.setScale(rate?.precision ?: 0, RoundingMode.DOWN)
    } ${asset?.symbol ?: ""} "

    private var updateAccountJob: Job? = null
    private fun compileAccountsAssets(accounts: List<AppAccount>) {
        if (updateAccountJob?.isActive == true) {
            updateAccountJob?.cancel()
        }
        updateAccountJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbAssets = interactor.getDbAssets()
                val assets = dbAssets.ifEmpty { interactor.getAllAssets() }

                accounts.forEach { account ->
                    val localAccount = Flexa.appAccounts.value
                        .firstOrNull { it.assetAccountHash == account.accountId }
                    val accAssets = ArrayList<AvailableAsset>(account.availableAssets.size)
                    for (availableAsset in account.availableAssets) {
                        val localAsset =
                            localAccount?.availableAssets?.firstOrNull { it.assetId == availableAsset.assetId }
                        val assetData = assets.firstOrNull { it.id == availableAsset.assetId }
                        val exchangeRate = interactor.getDbExchangeRate(availableAsset.assetId)
                        val oneTimeKey = interactor.getDbOneTimeKey(
                            availableAsset.assetId,
                            availableAsset.livemode
                        )
                        val assetKey = oneTimeKey?.toAssetKey()
                        val fee = interactor.getDbFeeByTransactionAssetID(availableAsset.assetId)
                        val feeExchangeRate = interactor.getDbExchangeRate(fee?.asset ?: "")
                        val feeBundle = feeExchangeRate.toFeeBundle(transactionFee = fee)
                        accAssets.add(
                            availableAsset.copy(
                                assetData = assetData,
                                icon = localAsset?.icon,
                                key = assetKey,
                                oneTimeKey = oneTimeKey,
                                exchangeRate = exchangeRate,
                                balanceBundle = exchangeRate.toBalanceBundle(localAsset),
                                feeBundle = feeBundle
                            )
                        )
                    }
                    account.availableAssets.clear()
                    account.availableAssets.addAll(accAssets)
                }
                mutex.withLock {
                    _appAccounts.clear()
                    _appAccounts.addAll(accounts)
                }
                ensureActive()
                compileAssets(accounts)
                verifyAssetState(accounts)
                verifySelectedAsset(accounts)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorHandler.setError(e)
                }
            }
        }
    }

    private suspend fun compileAssets(appAccounts: List<AppAccount>) {
        val list = ArrayList<SelectedAsset>(appAccounts.sumOf { it.availableAssets.size })
        for (appAccount in appAccounts) {
            for (asset in appAccount.availableAssets) {
                list.add(
                    SelectedAsset(
                        accountId = appAccount.accountId,
                        asset = asset
                    )
                )
            }
        }
        mutex.withLock {
            _assets.clear()
            _assets.addAll(list)
        }
        _selectedAssetBundle.value =
            _assets.firstOrNull {
                it.accountId == selectedAsset.value?.accountId && it.asset.assetId == selectedAsset.value?.asset?.assetId
            }
    }

    private fun verifyAssetState(appAccounts: List<AppAccount>) {
        val assetsSize = appAccounts.sumOf { it.availableAssets.size }
        if (assetsSize == 0)
            assetsState.value = AssetsState.NoAssets(emptyList())
        else
            assetsState.value = AssetsState.Fine(emptyList())
    }

    private fun verifySelectedAsset(appAccounts: List<AppAccount>) {
        when {
            selectedAsset.value == null -> selectFirst(appAccounts)
            else -> checkSelectedAssetRepresented(appAccounts)
        }
    }

    private fun selectFirst(appAccounts: List<AppAccount>) {
        val account = appAccounts.firstOrNull { it.nonZeroAssets().isNotEmpty() }
        val asset = account?.nonZeroAssets()?.firstOrNull()
        asset?.let { setSelectedAsset(account.accountId, it) }
    }

    private fun checkSelectedAssetRepresented(appAccounts: List<AppAccount>) {
        selectedAsset.value?.let { selectedAsset ->
            val account =
                appAccounts.firstOrNull { it.accountId == selectedAsset.accountId }
            val asset =
                account?.nonZeroAssets()?.firstOrNull { it.assetId == selectedAsset.asset.assetId }
            if (asset == null)
                selectFirst(appAccounts)
        }
    }

    private var exchangeRatesJob: Job? = null
    private var oneTimeKeysJob: Job? = null
    private var unsubscribePollJob: Job? = null
    internal fun unsubscribePoll(
        delay: Long = 3000,
        listener: (() -> Unit)? = null
    ) {
        unsubscribePollJob = viewModelScope.launch {
            delay(delay)
            exchangeRatesJob?.cancel()
            oneTimeKeysJob?.cancel()
            listener?.invoke()
        }
    }

    private val minimumUpdateTime = 10_000L
    private fun pollExchangeRates() {
        if (exchangeRatesJob?.isActive == true) {
            unsubscribePollJob?.cancel()
            return
        }
        exchangeRatesJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val acc = interactor.getLocalAppAccounts()
                val assetIds = acc.getAssetIds()
                interactor.getExchangeRatesSmart(assetIds, acc.getUnitOfAccount() ?: "")
            }.onSuccess {
                eventFlow.emit(Event.ExchangeRatesUpdate(it.data))
            }

            while (isActive) {
                val acc = interactor.getLocalAppAccounts()
                val assetIds = acc.getAssetIds()

                val fees = runCatching { interactor.getTransactionFees(assetIds) }
                    .onSuccess { fees -> interactor.saveTransactionFees(fees) }
                    .getOrElse { emptyList() }
                val additionalRatesIDs =
                    fees.filter { it.asset != it.transactionAsset }.map { it.asset }
                val ids = assetIds.plus(additionalRatesIDs).distinct()

                runCatching {
                    interactor.getExchangeRatesSmart(ids, acc.getUnitOfAccount() ?: "")
                }.onSuccess { res ->
                    res.date?.let { date ->
                        this@AssetsViewModel.duration.value = getDuration(date)
                    }
                    eventFlow.emit(Event.ExchangeRatesUpdate(res.data))
                    val diff = res.data.map { it.expiresAt }
                        .getExpireTimeMills(Instant.now().toEpochMilli())
                        .coerceAtLeast(minimumUpdateTime)
                    delay(diff)
                }.onFailure {
                    delay(minimumUpdateTime)
                }
            }
        }
    }

    private fun pollOneTimeKeys() {
        if (oneTimeKeysJob?.isActive == true) {
            unsubscribePollJob?.cancel()
            return
        }
        oneTimeKeysJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val acc = interactor.getLocalAppAccounts()
                kotlin.runCatching {
                    val assetIds = acc.getAssetIds()
                    interactor.getOneTimeKeysSmart(assetIds)
                }.onSuccess { items ->
                    eventFlow.emit(Event.OneTimeKeysUpdate(items))
                    val diff = items.map { it.expiresAt }
                        .getExpireTimeMills(Instant.now().toEpochMilli(), 5 * 60 * 1000)
                        .coerceAtLeast(minimumUpdateTime)
                    delay(diff)
                }.onFailure {
                    delay(minimumUpdateTime)
                }
            }
        }
    }

    private fun getDuration(date: String): Duration {
        val serverTime = date.toDate().toInstant()
        val clientTime = Instant.now()
        val duration = Duration.between(serverTime, clientTime)
        return duration
    }
}

sealed class AssetsScreen {
    data object Assets : AssetsScreen()
    data class Settings(val asset: SelectedAsset? = null) : AssetsScreen()
    data class AssetDetails(val asset: SelectedAsset) : AssetsScreen()
}

data class SessionFee(
    val equivalent: BigDecimal,
    val equivalentLabel: String,
    val amount: BigDecimal,
    val amountPriceLabel: String,
    val amountLabel: String,
)
