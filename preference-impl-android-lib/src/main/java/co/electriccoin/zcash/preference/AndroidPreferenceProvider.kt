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
import kotlinx.coroutines.Dispatchers
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

/**
 * Provides an Android implementation of shared preferences.
 *
 * This class is thread-safe.
 *
 * For a given preference file, it is expected that only a single instance is constructed and that
 * this instance lives for the lifetime of the application. Constructing multiple instances will
 * potentially corrupt preference data and will leak resources.
 *
 * @param dispatcher a serial dispatcher (parallelism of one) owning all access to
 * [sharedPreferences]; EncryptedSharedPreferences are not thread-safe, so every operation is
 * confined to it.
 */
class AndroidPreferenceProvider(
    private val sharedPreferences: SharedPreferences,
    private val dispatcher: CoroutineDispatcher
) : PreferenceProvider {
    private val clearPipeline = MutableSharedFlow<Unit>()

    private val mutex = Mutex()

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

/**
 * Each created [AndroidPreferenceProvider] serializes its preference access on a dedicated
 * [Dispatchers.IO] parallelism-1 view, so at most one instance per filename must ever be
 * constructed (two instances would serialize independently); that invariant is what
 * [standardCache] and [encryptedCache] enforce. A view holds no thread of its own, so a failed
 * creation attempt leaks nothing.
 */
private class AndroidPreferenceFactoryImpl : AndroidPreferenceFactory {
    private val standardCache = PreferenceProviderCache()
    private val encryptedCache = PreferenceProviderCache()

    override suspend fun newStandard(context: Context, filename: String): PreferenceProvider =
        standardCache.getOrCreate(filename) {
            val dispatcher = Dispatchers.IO.limitedParallelism(1)

            val sharedPreferences =
                withContext(dispatcher) {
                    context.getSharedPreferences(filename, Context.MODE_PRIVATE)
                }

            AndroidPreferenceProvider(sharedPreferences, dispatcher)
        }

    /**
     * Android Keystore keys are hardware-bound and not transferred during device-to-device
     * migration, so the encrypted prefs file arrives on the new device but cannot be decrypted.
     * On failure, [deleteCorruptedEncryptedPreferences] wipes the orphaned file and creation is
     * retried fresh.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun newEncrypted(context: Context, filename: String): PreferenceProvider =
        encryptedCache.getOrCreate(filename) {
            val dispatcher = Dispatchers.IO.limitedParallelism(1)

            val sharedPreferences =
                withContext(dispatcher) {
                    try {
                        createEncryptedSharedPreferences(context, filename)
                    } catch (e: Exception) {
                        Log.w(TAG, "Encrypted prefs failed to open — wiping and recreating (D2D transfer recovery)", e)
                        deleteCorruptedEncryptedPreferences(context, filename)
                        createEncryptedSharedPreferences(context, filename)
                    }
                }

            AndroidPreferenceProvider(sharedPreferences, dispatcher)
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
}
