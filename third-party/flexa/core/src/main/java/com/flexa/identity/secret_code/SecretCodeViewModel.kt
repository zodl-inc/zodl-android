package com.flexa.identity.secret_code

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.Flexa
import com.flexa.core.entity.AppAccount
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.getAssetIds
import com.flexa.core.shared.ApiErrorHandler
import com.flexa.core.shared.FlexaConstants.Companion.RETRY_COUNT
import com.flexa.core.shared.FlexaConstants.Companion.RETRY_DELAY
import com.flexa.identity.domain.IIdentityInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ASSETS_PAGE_SIZE = 100

internal class SecretCodeViewModel(
    private val interactor: IIdentityInteractor = com.flexa.identity.Identity.identityInteractor,
    val deepLink: String? = null
) : ViewModel() {

    var progress = MutableStateFlow(false)
    var secretCode = MutableStateFlow<String?>(null)
    val errorHandler = ApiErrorHandler()
    val result = MutableSharedFlow<List<AppAccount>>()
    private var errorMagicCode: String? = null

    init {
        deepLink?.let { loginWithDeepLink(it) }
    }

    internal fun loginWithMagicCode(magicCode: String) {
        viewModelScope.launch {
            flow {
                emit(interactor.tokenPatch(code = magicCode))
            }.map { getAppAccounts() }
                .flowOn(Dispatchers.IO)
                .retryWhen { _, attempt ->
                delay(RETRY_DELAY)
                attempt < RETRY_COUNT
            }.onStart { progress.value = true }
                .catch {
                    Log.e(null, "loginWithMagicCode: ", it)
                    errorMagicCode = magicCode
                    progress.value = false
                    errorHandler.setError(it)
                }.collect { res -> result.emit(res) }
        }
    }

    private fun loginWithDeepLink(deepLink: String) {
        viewModelScope.launch {
            flow {
                emit(interactor.tokenPatch(link = deepLink))
            }.map { getAppAccounts() }
                .flowOn(Dispatchers.IO)
                .retryWhen { _, attempt ->
                delay(RETRY_DELAY)
                attempt < RETRY_COUNT
            }.onStart { progress.value = true }
                .catch {
                    Log.e(null, "loginWithDeepLink: ", it)
                    progress.value = false
                    errorHandler.setError(it)
                }.collect { res -> result.emit(res) }
        }
    }

    private fun getAppAccounts(): List<AppAccount> = runBlocking {
        val acc = interactor.putAppAccounts(Flexa.appAccounts.value)
        interactor.saveAppAccounts(acc.accounts)

        val brands = async { runCatching { getAndSaveBrands() } }
        val exchangeRates = async {
            runCatching {
                val unitOfAccount = interactor.getAccount().limits?.firstOrNull()?.asset ?: ""
                val assetIds = acc.accounts.getAssetIds()
                getAndSaveExchangeRates(
                    assetIds = assetIds, unitOfAccount = unitOfAccount
                )
            }
        }
        brands.await()
        exchangeRates.await()
        acc.accounts
    }

    private suspend fun getAndSaveBrands() {
        val brands = interactor.getBrands(true)
        interactor.deleteBrands()
        interactor.saveBrands(brands)
    }

    private suspend fun getAndSaveExchangeRates(
        assetIds: List<String>,
        unitOfAccount: String
    ): List<ExchangeRate> {
        val items = interactor.getExchangeRates(assetIds, unitOfAccount)
        interactor.deleteExchangeRates()
        interactor.saveExchangeRates(items)
        return items
    }
}
