package com.flexa.spend.domain

import com.flexa.core.data.db.BrandSession
import com.flexa.core.entity.Account
import com.flexa.core.entity.AppAccount
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
import kotlinx.coroutines.flow.Flow

interface ISpendInteractor {

    suspend fun getConnectionListener(): Flow<ConnectionState>?
    suspend fun getLocalAssetsAccounts(): List<AssetAccount>?
    suspend fun getLocalAppAccounts(): List<AppAccount>
    suspend fun getAllAssets(pageSize: Int = 100): List<Asset>
    suspend fun getAssets(pageSize: Int, nextPageToken: String? = null): AssetsResponse
    suspend fun getAssetById(assetId: String): Asset
    suspend fun getBrandSession(sessionId: String): BrandSession?
    suspend fun deleteBrandSession(sessionId: String)
    suspend fun deleteOutdatedSessions()
    suspend fun saveBrandSession(transactionBundle: BrandSession)
    suspend fun saveLastSessionId(eventId: String?)
    suspend fun getLastSessionId(): String?
    suspend fun getCommerceSession(sessionId: String): CommerceSession.Data
    suspend fun saveLastEventId(eventId: String?)
    suspend fun getLastEventId(): String?
    suspend fun putAccounts(accounts: List<com.flexa.core.shared.AssetAccount>): PutAppAccountsResponse
    suspend fun getAccount(): Account
    suspend fun getAccountCached(): Account?
    suspend fun getUnitOfAccount(): String
    suspend fun deleteNotification(id: String)
    suspend fun getEmail(): String?
    suspend fun getPublishableKey(): String
    suspend fun getPlacesToPayTheme(): String?
    suspend fun getDbAssets(): List<Asset>
    suspend fun getDbAssetsById(vararg ids: String): List<Asset>
    suspend fun deleteAssets()
    suspend fun saveAssets(items: List<Asset>)
    suspend fun listenEvents(lastEventId: String?): Flow<SseEvent>
    suspend fun savePinnedBrands(itemsIds: List<String>)
    suspend fun getPinnedBrands(): List<String>
    suspend fun getBrands(legacyOnly: Boolean): List<Brand>
    suspend fun getDbBrands(): List<Brand>
    suspend fun saveBrands(items: List<Brand>)
    suspend fun deleteBrands()
    suspend fun createCommerceSession(
        brandId: String, amount: String, assetId: String, paymentAssetId: String
    ): CommerceSession.Data
    suspend fun approveCommerceSession(commerceSessionId: String): Int
    suspend fun closeCommerceSession(commerceSessionId: String): CommerceSession.Data
    suspend fun confirmTransaction(
        commerceSessionId: String, txSignature: String
    ): String

    suspend fun patchCommerceSession(
        commerceSessionId: String, paymentAssetId: String
    ): String

    suspend fun deleteToken(): Int
    suspend fun deleteAccount(): Int

    suspend fun hasOutdatedExchangeRates(): Boolean
    suspend fun getDbExchangeRate(id: String): ExchangeRate?
    suspend fun getDbExchangeRates(): List<ExchangeRate>
    suspend fun getExchangeRates(assetIds: List<String>, unitOfAccount: String): ExchangeRatesResponse
    suspend fun getExchangeRatesSmart(assetIds: List<String>, unitOfAccount: String): ExchangeRatesResponse
    suspend fun saveExchangeRates(items: List<ExchangeRate>)

    suspend fun hasOutdatedOneTimeKeys(): Boolean
    suspend fun getDbOneTimeKey(assetId: String, livemode: Boolean?): OneTimeKey?
    suspend fun getDbOneTimeKeys(): List<OneTimeKey>
    suspend fun getOneTimeKeys(assetIds: List<String>): OneTimeKeyResponse
    suspend fun getOneTimeKeysSmart(assetIds: List<String>): List<OneTimeKey>
    suspend fun saveOneTimeKeys(items: List<OneTimeKey>)
    suspend fun deleteExchangeRates()

    suspend fun getTransactionFees(assetIds: List<String>): List<TransactionFee>
    suspend fun getDbTransactionFees(): List<TransactionFee>
    suspend fun saveTransactionFees(items: List<TransactionFee>)
    suspend fun getDbFeeByTransactionAssetID(assetId: String): TransactionFee?
    suspend fun getDbFeeByAssetID(assetId: String): TransactionFee?
}
