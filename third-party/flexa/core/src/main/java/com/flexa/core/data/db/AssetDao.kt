package com.flexa.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Dao
internal interface AssetDao {
    @Query("SELECT * FROM asset")
    fun getAll(): List<Asset>

    @Query("SELECT * FROM asset WHERE id IN (:ids)")
    fun getByIds(vararg ids: String): List<Asset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<Asset>)

    @Query("DELETE FROM asset")
    fun deleteAll()
}

@Entity(tableName = "asset")
internal class Asset(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "icon_url")
    val iconUrl: String? = null,
    @ColumnInfo(name = "symbol")
    val symbol: String? = null,
    @ColumnInfo(name = "color")
    val color: String? = null,
    @ColumnInfo(name = "display_name")
    val displayName: String? = null,
)

internal fun Asset.toObject(): com.flexa.core.shared.Asset =
    com.flexa.core.shared.Asset(
        iconUrl = this.iconUrl, symbol = this.symbol, color = this.color,
        displayName = this.displayName, id = this.id
    )

internal fun com.flexa.core.shared.Asset.toDao(): Asset =
    Asset(
        iconUrl = this.iconUrl, symbol = this.symbol, color = this.color,
        displayName = this.displayName, id = this.id
    )
