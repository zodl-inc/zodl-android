package com.flexa.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Dao
internal interface BrandSessionDao {
    @Query("SELECT * FROM brand_session WHERE session_id IN (:sessionId)")
    fun getBySessionId(sessionId: String): List<BrandSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: BrandSession)

    @Query("DELETE FROM brand_session")
    fun deleteAll()

    @Query("DELETE FROM brand_session WHERE session_id IN (:sessionId)")
    fun deleteById(vararg sessionId: String)

    @Query("DELETE FROM brand_session WHERE date < strftime('%s', 'now')")
    fun deleteOutdated()
}

@Entity(tableName = "brand_session")
class BrandSession(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
    @ColumnInfo(name = "date")
    val date: Long,
    @ColumnInfo(name = "legacy")
    val legacy: Boolean = true,
)
