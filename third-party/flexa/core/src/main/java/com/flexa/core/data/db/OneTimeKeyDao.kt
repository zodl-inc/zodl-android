package com.flexa.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Dao
internal interface OneTimeKeyDao {
    @Query("SELECT * FROM one_time_key")
    fun getAll(): List<OneTimeKey>

    @Query("SELECT COUNT(asset) FROM one_time_key WHERE asset IN (:ids)")
    fun countIds(ids: List<String>): Int

    @Query("SELECT * FROM one_time_key WHERE asset = :id LIMIT 1")
    fun getByAssetId(id: String): OneTimeKey?

    @Query("SELECT * FROM one_time_key WHERE livemode = :livemode LIMIT 1")
    fun getByLiveMode(livemode: Boolean): OneTimeKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<OneTimeKey>)

    @Query("DELETE FROM one_time_key")
    fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM one_time_key WHERE expires_at < strftime('%s', 'now'))")
    fun hasOutdatedItems(): Boolean
}


@Entity(tableName = "one_time_key")
internal class OneTimeKey(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "asset")
    val asset: String? = null,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,
    @ColumnInfo(name = "length")
    val length: Int? = null,
    @ColumnInfo(name = "livemode")
    val livemode: Boolean? = null,
    @ColumnInfo(name = "prefix")
    val prefix: String? = null,
    @ColumnInfo(name = "secret")
    val secret: String? = null
)

internal fun OneTimeKey.toObject(): com.flexa.core.entity.OneTimeKey =
    com.flexa.core.entity.OneTimeKey(
        id = id, asset = asset, expiresAt = expiresAt, length = length,
        livemode = livemode, prefix = prefix, secret = secret
    )

internal fun com.flexa.core.entity.OneTimeKey.toDao(): OneTimeKey =
    OneTimeKey(
        id = id, asset = asset, expiresAt = expiresAt, length = length,
        livemode = livemode, prefix = prefix, secret = secret
    )
