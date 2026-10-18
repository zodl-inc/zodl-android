package com.flexa.core.domain.db

import com.flexa.core.data.db.BrandSession
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.TransactionFee
import com.flexa.core.shared.Asset
import com.flexa.core.shared.Brand


interface IDbRepository {

    suspend fun clearAllTables()

    suspend fun getAssets(): List<Asset>
    suspend fun getAssetsById(vararg ids: String): List<Asset>
    suspend fun deleteAssets()
    suspend fun saveAssets(items: List<Asset>)

    suspend fun getBrands(): List<Brand>
    suspend fun deleteBrands()
    suspend fun saveBrands(items: List<Brand>)

    suspend fun hasOutdatedExchangeRates(): Boolean
    suspend fun containsAllExchangeRates(ids: List<String>): Boolean
    suspend fun getExchangeRates(): List<ExchangeRate>
    suspend fun getExchangeRateById(id: String): ExchangeRate?
    suspend fun saveExchangeRates(items: List<ExchangeRate>)
    suspend fun deleteExchangeRates()

    suspend fun hasOutdatedOneTimeKeys(): Boolean
    suspend fun containsAllOneTimeKeys(ids: List<String>): Boolean
    suspend fun getOneTimeKeys(): List<OneTimeKey>
    suspend fun getOneTimeKeyByAssetId(id: String): OneTimeKey?
    suspend fun getOneTimeKeyByLiveMode(livemode: Boolean): OneTimeKey?
    suspend fun saveOneTimeKeys(items: List<OneTimeKey>)
    suspend fun deleteOneTimeKeys()

    suspend fun getTransactionFees(): List<TransactionFee>
    suspend fun getTransactionFeeByTransactionAssetId(id: String): TransactionFee?
    suspend fun getTransactionFeeByAssetId(id: String): TransactionFee?
    suspend fun saveTransactionFees(items: List<TransactionFee>)
    suspend fun deleteTransactionFees()

    suspend fun getBrandSession(sessionId: String): BrandSession?
    suspend fun deleteBrandSession(vararg sessionIds: String)
    suspend fun deleteOutdatedSessions()
    suspend fun saveTransaction(brandSession: BrandSession)
}
