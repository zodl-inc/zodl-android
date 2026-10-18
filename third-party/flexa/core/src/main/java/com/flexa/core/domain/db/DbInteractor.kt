package com.flexa.core.domain.db

import com.flexa.core.data.db.BrandSession
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.TransactionFee
import com.flexa.core.shared.Asset
import com.flexa.core.shared.Brand


class DbInteractor(
    private val repository: IDbRepository
) {

    suspend fun clearAllTables() = repository.clearAllTables()
    suspend fun getAssets(): List<Asset> = repository.getAssets()
    suspend fun getAssetsById(vararg ids: String): List<Asset> =
        repository.getAssetsById(*ids)

    suspend fun deleteAssets() = repository.deleteAssets()
    suspend fun saveAssets(items: List<Asset>) = repository.saveAssets(items)
    suspend fun getBrands(): List<Brand> = repository.getBrands()
    suspend fun saveBrands(items: List<Brand>) = repository.saveBrands(items)
    suspend fun deleteBrands() = repository.deleteBrands()

    suspend fun hasOutdatedExchangeRates(): Boolean = repository.hasOutdatedExchangeRates()
    suspend fun containsAllExchangeRates(ids: List<String>): Boolean =
        repository.containsAllExchangeRates(ids)

    suspend fun getExchangeRates(): List<ExchangeRate> = repository.getExchangeRates()
    suspend fun getExchangeRateById(id: String): ExchangeRate? = repository.getExchangeRateById(id)
    suspend fun saveExchangeRates(items: List<ExchangeRate>) = repository.saveExchangeRates(items)
    suspend fun deleteExchangeRates() = repository.deleteExchangeRates()

    suspend fun hasOutdatedOneTimeKeys(): Boolean = repository.hasOutdatedOneTimeKeys()
    suspend fun containsAllOneTimeKeys(ids: List<String>): Boolean =
        repository.containsAllOneTimeKeys(ids)

    suspend fun getOneTimeKeys(): List<OneTimeKey> = repository.getOneTimeKeys()
    suspend fun getOneTimeKeyByAssetId(id: String): OneTimeKey? =
        repository.getOneTimeKeyByAssetId(id)
    suspend fun getOneTimeKeyByLiveMode(livemode: Boolean): OneTimeKey? =
        repository.getOneTimeKeyByLiveMode(livemode)

    suspend fun saveOneTimeKeys(items: List<OneTimeKey>) = repository.saveOneTimeKeys(items)
    suspend fun deleteOneTimeKeys() = repository.deleteOneTimeKeys()

    suspend fun getTransactionFees(): List<TransactionFee> = repository.getTransactionFees()
    suspend fun getTransactionFeeByTransactionAssetId(id: String): TransactionFee? =
        repository.getTransactionFeeByTransactionAssetId(id)

    suspend fun getTransactionFeeByAssetId(id: String): TransactionFee? =
        repository.getTransactionFeeByAssetId(id)

    suspend fun saveTransactionFees(items: List<TransactionFee>) =
        repository.saveTransactionFees(items)

    suspend fun deleteTransactionFees() = repository.deleteTransactionFees()

    suspend fun getBrandSession(sessionId: String) =
        repository.getBrandSession(sessionId)

    suspend fun deleteBrandSession(sessionId: String) = repository.deleteBrandSession(sessionId)
    suspend fun deleteOutdatedSessions() = repository.deleteOutdatedSessions()
    suspend fun saveTransaction(brandSession: BrandSession) =
        repository.saveTransaction(brandSession)
}
