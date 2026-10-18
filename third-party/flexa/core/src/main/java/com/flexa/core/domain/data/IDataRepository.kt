package com.flexa.core.domain.data

import android.content.SharedPreferences
import java.lang.reflect.Type

interface IDataRepository {

    suspend fun <T> save(key: String, data: T, type: Type)

    suspend fun <T> get(key: String, type: Type): T?

    suspend fun remove(key: String)

    suspend fun clearPreferences()

    fun saveStringSynchronous(key: String, value: String)

    suspend fun saveString(key: String, value: String)

    suspend fun saveSet(key: String, value: Set<String>)

    fun getStringSynchronously(key: String): String?

    suspend fun getString(key: String): String?

    suspend fun saveLong(key: String, value: Long)

    fun getLongSynchronously(key: String): Long

    suspend fun getSet(key: String): Set<String>?

    fun edit(): SharedPreferences.Editor
}
