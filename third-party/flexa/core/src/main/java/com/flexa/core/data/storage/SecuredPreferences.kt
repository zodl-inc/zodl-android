package com.flexa.core.data.storage

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.flexa.core.domain.data.IDataRepository
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.shared.FlexaConstants.Companion.EMPTY
import com.flexa.core.shared.SerializerProvider
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

internal class SecuredPreferences(
    private val application: Application,
    private val serializerProvider: SerializerProvider,
    private val fileName: String,
) : IDataRepository {


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

    internal fun savePlacesToPayTheme(data: String?) {
        if (data != null) {
            preferences.edit().putString(
                FlexaConstants.PLACES_TO_PAY_THEME,
                data
            ).apply()
        } else {
            preferences.edit().remove(FlexaConstants.PLACES_TO_PAY_THEME)
                .apply()
        }
    }

    internal fun savePublishableKey(publishableKey: String) {
        preferences.edit()
            .putString(FlexaConstants.PUBLISHABLE_KEY, publishableKey)
            .commit()
    }

    internal fun getPublishableKey(): String =
        preferences.getString(FlexaConstants.PUBLISHABLE_KEY, EMPTY) ?: EMPTY

    internal fun saveUniqueIdentifier(uniqueIdentifier: String) {
        preferences.edit()
            .putString(FlexaConstants.UNIQUE_IDENTIFIER, uniqueIdentifier)
            .commit()
    }

    internal fun getUniqueIdentifier(): String? =
        preferences.getString(FlexaConstants.UNIQUE_IDENTIFIER, null)

    internal fun getStringSet(key: String): Set<String>? =
        preferences.getStringSet(key, null)

    override suspend fun <T> save(key: String, data: T, type: Type) {
        val raw = serializerProvider.json.toJson(data, type)
        raw?.let { preferences.edit().putString(key, it).commit() }
    }

    override fun saveStringSynchronous(key: String, value: String) {
        preferences.edit().putString(key, value).commit()
    }

    override fun getStringSynchronously(key: String): String? =
        preferences.getString(key, null)

    override suspend fun getString(key: String): String? =
        getStringSynchronously(key)

    override suspend fun saveString(key: String, value: String) {
        saveStringSynchronous(key, value)
    }

    override suspend fun saveLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).commit()
    }

    override fun getLongSynchronously(key: String): Long {
        return preferences.getLong(key, Long.MIN_VALUE)
    }

    override suspend fun <T> get(key: String, type: Type): T? {
        val raw = getString(key)
        return try {
            serializerProvider.json.fromJson<T>(raw, type)
        } catch (e: JsonSyntaxException) {
            Log.w(SecuredPreferences::class.java.simpleName, e.message, e)
            null
        }
    }

    override suspend fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    override suspend fun clearPreferences() {
        application.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    override suspend fun saveSet(key: String, value: Set<String>) {
        preferences.edit()
            .putStringSet(key, value)
            .commit()
    }

    override suspend fun getSet(key: String): Set<String> =
        preferences.getStringSet(key, emptySet()) ?: emptySet()

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

