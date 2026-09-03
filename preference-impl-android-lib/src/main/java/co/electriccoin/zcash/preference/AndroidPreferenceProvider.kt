@file:Suppress("DEPRECATION")

package co.electriccoin.zcash.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.spackle.Twig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.CharConversionException
import java.io.File
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.BadPaddingException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

    /**
     * Filenames already quarantined and recreated once in this process. Bounds recovery to one
     * recreate per process per filename: if the freshly recreated store still fails to open,
     * later callers rethrow instead of re-running the ladder and quarantining the fresh store
     * again.
     */
    private val recreatedFilenames = mutableSetOf<String>()

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
     * Opening is retried [ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS] times before any failure is
     * classified, since a transient Keystore operation error can look identical to real
     * corruption on the first attempt. Only failures that provably mean the stored data can never
     * be decrypted again then trigger [quarantineCorruptedEncryptedPreferences] and a fresh start:
     * the master key being verifiably absent while the file exists ([isEncryptedFileOrphaned]), or
     * a deterministic decryption or keyset-parse failure ([isUnrecoverableCorruption]). Every
     * other failure is logged and rethrown, because misclassifying it as corruption would
     * irreversibly destroy the stored secrets.
     */
    override suspend fun newEncrypted(context: Context, filename: String): PreferenceProvider =
        encryptedCache.getOrCreate(filename) {
            val dispatcher = Dispatchers.IO.limitedParallelism(1)

            val sharedPreferences =
                withContext(dispatcher) {
                    val isOrphaned = isEncryptedFileOrphaned(context, filename)
                    createEncryptedPreferencesWithRecovery(
                        filename = filename,
                        isOrphaned = isOrphaned,
                        isRecreateAllowed = filename !in recreatedFilenames,
                        create = { createEncryptedSharedPreferences(context, filename) },
                        quarantine = { cause ->
                            recreatedFilenames += filename
                            quarantineCorruptedEncryptedPreferences(context, filename, cause)
                        },
                    )
                }

            AndroidPreferenceProvider(sharedPreferences, dispatcher)
        }

    /**
     * The device-to-device migration signature: the encrypted preferences file exists, but a
     * working Keystore definitively reports the master key absent, so nothing can ever decrypt
     * the file. The query is retried once so that a momentary Keystore hiccup does not disguise
     * a genuinely orphaned file as healthy; a Keystore that still cannot be queried yields false —
     * an unknown Keystore state must never authorize deleting the stored secrets.
     *
     * Must be evaluated before attempting [createEncryptedSharedPreferences]: a failed attempt has
     * already recreated the master key alias via [MasterKey.Builder.build], so querying the
     * Keystore afterwards would always report the alias present and never detect the orphan.
     */
    private fun isEncryptedFileOrphaned(
        context: Context,
        filename: String
    ): Boolean {
        if (!encryptedPreferencesFile(context, filename).exists()) {
            return false
        }
        return retryOnceOrDefault(false) {
            !androidKeyStore().containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
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

    /**
     * Sets the corrupted file aside instead of deleting it, in this order:
     * 1. Move `<filename>.xml` and its `.xml.bak` sibling into the quarantine directory. The
     *    `.bak` file is mandatory: `SharedPreferencesImpl.loadFromDisk` restores a leftover
     *    `.bak` over a fresh file on the next process start, which would silently resurrect the
     *    corrupted data. If the move itself fails, both files are deleted instead so recovery
     *    still completes.
     * 2. Clear the in-memory `SharedPreferences` cache, so the retry gets a clean instance instead
     *    of the cached corrupted one — done after the move, otherwise it would quarantine an empty
     *    file.
     * 3. Delete the Keystore master-key alias, but only for [isMasterKeyFailure]: `MasterKey.Builder.build()`
     *    reuses an existing alias, so a data-level failure keeps the key and the quarantined file
     *    stays decryptable by this device if the classification was ever wrong. Orphaned files
     *    need no deletion — the alias was already recreated by the first attempt.
     */
    private fun quarantineCorruptedEncryptedPreferences(
        context: Context,
        filename: String,
        cause: Exception
    ) {
        runCatching {
            quarantineEncryptedPreferencesFiles(
                sharedPrefsDir = sharedPreferencesDirectory(context),
                quarantineDir = File(context.noBackupFilesDir, QUARANTINE_DIRECTORY),
                filename = filename,
            )
        }.onFailure { failure ->
            Twig.error(failure) { "Quarantining encrypted preferences $filename failed; deleting instead" }
            runCatching { encryptedPreferencesFile(context, filename).delete() }
            runCatching { File(sharedPreferencesDirectory(context), "$filename.xml.bak").delete() }
        }

        runCatching {
            context
                .getSharedPreferences(filename, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }

        if (isMasterKeyFailure(cause)) {
            runCatching { androidKeyStore().deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS) }
        }
    }

    private fun encryptedPreferencesFile(
        context: Context,
        filename: String
    ) = File(sharedPreferencesDirectory(context), "$filename.xml")

    private fun sharedPreferencesDirectory(context: Context) = File(context.filesDir.parent, "shared_prefs")
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CAUSE_CHAIN_LIMIT = 10

private fun androidKeyStore(): KeyStore =
    KeyStore
        .getInstance(ANDROID_KEYSTORE)
        .apply { load(null) }

/**
 * True only for failures that are deterministic for the stored ciphertext or this device's
 * Keystore: an AEAD/padding authentication failure, a Tink keyset that no longer decodes
 * ([CharConversionException] is Tink's malformed-hex signature, InvalidProtocolBufferException its
 * malformed-proto one), Tink's Keystore self-test failure ([KeyStoreException] — as of tink-android
 * 1.20.0, `AndroidKeystoreKmsClient` throws it from `validateAead()` when a post-key-creation AEAD
 * encrypt/decrypt round-trip of a random message doesn't match; a permanent condition on devices
 * with a buggy hardware Keystore), or [InvalidKeyException], the failure
 * [createEncryptedSharedPreferences] actually throws for a genuinely device-to-device-orphaned
 * file when [isEncryptedFileOrphaned]'s Keystore query itself failed twice (see
 * [retryOnceOrDefault]) and so could not flag the orphan first — this is the second line of
 * defense for that case. Other Keystore and general IO failures are excluded because they can be
 * transient — [createEncryptedPreferencesWithRecovery] retries before this classification runs.
 */
internal fun isUnrecoverableCorruption(exception: Exception): Boolean =
    generateSequence<Throwable>(exception) { it.cause }
        .take(CAUSE_CHAIN_LIMIT)
        .any {
            it is BadPaddingException ||
                it is CharConversionException ||
                it is KeyStoreException ||
                it is InvalidKeyException ||
                it.javaClass.simpleName == "InvalidProtocolBufferException"
        }

/**
 * True for failures that mean the Keystore key itself is unusable, as opposed to the stored
 * ciphertext being unreadable. [MasterKey.Builder.build] reuses an existing alias, so these are
 * the only failures for which [quarantineCorruptedEncryptedPreferences] must delete it and force a
 * fresh key on the next attempt.
 */
internal fun isMasterKeyFailure(exception: Exception): Boolean =
    generateSequence<Throwable>(exception) { it.cause }
        .take(CAUSE_CHAIN_LIMIT)
        .any { it is KeyStoreException || it is InvalidKeyException }

/**
 * Runs [block], retrying once if it throws; returns [default] when both attempts throw.
 */
internal fun <T> retryOnceOrDefault(
    default: T,
    block: () -> T
): T =
    runCatching(block)
        .recoverCatching { block() }
        .getOrDefault(default)

internal const val ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS = 3

internal fun encryptedPreferencesOpenBackoff(attempt: Int): Duration = 200.milliseconds * (1 shl (attempt - 1))

/**
 * Opens encrypted preferences with bounded retry before quarantine: the MOB-1452 Play trace showed
 * an `AEADBadTagException` caused by a Keystore operation error, so a single failure is never
 * enough to condemn the store. [create] is deliberately non-suspending — the only suspension point
 * is [delay], kept outside the try/catch so a coroutine cancellation always propagates instead of
 * being caught by the broad `Exception` handling here.
 *
 * After [maxAttempts] failures, [quarantine] runs only when [isRecreateAllowed] and either
 * [isOrphaned] or the last failure is [isUnrecoverableCorruption]; otherwise the last failure is
 * rethrown, keeping the stored data intact for a later retry.
 */
@Suppress("TooGenericExceptionCaught", "ReturnCount")
internal suspend fun <T> createEncryptedPreferencesWithRecovery(
    filename: String,
    isOrphaned: Boolean,
    isRecreateAllowed: Boolean = true,
    maxAttempts: Int = ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS,
    backoff: (attempt: Int) -> Duration = ::encryptedPreferencesOpenBackoff,
    create: () -> T,
    quarantine: (cause: Exception) -> Unit,
): T {
    var lastFailure: Exception? = null

    for (attempt in 1..maxAttempts) {
        val failure =
            try {
                return create()
            } catch (e: Exception) {
                e
            }

        lastFailure = failure
        Twig.warn(failure) { "Opening encrypted preferences $filename failed on attempt $attempt of $maxAttempts" }

        if (attempt < maxAttempts) {
            delay(backoff(attempt))
        }
    }

    val cause = checkNotNull(lastFailure) { "maxAttempts must be at least 1" }

    return if (isRecreateAllowed && (isOrphaned || isUnrecoverableCorruption(cause))) {
        Twig.error(cause) { "Encrypted preferences $filename can never be decrypted again; quarantining them" }
        quarantine(cause)
        create()
    } else {
        Twig.error(cause) { "Opening encrypted preferences $filename failed; keeping data intact for a retry" }
        throw cause
    }
}
