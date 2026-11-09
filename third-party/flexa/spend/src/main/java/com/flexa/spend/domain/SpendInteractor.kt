package com.flexa.spend.domain

import com.flexa.core.data.db.BrandSession
import com.flexa.core.domain.db.DbInteractor
import com.flexa.core.domain.rest.RestInteractor
import com.flexa.core.entity.Account
import com.flexa.core.entity.AppAccount
import com.flexa.core.entity.AvailableAsset
import com.flexa.core.entity.CommerceSession
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.ExchangeRatesResponse
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.OneTimeKeyResponse
import com.flexa.core.entity.PutAppAccountsResponse
import com.flexa.core.entity.SseEvent
import com.flexa.core.entity.TransactionFee
import com.flexa.core.shared.Asset
import com.flexa.core.shared.AssetAccount
import com.flexa.core.shared.AssetsResponse
import com.flexa.core.shared.Brand
import com.flexa.core.shared.ConnectionState
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.shared.filterAssets
import com.flexa.core.toAssetKey
import com.flexa.core.toBalanceBundle
import com.flexa.spend.Spend.json
import com.flexa.spend.SpendConstants
import com.flexa.spend.data.SecuredPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

internal class SpendInteractor(
    private val interactor: RestInteractor,
    private val dbInteractor: DbInteractor,
    private val preferences: SecuredPreferences,
    private val connectionListener: Flow<ConnectionState>? = null
) : ISpendInteractor {

    override suspend fun getConnectionListener(): Flow<ConnectionState>? = connectionListener
    override suspend fun getLocalAssetsAccounts(): List<AssetAccount>? = withContext(Dispatchers.IO) {
        val rawAccounts = preferences.getString(FlexaConstants.ASSET_ACCOUNTS)
        rawAccounts?.let { json.decodeFromString<List<AssetAccount>>(it) }
    }

    override suspend fun getLocalAppAccounts(): List<AppAccount> = withContext(Dispatchers.IO) {
        val data = preferences.getString(FlexaConstants.APP_ACCOUNTS)
        val accounts = data?.let { json.decodeFromString<List<AppAccount>>(it) }
        accounts ?: emptyList()
    }

    override suspend fun putAccounts(
        accounts: List<com.flexa.core.shared.AssetAccount>
    ): PutAppAccountsResponse = withContext(Dispatchers.IO) {
        val appAccounts = ArrayList<AppAccount>(accounts.size)

        val assets = getAllAssets()
        deleteAssets()
        saveAssets(assets)

        val unitOfAccount = getUnitOfAccount()
        val assetIds = accounts.filterAssets(assets)
            .flatMap { it.availableAssets }.map { it.assetId }.distinct()
        val exchangeRates = async {
            runCatching {
                getExchangeRatesSmart(assetIds, unitOfAccount).data
            }.getOrElse { emptyList() }
        }.await()
        val oneTimeKeys = async {
            runCatching {
                if (assetIds.isNotEmpty())
                    getOneTimeKeysSmart(assetIds).apply {
                        saveOneTimeKeys(this)
                    }
                else emptyList()
            }.getOrElse { emptyList() }
        }.await()

        ensureActive()
        for (localAccount in accounts) {
            val filteredAssets = localAccount.filterAssets(assets)
            val accountAssets = filteredAssets.map { localAsset ->
                val exchangeRate = exchangeRates.firstOrNull { it.asset == localAsset.assetId }
                val oneTimeKey = oneTimeKeys.firstOrNull { it.asset == localAsset.assetId }
                val assetKey = oneTimeKey?.toAssetKey()
                val assetData = assets.firstOrNull { it.id == localAsset.assetId }
                AvailableAsset(
                    assetId = localAsset.assetId,
                    balance = localAsset.balance.toString(),
                    balanceAvailable = localAsset.balanceAvailable,
                    icon = localAsset.icon,
                    livemode = assetData?.livemode,
                    exchangeRate = exchangeRate,
                    balanceBundle = exchangeRate.toBalanceBundle(localAsset),
                    oneTimeKey = oneTimeKey,
                    key = assetKey,
                    assetData = assetData
                )
            }

            val appAccount = AppAccount(
                accountId = localAccount.assetAccountHash,
                displayName = localAccount.displayName,
                unitOfAccount = unitOfAccount,
                icon = localAccount.icon,
                availableAssets = ArrayList(accountAssets)
            )
            appAccounts.add(appAccount)
        }

        val response = PutAppAccountsResponse(date = "", accounts = appAccounts)

        json.encodeToString(response.accounts).let { accountsString ->
            preferences.saveString(FlexaConstants.APP_ACCOUNTS, accountsString)
        }
        response
    }

    override suspend fun getAllAssets(pageSize: Int): List<Asset> =
        withContext(Dispatchers.IO) {
            var startingAfter: String? = null
            val assets = arrayListOf<Asset>()
            do {
                val response = getAssets(pageSize, startingAfter)
                startingAfter = response.startingAfter
                assets.addAll(response.data)
            } while (startingAfter != null)
            assets
        }

    override suspend fun getAssets(pageSize: Int, nextPageToken: String?): AssetsResponse =
        interactor.getAssets(pageSize, nextPageToken)

    override suspend fun getAssetById(assetId: String): Asset = interactor.getAssetById(assetId)
    override suspend fun getBrandSession(sessionId: String): BrandSession? =
        withContext(Dispatchers.IO) {
            dbInteractor.getBrandSession(sessionId)
        }

    override suspend fun deleteBrandSession(sessionId: String) = withContext(Dispatchers.IO) {
        dbInteractor.deleteBrandSession(sessionId)
    }

    override suspend fun deleteOutdatedSessions() = withContext(Dispatchers.IO) {
        dbInteractor.deleteOutdatedSessions()
    }

    override suspend fun saveBrandSession(transactionBundle: BrandSession) =
        withContext(Dispatchers.IO) {
            dbInteractor.saveTransaction(transactionBundle)
        }

    override suspend fun saveLastSessionId(eventId: String?) = withContext(Dispatchers.IO) {
        if (eventId != null) {
            preferences.saveString(SpendConstants.LAST_SESSION_ID, eventId)
        } else {
            preferences.remove(SpendConstants.LAST_SESSION_ID)
        }
    }

    override suspend fun getLastSessionId(): String? = withContext(Dispatchers.IO) {
        preferences.getString(SpendConstants.LAST_SESSION_ID)
    }

    override suspend fun getCommerceSession(sessionId: String): CommerceSession.Data =
        withContext(Dispatchers.IO) {
            interactor.getCommerceSession(sessionId)
        }

    override suspend fun saveLastEventId(eventId: String?) = withContext(Dispatchers.IO) {
        if (eventId != null)
            preferences.saveString(SpendConstants.LAST_EVENT_ID, eventId)
        else
            preferences.remove(SpendConstants.LAST_EVENT_ID)
    }

    override suspend fun getLastEventId(): String? = withContext(Dispatchers.IO) {
        preferences.getString(SpendConstants.LAST_EVENT_ID)
    }

    override suspend fun getAccount() = withContext(Dispatchers.IO) {
        interactor.getAccount().apply {
            val jsonData = json.encodeToString(this)
            preferences.saveString(SpendConstants.ACCOUNT, jsonData)
        }
    }

    override suspend fun getAccountCached(): Account? = withContext(Dispatchers.IO) {
        val accountJson = preferences.getString(SpendConstants.ACCOUNT)
        accountJson?.let { json.decodeFromString<Account>(it) }
    }

    override suspend fun getUnitOfAccount(): String = withContext(Dispatchers.IO) {
        val account = getAccountCached() ?: getAccount()
        account.limits?.firstOrNull { !it.asset.isNullOrBlank() }?.asset ?: "iso4217/USD"
    }

    override suspend fun deleteNotification(id: String) = withContext(Dispatchers.IO) {
        interactor.deleteNotification(id)
    }

    override suspend fun getEmail(): String? = withContext(Dispatchers.IO) {
        preferences.getString(FlexaConstants.EMAIL)
    }

    override suspend fun getPublishableKey(): String {
        return preferences.getString(FlexaConstants.PUBLISHABLE_KEY) ?: ""
    }

    override suspend fun getPlacesToPayTheme(): String? {
        return preferences.getString(FlexaConstants.PLACES_TO_PAY_THEME)
    }

    override suspend fun getDbAssets(): List<Asset> =
        withContext(Dispatchers.IO) { dbInteractor.getAssets() }

    override suspend fun getDbAssetsById(vararg ids: String): List<Asset> =
        withContext(Dispatchers.IO) { dbInteractor.getAssetsById(*ids) }

    override suspend fun deleteAssets() =
        withContext(Dispatchers.IO) { dbInteractor.deleteAssets() }

    override suspend fun saveAssets(items: List<Asset>) =
        withContext(Dispatchers.IO) { dbInteractor.saveAssets(items) }

    override suspend fun listenEvents(lastEventId: String?): Flow<SseEvent> =
        interactor.listenEvents(lastEventId)

    override suspend fun savePinnedBrands(itemsIds: List<String>) =
        withContext(Dispatchers.IO) {
            val items = json.encodeToString(itemsIds)
            preferences.saveString(SpendConstants.PINNED_BRANDS, items)
        }

    override suspend fun getPinnedBrands(): List<String> =
        withContext(Dispatchers.IO) {
            val string = preferences.getString(SpendConstants.PINNED_BRANDS) ?: "[]"
            json.decodeFromString<List<String>>(string)
        }

    override suspend fun getBrands(legacyOnly: Boolean): List<Brand> =
        withContext(Dispatchers.IO) {
            var startingAfter: String? = null
            val brands = ArrayList<Brand>()
            do {
                val response = interactor.getBrands(legacyOnly, startingAfter)
                startingAfter = response.startingAfter
                brands.addAll(response.data)
            } while (startingAfter != null)
            brands
        }

    override suspend fun getDbBrands(): List<Brand> =
        withContext(Dispatchers.IO) { dbInteractor.getBrands() }

    override suspend fun saveBrands(items: List<Brand>) =
        withContext(Dispatchers.IO) { dbInteractor.saveBrands(items) }

    override suspend fun deleteBrands() =
        withContext(Dispatchers.IO) { dbInteractor.deleteBrands() }

    override suspend fun createCommerceSession(
        brandId: String,
        amount: String,
        assetId: String,
        paymentAssetId: String
    ): CommerceSession.Data =
        withContext(Dispatchers.IO) {
            interactor.createCommerceSession(
                brandId,
                amount,
                assetId,
                paymentAssetId
            )
        }

    override suspend fun approveCommerceSession(commerceSessionId: String): Int =
        withContext(Dispatchers.IO) {
            interactor.approveCommerceSession(commerceSessionId)
        }

    override suspend fun closeCommerceSession(commerceSessionId: String): CommerceSession.Data =
        withContext(Dispatchers.IO) {
            interactor.closeCommerceSession(commerceSessionId)
        }

    override suspend fun confirmTransaction(
        commerceSessionId: String,
        txSignature: String
    ): String = withContext(Dispatchers.IO) {
        interactor.confirmTransaction(commerceSessionId, txSignature)
    }

    override suspend fun patchCommerceSession(
        commerceSessionId: String,
        paymentAssetId: String
    ): String = withContext(Dispatchers.IO) {
        interactor.patchCommerceSession(commerceSessionId, paymentAssetId)
    }

    override suspend fun deleteToken(): Int = withContext(Dispatchers.IO) {
        val tokenId = preferences.getString(FlexaConstants.TOKEN_ID) ?: ""
        interactor.deleteToken(tokenId)
    }

    override suspend fun deleteAccount(): Int = withContext(Dispatchers.IO) {
        interactor.deleteAccount()
    }

    override suspend fun hasOutdatedExchangeRates(): Boolean = withContext(Dispatchers.IO) {
        dbInteractor.hasOutdatedExchangeRates()
    }

    override suspend fun getDbExchangeRates(): List<ExchangeRate> = withContext(Dispatchers.IO) {
        dbInteractor.getExchangeRates()
    }

    override suspend fun getDbExchangeRate(id: String): ExchangeRate? =
        withContext(Dispatchers.IO) {
            dbInteractor.getExchangeRateById(id)
        }

    override suspend fun getExchangeRates(
        assetIds: List<String>,
        unitOfAccount: String
    ): ExchangeRatesResponse = withContext(Dispatchers.IO) {
        interactor.getExchangeRates(assetIds, unitOfAccount).apply {
            dbInteractor.deleteExchangeRates()
            dbInteractor.saveExchangeRates(this.data)
        }
    }

    override suspend fun getExchangeRatesSmart(
        assetIds: List<String>,
        unitOfAccount: String
    ): ExchangeRatesResponse = withContext(Dispatchers.IO) {
        when {
            dbInteractor.hasOutdatedExchangeRates() ||
                    !dbInteractor.containsAllExchangeRates(assetIds) ->
                getExchangeRates(assetIds, unitOfAccount).apply {
                    saveExchangeRates(data)
                }

            else -> {
                ExchangeRatesResponse(data = dbInteractor.getExchangeRates())
            }
        }
    }

    override suspend fun getDbOneTimeKey(assetId: String, livemode: Boolean?): OneTimeKey? =
        withContext(Dispatchers.IO) {
            var key = dbInteractor.getOneTimeKeyByAssetId(assetId)
            if (key == null)
                key = livemode?.run { dbInteractor.getOneTimeKeyByLiveMode(this) }
            key
        }

    override suspend fun getDbOneTimeKeys(): List<OneTimeKey> = withContext(Dispatchers.IO) {
        dbInteractor.getOneTimeKeys()
    }

    override suspend fun hasOutdatedOneTimeKeys(): Boolean = withContext(Dispatchers.IO) {
        dbInteractor.hasOutdatedOneTimeKeys()
    }

    override suspend fun getOneTimeKeys(assetIds: List<String>): OneTimeKeyResponse =
        withContext(Dispatchers.IO) {
            interactor.getOneTimeKeys(assetIds)
        }

    override suspend fun getOneTimeKeysSmart(assetIds: List<String>): List<OneTimeKey> =
        withContext(Dispatchers.IO) {
            when {
                !assetsAreEquals(assetIds) -> {
                    getOneTimeKeys(assetIds).data.apply {
                        preferences.saveSet(SpendConstants.ASSET_IDS, assetIds.toSet())
                        saveOneTimeKeys(this)
                    }
                }

                dbInteractor.hasOutdatedOneTimeKeys() -> {
                    getOneTimeKeys(assetIds).data.apply {
                        saveOneTimeKeys(this)
                    }
                }

                else -> {
                    dbInteractor.getOneTimeKeys().ifEmpty {
                        getOneTimeKeys(assetIds).data.apply {
                            saveOneTimeKeys(this)
                        }
                    }
                }
            }
        }

    private suspend fun assetsAreEquals(assetIds: List<String>): Boolean {
        val storedAssetIds = preferences.getSet(SpendConstants.ASSET_IDS).toList()
        val res = storedAssetIds == assetIds
        return res
    }

    override suspend fun saveOneTimeKeys(items: List<OneTimeKey>) =
        withContext(Dispatchers.IO) {
            dbInteractor.deleteOneTimeKeys()
            dbInteractor.saveOneTimeKeys(items)
        }

    override suspend fun saveExchangeRates(items: List<ExchangeRate>) =
        withContext(Dispatchers.IO) {
            dbInteractor.saveExchangeRates(items)
        }

    override suspend fun deleteExchangeRates() = withContext(Dispatchers.IO) {
        dbInteractor.deleteExchangeRates()
    }

    override suspend fun getTransactionFees(
        assetIds: List<String>,
    ): List<TransactionFee> = withContext(Dispatchers.IO) {
        interactor.getTransactionFees(assetIds)
    }

    override suspend fun getDbTransactionFees(): List<TransactionFee> =
        withContext(Dispatchers.IO) {
            dbInteractor.getTransactionFees()
        }

    override suspend fun saveTransactionFees(items: List<TransactionFee>) =
        withContext(Dispatchers.IO) {
            dbInteractor.saveTransactionFees(items)
        }

    override suspend fun getDbFeeByTransactionAssetID(assetId: String): TransactionFee? =
        withContext(Dispatchers.IO) {
            dbInteractor.getTransactionFeeByTransactionAssetId(assetId)
        }

    override suspend fun getDbFeeByAssetID(assetId: String): TransactionFee? =
        withContext(Dispatchers.IO) {
            dbInteractor.getTransactionFeeByAssetId(assetId)
        }
}
