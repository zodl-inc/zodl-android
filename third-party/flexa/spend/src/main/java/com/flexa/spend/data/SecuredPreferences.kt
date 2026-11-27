package com.flexa.spend.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.flexa.core.domain.data.IDataRepository
import com.flexa.core.shared.SerializerProvider
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

internal class SecuredPreferences(
    application: Application,
    private val gsonProvider: SerializerProvider,
    fileName: String,
): IDataRepository {


    private val preferences: SharedPreferences by lazy {
        try {
            val masterKey = getMasterKey()
            getEncryptedSharedPreferences(application.applicationContext, fileName, masterKey)
        } catch (ex: Exception) {
            Log.e(null, ex.message, ex)
            runCatching {
                application.deleteSharedPreferences(fileName)
                val masterKey = getMasterKey()
                getEncryptedSharedPreferences(application.applicationContext, fileName, masterKey)
            }.getOrElse {
                Log.e(null, it.message, it)
                throw Error("EncryptedSharedPreferences error")
            }
        }
    }

    internal fun getStringSet(key: String): Set<String>? =
        preferences.getStringSet(key, emptySet())

    override suspend fun <T> save(key: String, data: T, type: Type) {
        val raw = gsonProvider.json.toJson(data, type)
        raw?.let {
            preferences.edit().putString(key, it).commit()
        }
    }

    override suspend fun saveLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).commit()
    }

    override suspend fun <T> get(key: String, type: Type): T? {
        val raw = getString(key)
        return try {
            gsonProvider.json.fromJson<T>(raw, type)
        } catch (e: JsonSyntaxException) {
            Log.w(SecuredPreferences::class.java.simpleName, e.message, e)
            null
        }
    }

    override fun getLongSynchronously(key: String): Long {
        return preferences.getLong(key, Long.MIN_VALUE)
    }

    override suspend fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    override suspend fun saveSet(key: String, value: Set<String>) {
        preferences.edit().putStringSet(key, value).commit()
    }

    override suspend fun saveString(key: String, value: String) =
        saveStringSynchronous(key, value)

    override fun saveStringSynchronous(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override suspend fun getSet(key: String): Set<String> =
        preferences.getStringSet(key, emptySet()) ?: emptySet()

    override suspend fun getString(key: String): String? =
        getStringSynchronously(key)

    override fun getStringSynchronously(key: String): String? =
        preferences.getString(key, null)

    override suspend fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    override fun edit(): SharedPreferences.Editor = preferences.edit()

    private fun getMasterKey(): String {
        return MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    }

    private fun getEncryptedSharedPreferences(
        context: Context, fileName: String, masterKeyAlias: String
    ): SharedPreferences {
        return EncryptedSharedPreferences.create(
            fileName,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}

