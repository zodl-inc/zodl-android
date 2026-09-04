package co.electriccoin.zcash.preference

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.CharConversionException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import co.electriccoin.zcash.preference.androidsecurity.KeyStoreException as AndroidKeyStoreException
import co.electriccoin.zcash.preference.androidsecurity.legacy.KeyStoreException as LegacyAndroidKeyStoreException

/**
 * Pins the recovery heuristics of `AndroidPreferenceProvider.newEncrypted`: which failures are
 * allowed to trigger wipe-and-recreate of the encrypted preferences, and which failures are
 * allowed to delete the shared Keystore master key. Widening or narrowing either behavior must be
 * a deliberate change that shows up here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedPreferenceRecoveryTest {
    @Test
    fun `AEAD authentication failure is unrecoverable corruption`() {
        assertTrue(isUnrecoverableCorruption(AEADBadTagException("decryption failed")))
        assertTrue(isUnrecoverableCorruption(BadPaddingException("decryption failed")))
    }

    @Test
    fun `malformed Tink keyset is unrecoverable corruption`() {
        assertTrue(isUnrecoverableCorruption(CharConversionException()))
        assertTrue(
            isUnrecoverableCorruption(GeneralSecurityException(InvalidProtocolBufferException()))
        )
    }

    @Test
    fun `Keystore self-test failure is unrecoverable corruption`() {
        assertTrue(
            isUnrecoverableCorruption(
                KeyStoreException("cannot use Android Keystore: encryption/decryption failed")
            )
        )
        assertTrue(isUnrecoverableCorruption(GeneralSecurityException(KeyStoreException())))
    }

    @Test
    fun `Keystore-orphaned file failure is unrecoverable corruption`() {
        assertTrue(
            isUnrecoverableCorruption(
                InvalidKeyException("Failed to unwrap key")
            )
        )
        assertTrue(isUnrecoverableCorruption(GeneralSecurityException(InvalidKeyException())))
    }

    /**
     * The AOSP-wrapped transient shape a wedged Keystore daemon throws. Classifying it as
     * corruption would move the seed into quarantine, where no screen can reach it, over a failure
     * that heals itself once the daemon settles; the veto must hold for both classifiers, not only
     * for [isMasterKeyFailure].
     */
    @Test
    fun `a failure wrapping an android Keystore failure is not corruption`() {
        assertFalse(
            isUnrecoverableCorruption(
                InvalidKeyException("Keystore operation failed", AndroidKeyStoreException())
            )
        )
        assertFalse(
            isUnrecoverableCorruption(
                KeyStoreException("cannot use Android Keystore", AndroidKeyStoreException())
            )
        )
        assertFalse(
            isUnrecoverableCorruption(
                GeneralSecurityException(InvalidKeyException("Keystore operation failed", AndroidKeyStoreException()))
            )
        )
    }

    /**
     * The shape a device-to-device transfer actually leaves behind, reproduced on an emulator on
     * both API 31 and API 35: `MasterKey.Builder.build()` mints a replacement key under the old
     * alias, so the stored ciphertext no longer authenticates and the Keystore says so. The marker
     * is present, but the failure will never heal — vetoing on the marker alone stranded the store
     * on every launch instead of quarantining it and returning the user to onboarding.
     */
    @Test
    fun `a Keystore failure that cannot authenticate the stored ciphertext is corruption`() {
        assertTrue(
            isUnrecoverableCorruption(
                aeadFailureCausedBy(
                    AndroidKeyStoreException(
                        "Signature/MAC verification failed (internal Keystore code: -30)",
                        numericErrorCode = AndroidKeyStoreException.ERROR_KEYMINT_FAILURE
                    )
                )
            )
        )
    }

    @Test
    fun `a Keystore failure reporting a missing key is corruption and a master key failure`() {
        val keyNotFound =
            InvalidKeyException(
                "Keystore operation failed",
                AndroidKeyStoreException(
                    "Key not found",
                    numericErrorCode = AndroidKeyStoreException.ERROR_KEY_DOES_NOT_EXIST
                )
            )

        assertTrue(isUnrecoverableCorruption(keyNotFound))
        assertTrue(isMasterKeyFailure(keyNotFound))
    }

    @Test
    fun `a Keystore failure reporting a permanently invalidated key is corruption and a master key failure`() {
        val permanentlyInvalidated =
            InvalidKeyException(
                "Keystore operation failed",
                AndroidKeyStoreException(
                    "Key permanently invalidated",
                    numericErrorCode = AndroidKeyStoreException.ERROR_KEY_DOES_NOT_EXIST
                )
            )

        assertTrue(isUnrecoverableCorruption(permanentlyInvalidated))
        assertTrue(isMasterKeyFailure(permanentlyInvalidated))
    }

    /**
     * AOSP falls back to the bare error code as the message for a condition it has no wording for,
     * so the API 33 classification has to stand on its own.
     */
    @Test
    fun `the API 33 error code identifies a lost key without a recognizable message`() {
        assertTrue(
            isUnrecoverableCorruption(
                aeadFailureCausedBy(
                    AndroidKeyStoreException(
                        "7",
                        numericErrorCode = AndroidKeyStoreException.ERROR_KEY_CORRUPTED
                    )
                )
            )
        )
    }

    /**
     * Devices below API 33 carry no classification methods at all, so the message AOSP hard-codes
     * for the condition is the only permanence signal there.
     */
    @Test
    fun `a pre API 33 Keystore failure is classified from its message alone`() {
        assertTrue(
            isUnrecoverableCorruption(
                aeadFailureCausedBy(LegacyAndroidKeyStoreException("Signature/MAC verification failed"))
            )
        )
        assertFalse(
            isUnrecoverableCorruption(
                InvalidKeyException("Keystore operation failed", LegacyAndroidKeyStoreException("System error"))
            )
        )
    }

    @Test
    fun `a Keystore failure AOSP itself calls transient is never permanent`() {
        val secureHardwareBusy =
            InvalidKeyException(
                "Keystore operation failed",
                AndroidKeyStoreException("Secure hardware busy", isTransientFailure = true)
            )

        assertFalse(isUnrecoverableCorruption(secureHardwareBusy))
        assertFalse(isMasterKeyFailure(secureHardwareBusy))
    }

    /**
     * The retry verdict AOSP gives outranks the wording, so a Keystore that is merely wedged can
     * never be read as permanent however its message happens to read.
     */
    @Test
    fun `a transient verdict outranks a permanent sounding message`() {
        assertFalse(
            isUnrecoverableCorruption(
                aeadFailureCausedBy(
                    AndroidKeyStoreException("Signature/MAC verification failed", isTransientFailure = true)
                )
            )
        )
    }

    @Test
    fun `corruption signature is found deep in the cause chain`() {
        val exception = RuntimeException(IOException(GeneralSecurityException(AEADBadTagException())))

        assertTrue(isUnrecoverableCorruption(exception))
    }

    @Test
    fun `potentially transient failures are not corruption`() {
        assertFalse(isUnrecoverableCorruption(IOException("Keystore temporarily unavailable")))
        assertFalse(isUnrecoverableCorruption(GeneralSecurityException("cannot get keystore key")))
        assertFalse(isUnrecoverableCorruption(RuntimeException(IllegalStateException())))
    }

    @Test
    fun `InvalidKeyException without an android Keystore cause is a master key failure`() {
        assertTrue(isMasterKeyFailure(InvalidKeyException("Failed to unwrap key")))
        assertTrue(isMasterKeyFailure(GeneralSecurityException(InvalidKeyException())))
    }

    @Test
    fun `InvalidKeyException wrapping an android Keystore failure is not a master key failure`() {
        assertFalse(
            isMasterKeyFailure(
                InvalidKeyException("Keystore operation failed", AndroidKeyStoreException())
            )
        )
    }

    @Test
    fun `java security KeyStoreException anywhere in the chain is a master key failure`() {
        assertTrue(isMasterKeyFailure(KeyStoreException()))
        assertTrue(isMasterKeyFailure(GeneralSecurityException(KeyStoreException())))
        assertTrue(
            isMasterKeyFailure(
                InvalidKeyException("Keystore operation failed", KeyStoreException())
            )
        )
    }

    @Test
    fun `java security KeyStoreException wrapping an android Keystore failure is not a master key failure`() {
        assertFalse(isMasterKeyFailure(KeyStoreException("cannot use Android Keystore", AndroidKeyStoreException())))
    }

    @Test
    fun `data level failures are not master key failures`() {
        assertFalse(isMasterKeyFailure(AEADBadTagException()))
        assertFalse(isMasterKeyFailure(CharConversionException()))
        assertFalse(isMasterKeyFailure(IOException()))
    }

    @Test
    fun `backoff doubles starting at 200 milliseconds`() {
        assertEquals(200.milliseconds, encryptedPreferencesOpenBackoff(1))
        assertEquals(400.milliseconds, encryptedPreferencesOpenBackoff(2))
        assertEquals(800.milliseconds, encryptedPreferencesOpenBackoff(3))
    }

    @Test
    fun `a transient failure then success requires two attempts and no quarantine`() =
        runTest {
            var attempts = 0
            var quarantined = false

            val result =
                createEncryptedPreferencesWithRecovery(
                    filename = "test",
                    create = {
                        attempts++
                        if (attempts == 1) error("transient Keystore failure")
                        "opened"
                    },
                    quarantine = { quarantined = true },
                )

            assertEquals("opened", result)
            assertEquals(2, attempts)
            assertFalse(quarantined)
            assertEquals(200, currentTime)
        }

    @Test
    fun `a persistent transient failure is rethrown after three attempts without quarantine`() =
        runTest {
            var attempts = 0
            var quarantined = false

            val exception =
                assertFailsWith<IllegalStateException> {
                    createEncryptedPreferencesWithRecovery<String>(
                        filename = "test",
                        create = {
                            attempts++
                            error("persistent Keystore failure")
                        },
                        quarantine = { quarantined = true },
                    )
                }

            assertEquals("persistent Keystore failure", exception.message)
            assertEquals(3, attempts)
            assertFalse(quarantined)
            assertEquals(600, currentTime)
        }

    @Test
    fun `AEAD failure on every attempt quarantines once then recreates`() =
        runTest {
            var attempts = 0
            var quarantineCause: Exception? = null

            val result =
                createEncryptedPreferencesWithRecovery(
                    filename = "test",
                    create = {
                        attempts++
                        if (attempts <= ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS) {
                            throw AEADBadTagException("decryption failed")
                        }
                        "fresh store"
                    },
                    quarantine = { cause -> quarantineCause = cause },
                )

            assertEquals("fresh store", result)
            assertEquals(ENCRYPTED_PREFERENCES_OPEN_ATTEMPTS + 1, attempts)
            assertTrue(quarantineCause is AEADBadTagException)
        }

    @Test
    fun `AEAD failure once then success is not quarantined`() =
        runTest {
            var attempts = 0
            var quarantined = false

            val result =
                createEncryptedPreferencesWithRecovery(
                    filename = "test",
                    create = {
                        attempts++
                        if (attempts == 1) throw AEADBadTagException("decryption failed")
                        "opened"
                    },
                    quarantine = { quarantined = true },
                )

            assertEquals("opened", result)
            assertEquals(2, attempts)
            assertFalse(quarantined)
        }

    @Test
    fun `recreate not allowed rethrows without quarantining`() =
        runTest {
            var quarantined = false

            assertFailsWith<AEADBadTagException> {
                createEncryptedPreferencesWithRecovery<String>(
                    filename = "test",
                    isRecreateAllowed = false,
                    create = { throw AEADBadTagException("decryption failed") },
                    quarantine = { quarantined = true },
                )
            }

            assertFalse(quarantined)
        }

    @Test
    fun `a recreate failure after quarantine propagates`() =
        runTest {
            var quarantined = false

            assertFailsWith<AEADBadTagException> {
                createEncryptedPreferencesWithRecovery<String>(
                    filename = "test",
                    create = { throw AEADBadTagException("decryption failed") },
                    quarantine = { quarantined = true },
                )
            }

            assertTrue(quarantined)
        }
}

/**
 * The AEAD failure a Keystore raises through `AndroidKeyStoreCipherSpiBase.engineDoFinal`, which
 * chains the Keystore exception as the cause rather than passing it to a constructor.
 */
private fun aeadFailureCausedBy(cause: Throwable): AEADBadTagException =
    AEADBadTagException("decryption failed").apply { initCause(cause) }

/**
 * Stand-in matching Tink's shaded `InvalidProtocolBufferException` by simple name, which is how
 * production code recognizes it without depending on Tink's shaded protobuf package.
 */
private class InvalidProtocolBufferException : IOException()
