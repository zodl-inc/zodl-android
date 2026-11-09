package com.flexa.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Dao
internal interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rate")
    fun getAll(): List<ExchangeRate>

    @Query("SELECT COUNT(asset) FROM exchange_rate WHERE asset IN (:ids)")
    fun countIds(ids: List<String>): Int

    @Query("SELECT * FROM exchange_rate WHERE asset = :id LIMIT 1")
    fun getByIdl(id: String): ExchangeRate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<ExchangeRate>)

    @Query("DELETE FROM exchange_rate")
    fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM exchange_rate WHERE expires_at < strftime('%s', 'now'))")
    fun hasOutdatedItems(): Boolean
}


@Entity(tableName = "exchange_rate")
internal class ExchangeRate(
    @PrimaryKey
    @ColumnInfo(name = "asset")
    val asset: String,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,
    @ColumnInfo(name = "label")
    val label: String? = null,
    @ColumnInfo(name = "precision")
    val precision: Int? = null,
    @ColumnInfo(name = "price")
    val price: String? = null,
    @ColumnInfo(name = "unit_of_account")
    val unitOfAccount: String? = null
)

internal fun ExchangeRate.toObject(): com.flexa.core.entity.ExchangeRate =
    com.flexa.core.entity.ExchangeRate(
        asset = asset, expiresAt = expiresAt, label = label,
        precision = precision, price = price, unitOfAccount = unitOfAccount
    )

internal fun com.flexa.core.entity.ExchangeRate.toDao(): ExchangeRate =
    ExchangeRate(
        asset = asset, expiresAt = expiresAt, label = label,
        precision = precision, price = price, unitOfAccount = unitOfAccount
    )
