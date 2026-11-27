package com.flexa.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Database(
    entities = [
        Asset::class, Brand::class, BrandSession::class, ExchangeRate::class,
        OneTimeKey::class, TransactionFee::class
    ], version = 11,
    exportSchema = false
)
@TypeConverters(StringListConverter::class, ObjectListsConverter::class, ObjectConverters::class)
internal abstract class Database : RoomDatabase() {

    abstract fun assetsDao(): AssetDao

    abstract fun brandsDao(): BrandDao

    abstract fun brandSessionDao(): BrandSessionDao

    abstract fun exchangeRateDao(): ExchangeRateDao

    abstract fun oneTimeKeyDao(): OneTimeKeyDao

    abstract fun transactionFeeDao(): TransactionFeeDao
}
