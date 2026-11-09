package com.flexa.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import com.flexa.core.shared.LegacyFlexcode
import com.flexa.core.shared.Promotion

@Dao
internal interface BrandDao {
    @Query("SELECT * FROM brand")
    fun getAll(): List<Brand>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<Brand>)

    @Query("DELETE FROM brand")
    fun deleteAll()
}

@Entity(tableName = "brand")
internal class Brand(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "category_name")
    val categoryName: String? = null,
    @ColumnInfo(name = "color")
    val color: String? = null,
    @ColumnInfo(name = "legacy_flexcodes")
    @TypeConverters(ObjectListsConverter::class)
    val legacyFlexcodes: List<LegacyFlexcode>? = null,
    @TypeConverters(ObjectListsConverter::class)
    val promotions: List<Promotion>? = null,
    @ColumnInfo(name = "logo_url")
    val logoUrl: String? = null,
    @ColumnInfo(name = "name")
    val name: String? = null,
    @ColumnInfo(name = "slug")
    val slug: String? = null,
    @ColumnInfo(name = "status")
    val status: String? = null,
)

internal fun Brand.toObject(): com.flexa.core.shared.Brand =
    com.flexa.core.shared.Brand(
        id = id, categoryName = categoryName,
        color = color, legacyFlexcodes = legacyFlexcodes,
        promotions = promotions,
        logoUrl = logoUrl, name = name, slug = slug, status = status
    )

internal fun com.flexa.core.shared.Brand.toDao(): Brand =
    Brand(
        id = id, categoryName = categoryName,
        color = color, legacyFlexcodes = legacyFlexcodes,
        promotions = promotions,
        logoUrl = logoUrl, name = name, slug = slug, status = status
    )
