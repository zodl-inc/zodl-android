package com.flexa.core.data.db

import android.content.Context
import androidx.room.Room
import com.flexa.BuildConfig
import com.flexa.core.domain.db.IDbRepository
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.TransactionFee
import com.flexa.core.shared.Asset
import com.flexa.core.shared.Brand

class DbRepository(
    context: Context,
) : IDbRepository {

    private val db = Room.databaseBuilder(
        context,
        Database::class.java, BuildConfig.LIBRARY_PACKAGE_NAME
    )
        .fallbackToDestructiveMigrationOnDowngrade()
        .fallbackToDestructiveMigration()
        .build()

    override suspend fun clearAllTables() = db.clearAllTables()

    override suspend fun getAssets(): List<Asset> =
        db.assetsDao().getAll().map { it.toObject() }

    override suspend fun getAssetsById(vararg ids: String): List<Asset> =
        db.assetsDao().getByIds(*ids).map { it.toObject() }

    override suspend fun saveAssets(items: List<Asset>) =
        db.assetsDao().insertAll(items.map { it.toDao() })

    override suspend fun deleteAssets() = db.assetsDao().deleteAll()

    override suspend fun getBrands(): List<Brand> =
        db.brandsDao().getAll().map { it.toObject() }

    override suspend fun saveBrands(items: List<Brand>) =
        db.brandsDao().insertAll(items.map { it.toDao() })

    override suspend fun hasOutdatedExchangeRates(): Boolean {
        val empty = db.exchangeRateDao().getAll().isEmpty()
        val outdated = db.exchangeRateDao().hasOutdatedItems()
        return empty || outdated
    }

    override suspend fun containsAllExchangeRates(ids: List<String>): Boolean {
        val count = db.exchangeRateDao().countIds(ids)
        return count == ids.size
    }

    override suspend fun getExchangeRates(): List<ExchangeRate> =
        db.exchangeRateDao().getAll().map { it.toObject() }

    override suspend fun getExchangeRateById(id: String): ExchangeRate? =
        db.exchangeRateDao().getByIdl(id)?.run { toObject() }

    override suspend fun saveExchangeRates(items: List<ExchangeRate>) =
        db.exchangeRateDao().insertAll(items.map { it.toDao() })

    override suspend fun hasOutdatedOneTimeKeys(): Boolean {
        val empty = db.oneTimeKeyDao().getAll().isEmpty()
        val outdated = db.oneTimeKeyDao().hasOutdatedItems()
        return empty || outdated
    }

    override suspend fun containsAllOneTimeKeys(ids: List<String>): Boolean {
        val count = db.oneTimeKeyDao().countIds(ids)
        return count == ids.size
    }

    override suspend fun getOneTimeKeys(): List<OneTimeKey> =
        db.oneTimeKeyDao().getAll().map { it.toObject() }

    override suspend fun getOneTimeKeyByAssetId(id: String): OneTimeKey? =
        db.oneTimeKeyDao().getByAssetId(id)?.run { toObject() }

    override suspend fun getOneTimeKeyByLiveMode(livemode: Boolean): OneTimeKey? =
        db.oneTimeKeyDao().getByLiveMode(livemode)?.run { toObject() }

    override suspend fun saveOneTimeKeys(items: List<OneTimeKey>) =
        db.oneTimeKeyDao().insertAll(items.map { it.toDao() })

    override suspend fun deleteOneTimeKeys() =
        db.oneTimeKeyDao().deleteAll()

    override suspend fun getTransactionFees(): List<TransactionFee> =
        db.transactionFeeDao().getAll().map { it.toObject() }

    override suspend fun getTransactionFeeByTransactionAssetId(id: String): TransactionFee? =
        db.transactionFeeDao().getByTransactionAssetID(id)?.run { toObject() }

    override suspend fun getTransactionFeeByAssetId(id: String): TransactionFee? =
        db.transactionFeeDao().getByAssetID(id)?.run { toObject() }

    override suspend fun saveTransactionFees(items: List<TransactionFee>) =
        db.transactionFeeDao().insertAll(items.map { it.toDao() })

    override suspend fun deleteTransactionFees() =
        db.transactionFeeDao().deleteAll()

    override suspend fun deleteExchangeRates() = db.exchangeRateDao().deleteAll()

    override suspend fun getBrandSession(sessionId: String): BrandSession? {
        return db.brandSessionDao().getBySessionId(sessionId).firstOrNull()
    }

    override suspend fun deleteBrandSession(vararg sessionIds: String) {
        db.brandSessionDao().deleteById(*sessionIds)
    }

    override suspend fun deleteOutdatedSessions() {
        db.brandSessionDao().deleteOutdated()
    }

    override suspend fun saveTransaction(brandSession: BrandSession) {
        db.brandSessionDao().insert(brandSession)
    }

    override suspend fun deleteBrands() = db.brandsDao().deleteAll()
}
