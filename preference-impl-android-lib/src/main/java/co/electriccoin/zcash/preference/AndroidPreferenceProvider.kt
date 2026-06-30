@file:Suppress("DEPRECATION")

package co.electriccoin.zcash.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.util.concurrent.Executors

/**
 * Provides an Android implementation of shared preferences.
 *
 * This class is thread-safe.
 *
 * For a given preference file, it is expected that only a single instance is constructed and that
 * this instance lives for the lifetime of the application. Constructing multiple instances will
 * potentially corrupt preference data and will leak resources.
 */
class AndroidPreferenceProvider(
    private val sharedPreferences: SharedPreferences,
    private val dispatcher: CoroutineDispatcher
) : PreferenceProvider {
    private val clearPipeline = MutableSharedFlow<Unit>()

    private val mutex = Mutex()
    /*
     * Implementation note: EncryptedSharedPreferences are not thread-safe, so this implementation
     * confines them to a single background thread.
     */

    override suspend fun hasKey(key: PreferenceKey) =
        withContext(dispatcher) {
            sharedPreferences.contains(key.key)
        }

    @SuppressLint("ApplySharedPref")
    override suspend fun putString(
        key: PreferenceKey,
        value: String?
    ) = withContext(dispatcher) {
        mutex.withLock {
            val editor = sharedPreferences.edit()

            editor.putString(key.key, value)

            editor.commit()

            Unit
        }
    }

    @SuppressLint("ApplySharedPref")
    override suspend fun putStringSet(
        key: PreferenceKey,
        value: Set<String>?
    ) = withContext(dispatcher) {
        mutex.withLock {
            val editor = sharedPreferences.edit()

            editor.putStringSet(key.key, value)

            editor.commit()

            Unit
        }
    }

    @SuppressLint("ApplySharedPref")
    override suspend fun putLong(
        key: PreferenceKey,
        value: Long?
    ) = withContext(dispatcher) {
        mutex.withLock {
            val editor = sharedPreferences.edit()

            if (value != null) {
                editor.putLong(key.key, value)
            } else {
                editor.remove(key.key)
            }
            editor.commit()
            Unit
        }
    }

    override suspend fun getLong(key: PreferenceKey): Long? =
        withContext(dispatcher) {
            if (sharedPreferences.contains(key.key)) {
                sharedPreferences.getLong(key.key, 0)
            } else {
                null
            }
        }

    override suspend fun getString(key: PreferenceKey) =
        withContext(dispatcher) {
            sharedPreferences.getString(key.key, null)
        }

    override suspend fun getStringSet(key: PreferenceKey): Set<String>? =
        withContext(dispatcher) {
            sharedPreferences.getStringSet(key.key, null)
        }

    @SuppressLint("ApplySharedPref")
    override suspend fun clearPreferences() =
        withContext(dispatcher) {
            val editor = sharedPreferences.edit()

            editor.clear()

            clearPipeline.emit(Unit)

            return@withContext editor.commit()
        }

    override fun observe(key: PreferenceKey): Flow<String?> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    // Callback on main thread
                    trySend(Unit)
                }
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

            this.launch {
                clearPipeline.collect {
                    send(Unit)
                }
            }

            // Kickstart the emissions
            trySend(Unit)

            awaitClose {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }.flowOn(dispatcher)
            .map { getString(key) }

    @SuppressLint("ApplySharedPref")
    override suspend fun remove(key: PreferenceKey) {
        withContext(dispatcher) {
            val editor = sharedPreferences.edit()

            editor.remove(key.key)

            editor.commit()
        }
    }

    companion object Factory : AndroidPreferenceFactory by AndroidPreferenceFactoryImpl()
}

interface AndroidPreferenceFactory {
    suspend fun newStandard(context: Context, filename: String): PreferenceProvider

    suspend fun newEncrypted(context: Context, filename: String): PreferenceProvider
}

private class AndroidPreferenceFactoryImpl : AndroidPreferenceFactory {
    private val semaphore = Mutex()
    private val standardCache = mutableMapOf<String, PreferenceProvider>()
    private val encryptedCache = mutableMapOf<String, PreferenceProvider>()

    override suspend fun newStandard(context: Context, filename: String): PreferenceProvider =
        getOrCreate(standardCache, filename) {
        /*
         * Because of this line, we don't want multiple instances of this object created
         * because we don't clean up the thread afterwards.
         */
            val singleThreadedDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

            val sharedPreferences =
                withContext(singleThreadedDispatcher) {
                    context.getSharedPreferences(filename, Context.MODE_PRIVATE)
                }

            return AndroidPreferenceProvider(sharedPreferences, singleThreadedDispatcher)
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun newEncrypted(context: Context, filename: String): PreferenceProvider =
        getOrCreate(encryptedCache, filename) {
        /*
         * Because of this line, we don't want multiple instances of this object created
         * because we don't clean up the thread afterwards.
         */
            val singleThreadedDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

            val sharedPreferences =
                withContext(singleThreadedDispatcher) {
                    try {
                        createEncryptedSharedPreferences(context, filename)
                    } catch (e: Exception) {
                        // Android Keystore keys are hardware-bound and not transferred during device-to-device
                        // migration, so the encrypted prefs file arrives on the new device but cannot be
                        // decrypted. Wipe the orphaned file and recreate fresh.
                        Log.w(TAG, "Encrypted prefs failed to open — wiping and recreating (D2D transfer recovery)", e)
                        deleteCorruptedEncryptedPreferences(context, filename)
                        createEncryptedSharedPreferences(context, filename)
                    }
                }

            return AndroidPreferenceProvider(sharedPreferences, singleThreadedDispatcher)
        }

    private fun createEncryptedSharedPreferences(
        context: Context,
        filename: String
    ): SharedPreferences {
        val mainKey =
            MasterKey
                .Builder(context)
                .apply { setKeyScheme(MasterKey.KeyScheme.AES256_GCM) }
                .build()
        return EncryptedSharedPreferences.create(
            context,
            filename,
            mainKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteCorruptedEncryptedPreferences(
        context: Context,
        filename: String
    ) {
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }.onFailure { Log.w(TAG, "Failed to delete Keystore entry during encrypted prefs cleanup", it) }
        // Clear in-memory SharedPreferences cache so the retry gets a clean instance.
        // Without this, Android returns the cached (corrupted) instance even after file deletion.
        runCatching {
            context
                .getSharedPreferences(filename, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }.onFailure { Log.w(TAG, "Failed to clear in-memory prefs cache during encrypted prefs cleanup", it) }
        runCatching {
            File(context.filesDir.parent, "shared_prefs/$filename.xml").delete()
        }.onFailure { Log.w(TAG, "Failed to delete encrypted prefs file during cleanup", it) }
    }

    companion object {
        private const val TAG = "AndroidPreferenceFactory"
    }

    private suspend inline fun getOrCreate(
        map: MutableMap<String, PreferenceProvider>,
        filename: String,
        block: () -> PreferenceProvider
    ): PreferenceProvider = semaphore.withLock { map.getOrPut(filename, block) }
}
