package com.flexa.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import com.flexa.core.entity.TransactionFeePrice

@Dao
internal interface TransactionFeeDao {
    @Query("SELECT * FROM transaction_fee")
    fun getAll(): List<TransactionFee>

    @Query("SELECT * FROM transaction_fee WHERE transaction_asset = :id LIMIT 1")
    fun getByTransactionAssetID(id: String): TransactionFee?

    @Query("SELECT * FROM transaction_fee WHERE asset = :id LIMIT 1")
    fun getByAssetID(id: String): TransactionFee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<TransactionFee>)

    @Query("DELETE FROM transaction_fee")
    fun deleteAll()
}


@Entity(tableName = "transaction_fee")
internal class TransactionFee(
    @ColumnInfo(name = "amount")
    val amount: String? = null,
    @ColumnInfo(name = "asset")
    val asset: String,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,
    @ColumnInfo(name = "label")
    val label: String? = null,
    @ColumnInfo(name = "price")
    @TypeConverters(ObjectConverters::class)
    val price: TransactionFeePrice? = null,
    @PrimaryKey
    @ColumnInfo(name = "transaction_asset")
    val transactionAsset: String,
    @ColumnInfo(name = "zone")
    val zone: String? = null
)

internal fun TransactionFee.toObject(): com.flexa.core.entity.TransactionFee =
    com.flexa.core.entity.TransactionFee(
        amount = amount, asset = asset, expiresAt = expiresAt, label = label,
        price = price, transactionAsset = transactionAsset, zone = zone
    )

internal fun com.flexa.core.entity.TransactionFee.toDao(): TransactionFee =
    TransactionFee(
        amount = amount, asset = asset, expiresAt = expiresAt, label = label,
        price = price, transactionAsset = transactionAsset, zone = zone
    )
