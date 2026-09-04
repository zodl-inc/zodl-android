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

    /**
     * Runs the same open-with-recovery ladder as [newEncrypted] against [filename] without
     * caching a provider for it, repairing the file in place if it is corrupted. Used by the app
     * to repair the SDK's own `cash.z.ecc.android.sdk.encrypted` store, which shares this app's
     * Keystore master key but has no corruption recovery of its own.
     *
     * Caching no provider is deliberate: the opened store is never handed out, so no second
     * [AndroidPreferenceProvider] — and therefore no second serializing dispatcher — is ever
     * created for a file the SDK opens itself. The ordering that makes that safe is the caller's
     * to keep: this must complete before anything else opens [filename]. The app's only caller,
     * the wallet-less self-heal in `WalletRepositoryImpl.init`, awaits it before
     * `Synchronizer.erase` and runs only on the path where no wallet is stored, so no synchronizer
     * exists yet to be holding the SDK store open.
     */
    suspend fun ensureEncryptedReadable(context: Context, filename: String) = Unit
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

    override suspend fun newEncrypted(context: Context, filename: String): PreferenceProvider =
        encryptedCache.getOrCreate(filename) {
            val dispatcher = Dispatchers.IO.limitedParallelism(1)

            val sharedPreferences = withContext(dispatcher) { openEncrypted(context, filename) }

            AndroidPreferenceProvider(sharedPreferences, dispatcher)
        }

    override suspend fun ensureEncryptedReadable(context: Context, filename: String) {
        withContext(Dispatchers.IO) { openEncrypted(context, filename) }
    }

    /**
     * Android Keystore keys are hardware-bound and not transferred during device-to-device
     * migration, so the encrypted prefs file arrives on the new device but cannot be decrypted.
     * Opening is retried [ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS] times before any failure is
     * classified, since a transient Keystore operation error can look identical to real
     * corruption on the first attempt. Only a deterministic decryption or keyset-parse failure
     * ([isUnrecoverableCorruption]) then triggers [quarantineCorruptedEncryptedPreferences] and a
     * fresh start, and only once per filename until that fresh store opens successfully — the
     * persisted [recreatedMarkerFile] guard is what bounds that, surviving across process
     * restarts unlike an in-memory set. Every other failure is logged and rethrown, because
     * misclassifying it as corruption would irreversibly destroy the stored secrets.
     */
    private suspend fun openEncrypted(
        context: Context,
        filename: String
    ): SharedPreferences {
        val marker = recreatedMarkerFile(quarantineDirectory(context), filename)

        return createEncryptedPreferencesWithRecovery(
            filename = filename,
            isRecreateAllowed = !marker.exists(),
            create = { createEncryptedSharedPreferences(context, filename) },
            quarantine = { cause -> quarantineCorruptedEncryptedPreferences(context, filename, cause) },
        ).also { marker.delete() }
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
     * Sets the corrupted file aside instead of deleting it. `<filename>.xml` and its `.xml.bak`
     * sibling both move into the quarantine directory; taking the `.bak` along is mandatory,
     * because `SharedPreferencesImpl.loadFromDisk` restores a leftover `.bak` over a fresh file on
     * the next process start, which would silently resurrect the corrupted data. A failing move
     * rethrows out of here and out of [createEncryptedPreferencesWithRecovery], leaving the
     * original files untouched for a later attempt — see
     * [quarantineEncryptedPreferencesFilesAndMarkRecreated] for why deleting them instead would be
     * worse than not recovering at all.
     *
     * The [recreatedMarkerFile] guard is written by that same call, and only once the move
     * succeeded: a crash before the caller's post-quarantine retry still leaves the guard in
     * place — see [openEncrypted] for when it is cleared again. Android's in-memory
     * `SharedPreferences` cache is then cleared so the retry gets a clean instance instead of the
     * cached corrupted one, which has to happen after the move or it would quarantine an empty
     * file. That clear commits an empty `<filename>.xml` back to the now-vacated original path, so
     * the file is removed again immediately: a retry that fails afterwards would otherwise leave a
     * stray empty file behind for the next launch to quarantine, instead of the marker guard
     * rethrowing.
     *
     * The Keystore master-key alias is deleted last, and only for [isMasterKeyFailure]:
     * `MasterKey.Builder.build()` reuses an existing alias, so a data-level failure keeps the key
     * and the quarantined file stays decryptable by this device if the classification was ever
     * wrong. That guarantee does not hold for an `InvalidKeyException` without the transient
     * Keystore marker: [isUnrecoverableCorruption] reads it as corruption and [isMasterKeyFailure]
     * reads it as a dead key, so the file is quarantined and the key deleted in this same call, and
     * the quarantined file becomes permanently undecryptable on this device regardless of whether
     * the classification was correct.
     */
    private fun quarantineCorruptedEncryptedPreferences(
        context: Context,
        filename: String,
        cause: Exception
    ) {
        val sharedPrefsDir = sharedPreferencesDirectory(context)
        val quarantineDir = quarantineDirectory(context)

        quarantineEncryptedPreferencesFilesAndMarkRecreated(
            sharedPrefsDir = sharedPrefsDir,
            quarantineDir = quarantineDir,
            filename = filename,
        )

        runCatching {
            context
                .getSharedPreferences(filename, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            deleteEncryptedPreferencesFiles(sharedPrefsDir, filename)
        }

        if (isMasterKeyFailure(cause)) {
            runCatching { androidKeyStore().deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS) }
        }
    }
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CAUSE_CHAIN_LIMIT = 10

private fun androidKeyStore(): KeyStore =
    KeyStore
        .getInstance(ANDROID_KEYSTORE)
        .apply { load(null) }

private fun causeChain(exception: Exception): List<Throwable> =
    generateSequence<Throwable>(exception) { it.cause }
        .take(CAUSE_CHAIN_LIMIT)
        .toList()

private const val ANDROID_KEY_STORE_EXCEPTION_SIMPLE_NAME = "KeyStoreException"

/**
 * `android.security.KeyStoreException.ERROR_KEY_DOES_NOT_EXIST`. AOSP maps both
 * `ResponseCode.KEY_NOT_FOUND` and `ResponseCode.KEY_PERMANENTLY_INVALIDATED` onto it, so it is the
 * public code for "the alias this store was encrypted under is gone for good".
 */
private const val ANDROID_KEY_STORE_ERROR_KEY_DOES_NOT_EXIST = 6

/**
 * `android.security.KeyStoreException.ERROR_KEY_CORRUPTED`, AOSP's public code for
 * `ResponseCode.VALUE_CORRUPTED`: the stored key blob no longer parses.
 */
private const val ANDROID_KEY_STORE_ERROR_KEY_CORRUPTED = 7

private val PERMANENT_ANDROID_KEY_STORE_ERROR_CODES =
    setOf(
        ANDROID_KEY_STORE_ERROR_KEY_DOES_NOT_EXIST,
        ANDROID_KEY_STORE_ERROR_KEY_CORRUPTED
    )

/**
 * AOSP's own wording for the Keystore and KeyMint conditions under which this device can never read
 * the stored ciphertext again: the first two come from `KeymasterDefs.sErrorCodeToString`
 * (`KM_ERROR_VERIFICATION_FAILED`, `KM_ERROR_INVALID_KEY_BLOB`), the rest from the
 * `ResponseCode` switch in `KeyStore2.getKeyStoreException` (`VALUE_CORRUPTED`, `KEY_NOT_FOUND`,
 * `KEY_PERMANENTLY_INVALIDATED`). They are hard-coded, never localized, and identical in the legacy
 * `KeyStore.getKeyStoreException` that devices below API 31 still use, which is what makes matching
 * on them the one permanence signal available on every supported API level — the typed
 * classification below arrived only in API 33.
 */
private val PERMANENT_ANDROID_KEY_STORE_MESSAGES =
    setOf(
        "Signature/MAC verification failed",
        "Invalid key blob",
        "Key blob corrupted",
        "Key not found",
        "Key permanently invalidated"
    )

/**
 * AOSP's own verdict from `android.security.KeyStoreException.isTransientFailure()`, which exists
 * only from API 33. Below that the method is absent and this reports false — "AOSP did not say it
 * is transient", not "AOSP said it is permanent"; [isPermanentAndroidKeyStoreFailure] still has to
 * find positive evidence of permanence before the veto is released.
 */
private fun isTransientPerAndroidKeyStore(throwable: Throwable): Boolean =
    runCatching {
        throwable.javaClass.getMethod("isTransientFailure").invoke(throwable) as Boolean
    }.getOrDefault(false)

/**
 * True when `android.security.KeyStoreException.getNumericErrorCode()` (API 33 and up) names a key
 * that is gone or unparseable. Reflective for the same reason the class itself is matched by simple
 * name, and false whenever the method is missing.
 */
private fun hasPermanentAndroidKeyStoreErrorCode(throwable: Throwable): Boolean {
    val numericErrorCode =
        runCatching {
            throwable.javaClass.getMethod("getNumericErrorCode").invoke(throwable) as Int
        }.getOrNull()

    return numericErrorCode != null && numericErrorCode in PERMANENT_ANDROID_KEY_STORE_ERROR_CODES
}

/**
 * True only when the Keystore itself reports a condition this device can never recover from: the
 * key is gone, its blob is unparseable, or the stored ciphertext does not authenticate under the
 * key that exists now — the shape a device-to-device transfer leaves behind, where
 * `MasterKey.Builder.build()` silently mints a new key under the old alias and every decrypt then
 * fails with `AEADBadTagException` caused by
 * `android.security.KeyStoreException: Signature/MAC verification failed`.
 *
 * Everything else is treated as not-permanent, including a Keystore failure this code cannot
 * classify at all: unsure means the veto holds and the store is rethrown for a later attempt,
 * never quarantined.
 */
private fun isPermanentAndroidKeyStoreFailure(throwable: Throwable): Boolean {
    if (isTransientPerAndroidKeyStore(throwable)) return false

    val message = throwable.message.orEmpty()

    return PERMANENT_ANDROID_KEY_STORE_MESSAGES.any { message.startsWith(it) } ||
        hasPermanentAndroidKeyStoreErrorCode(throwable)
}

/**
 * True when [chain] carries an `android.security.KeyStoreException` that is not positively
 * permanent. That class extends `java.lang.Exception` rather than [java.security.KeyStoreException]
 * and is matched here by simple name, because the framework class is not a JVM dependency of this
 * module. AOSP wraps a transient HAL error as `InvalidKeyException("Keystore operation failed")`
 * carrying that marker, so such a chain says the Keystore daemon was wedged, not that anything is
 * permanently broken.
 *
 * The marker alone is not enough to veto, because AOSP also raises it for failures that will never
 * heal — [isPermanentAndroidKeyStoreFailure] is what separates the two. Vetoing on its mere
 * presence stranded the device-to-device case this recovery exists for: the store could never be
 * decrypted again, yet every launch classified it as transient, exhausted the retry ladder and
 * rethrew, so onboarding was never reached.
 *
 * Both [isUnrecoverableCorruption] and [isMasterKeyFailure] veto on this, and deliberately share
 * this one implementation: a shape only one of them vetoed would still be acted upon by the other,
 * and either action — quarantining the store or deleting the shared master key — is irreversible
 * for data the Keystore was about to be able to read again.
 */
private fun hasTransientAndroidKeyStoreMarker(chain: List<Throwable>): Boolean =
    chain.any {
        it !is KeyStoreException &&
            it.javaClass.simpleName == ANDROID_KEY_STORE_EXCEPTION_SIMPLE_NAME &&
            !isPermanentAndroidKeyStoreFailure(it)
    }

/**
 * True only for failures that are deterministic for the stored ciphertext or this device's
 * Keystore: an AEAD/padding authentication failure, a Tink keyset that no longer decodes
 * ([CharConversionException] is Tink's malformed-hex signature, InvalidProtocolBufferException its
 * malformed-proto one), Tink's Keystore self-test failure ([KeyStoreException] — as of tink-android
 * 1.20.0, `AndroidKeystoreKmsClient` throws it from `validateAead()` when a post-key-creation AEAD
 * encrypt/decrypt round-trip of a random message doesn't match; a permanent condition on devices
 * with a buggy hardware Keystore), or [InvalidKeyException] — one of the failures
 * [createEncryptedSharedPreferences] throws for a device-to-device-orphaned file, whose master key
 * is verifiably gone on this device. (The other, seen on an emulator across API 31 and 35, is an
 * `AEADBadTagException` carrying the Keystore's `Signature/MAC verification failed`.) Other
 * Keystore and general IO failures are excluded because they can be transient —
 * [createEncryptedPreferencesWithRecovery] retries before this classification runs.
 *
 * A chain carrying the [hasTransientAndroidKeyStoreMarker] shape is never corruption, whatever
 * else it holds: quarantining moves the seed somewhere no screen can reach it, while rethrowing
 * leaves the store readable again as soon as the Keystore settles. That veto is exactly as wide as
 * the Keystore's own uncertainty — a Keystore failure that positively reports a gone, unparseable
 * or unauthenticating key is not the shape, and is classified on its merits below.
 */
internal fun isUnrecoverableCorruption(exception: Exception): Boolean {
    val chain = causeChain(exception)
    if (hasTransientAndroidKeyStoreMarker(chain)) return false
    return chain.any {
        it is BadPaddingException ||
            it is CharConversionException ||
            it is KeyStoreException ||
            it is InvalidKeyException ||
            it.javaClass.simpleName == "InvalidProtocolBufferException"
    }
}

/**
 * True for failures that mean the Keystore key itself is unusable, as opposed to the stored
 * ciphertext being unreadable or a transient Keystore-daemon hiccup. Only ever consulted from
 * [quarantineCorruptedEncryptedPreferences], which runs after
 * [createEncryptedPreferencesWithRecovery] has exhausted its retry ladder, so anything reaching
 * this classification already survived [ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS] attempts.
 *
 * A chain carrying the [hasTransientAndroidKeyStoreMarker] shape is never a master-key failure,
 * whatever else it holds, or [quarantineCorruptedEncryptedPreferences] would delete the shared
 * master key over a transient error and permanently orphan both the quarantined file and the SDK's
 * own encrypted store.
 *
 * The residual assumption is that a [java.security.KeyStoreException] without that marker is
 * permanent. Tink's `AndroidKeystoreKmsClient.validateAead()` folds the failure it caught into the
 * message of the [java.security.KeyStoreException] it throws rather than setting it as the cause,
 * so a transient HAL error surfacing through it is indistinguishable by type from a genuinely
 * broken Keystore; the retry ladder is the only mitigation for that case.
 */
internal fun isMasterKeyFailure(exception: Exception): Boolean {
    val chain = causeChain(exception)
    if (hasTransientAndroidKeyStoreMarker(chain)) return false
    return chain.any { it is KeyStoreException || it is InvalidKeyException }
}

internal const val ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS = 3

internal fun encryptedPreferencesOpenBackoff(attempt: Int): Duration = 200.milliseconds * (1 shl (attempt - 1))

/**
 * Opens encrypted preferences with bounded retry before quarantine: the MOB-1452 Play trace showed
 * an `AEADBadTagException` caused by a Keystore operation error, so a single failure is never
 * enough to condemn the store. [create] is deliberately non-suspending — the only suspension point
 * is [delay], kept outside the try/catch so a coroutine cancellation always propagates instead of
 * being caught by the broad `Exception` handling here.
 *
 * After [maxAttempts] failures, [quarantine] runs only when [isRecreateAllowed] and the last
 * failure is [isUnrecoverableCorruption]; otherwise the last failure is rethrown, keeping the
 * stored data intact for a later retry.
 */
@Suppress("TooGenericExceptionCaught", "ReturnCount")
internal suspend fun <T> createEncryptedPreferencesWithRecovery(
    filename: String,
    isRecreateAllowed: Boolean = true,
    maxAttempts: Int = ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS,
    backoff: (attempt: Int) -> Duration = ::encryptedPreferencesOpenBackoff,
    create: () -> T,
    quarantine: (cause: Exception) -> Unit,
): T {
    require(maxAttempts >= 1) { "maxAttempts must be at least 1" }

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

    val cause = checkNotNull(lastFailure)

    return if (isRecreateAllowed && isUnrecoverableCorruption(cause)) {
        Twig.error(cause) { "Encrypted preferences $filename can never be decrypted again; quarantining them" }
        quarantine(cause)
        create()
    } else {
        Twig.error(cause) { "Opening encrypted preferences $filename failed; keeping data intact for a retry" }
        throw cause
    }
}
