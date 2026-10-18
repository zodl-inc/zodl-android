package com.flexa.core

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import com.flexa.core.data.db.DbRepository
import com.flexa.core.data.rest.RestRepository
import com.flexa.core.data.rest.RestRepository.Companion.json
import com.flexa.core.data.storage.SecuredPreferences
import com.flexa.core.domain.db.DbInteractor
import com.flexa.core.domain.rest.RestInteractor
import com.flexa.core.entity.error.SDKInitializationError
import com.flexa.core.shared.AssetAccount
import com.flexa.core.shared.FlexaClientConfiguration
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.shared.SelectedAsset
import com.flexa.core.shared.SerializerProvider
import com.flexa.core.theme.FlexaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import java.util.UUID


/**
 * The main Flexa access object
 */
@SuppressLint("StaticFieldLeak")
object Flexa {

    /**
     * Application context
     */
    var context: Context? = null
    val scope = CoroutineScope(Job())
    val requiredContext: Context
        get() {
            if (context == null)
                throw SDKInitializationError()
            return context!!
        }
    internal val _canSpend = MutableStateFlow(true)
    val canSpend = _canSpend.asStateFlow()
    private val _selectedAsset = MutableStateFlow<SelectedAsset?>(null)
    val selectedAsset: StateFlow<SelectedAsset?> = _selectedAsset
    internal var themeConfig = FlexaTheme()
    @Volatile
    internal var okHttpClientCustomizer: ((OkHttpClient.Builder) -> Unit)? = null
    private val _appAccounts = MutableStateFlow<List<AssetAccount>>(emptyList())
    val appAccounts: StateFlow<List<AssetAccount>> = _appAccounts
    private val serializerProvider = SerializerProvider()
    private val preferences by lazy {
        SecuredPreferences(
            context as Application,
            serializerProvider, FlexaConstants.FILE
        )
    }
    private val restRepository by lazy {
        RestRepository(preferences)
    }

    private val dbRepository by lazy {
        DbRepository(requiredContext)
    }

    val restInteractor by lazy(LazyThreadSafetyMode.NONE) {
        RestInteractor(restRepository)
    }

    val dbInteractor by lazy(LazyThreadSafetyMode.NONE) {
        DbInteractor(dbRepository)
    }

    fun init(config: FlexaClientConfiguration) {
        context = config.context.applicationContext
        okHttpClientCustomizer = config.okHttpClientCustomizer
        themeConfig = config.theme
        setAssetAccounts(config.assetAccounts)
        setUniqueIdentifier()
        saveApiKeys(
            publishableKey = config.publishableKey,
            webViewThemeConfig = config.webViewThemeConfig
        )
    }

    fun updateAssetAccounts(assetAccounts: ArrayList<AssetAccount>) {
        setAssetAccounts(assetAccounts)
        storeAssetAccounts(assetAccounts)
    }

    fun selectedAsset(appAccountId: String, assetId: String) {
        scope.launch {
            val accounts = preferences.getString(FlexaConstants.APP_ACCOUNTS)
                ?.let { json.decodeFromString<List<com.flexa.core.entity.AppAccount>>(it) }
            val acc = accounts?.firstOrNull { it.accountId == appAccountId }
            val asset = acc?.availableAssets?.firstOrNull { it.assetId == assetId }

            val localAccounts = appAccounts.value.firstOrNull { acc?.accountId == it.assetAccountHash }
            val localAsset =
                localAccounts?.availableAssets?.firstOrNull { it.assetId == asset?.assetId }

            val dbAsset = dbInteractor.getAssetsById(assetId).firstOrNull()
            val exchangeRate = dbInteractor.getExchangeRateById(assetId)
            val oneTimeKey = dbInteractor.getOneTimeKeyByAssetId(assetId) ?:
                dbAsset?.livemode?.run { dbInteractor.getOneTimeKeyByLiveMode(this) }
            val assetKey = oneTimeKey?.toAssetKey()
            val assetWithData = asset?.copy(
                assetData = dbAsset,
                icon = localAsset?.icon,
                exchangeRate = exchangeRate,
                balanceBundle = exchangeRate.toBalanceBundle(localAsset),
                oneTimeKey = oneTimeKey,
                key = assetKey,
            )
            if (acc != null && assetWithData != null && !assetWithData.zeroValue()) {
                Log.d(
                    null,
                    "setSelectedAsset: Flexa >>> ${assetWithData.assetData?.displayName} $assetKey"
                )
                _selectedAsset.emit(SelectedAsset(acc.accountId, assetWithData))
            }
        }
    }

    private fun setAssetAccounts(appAccounts: List<AssetAccount>?) {
        scope.launch {
            appAccounts?.let {
                _appAccounts.emit(it)
                storeAssetAccounts(it)
            }
        }
    }

    private fun storeAssetAccounts(appAccounts: List<AssetAccount>) {
        scope.launch {
            runCatching {
                json.encodeToString<List<AssetAccount>>(appAccounts)
            }.getOrNull()?.let { accountsJson ->
                preferences.saveString(FlexaConstants.ASSET_ACCOUNTS, accountsJson)
            }
        }
    }

    private fun saveApiKeys(
        publishableKey: String,
        webViewThemeConfig: String? = null,
    ) {
        preferences.run {
            savePublishableKey(publishableKey)
            savePlacesToPayTheme(webViewThemeConfig)
        }
    }

    private fun setUniqueIdentifier() {
        preferences.run {
            val storedUniqueId = getStringSynchronously(FlexaConstants.UNIQUE_IDENTIFIER)
            if (storedUniqueId == null) {
                val uniqueIdentifier = UUID.randomUUID().toString()
                saveUniqueIdentifier(uniqueIdentifier)
            }
        }
    }
}
