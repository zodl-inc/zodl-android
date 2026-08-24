package co.electriccoin.zcash.preference

import java.io.CharConversionException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the recovery heuristics of `AndroidPreferenceProvider.newEncrypted`: which failures are
 * allowed to trigger wipe-and-recreate of the encrypted preferences, and how the orphaned-file
 * Keystore query degrades when the Keystore itself misbehaves. Widening or narrowing either
 * behavior must be a deliberate change that shows up here.
 */
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
    fun `retry recovers from a single transient failure`() {
        var attempts = 0

        val result =
            retryOnceOrDefault(false) {
                attempts++
                if (attempts == 1) {
                    error("transient Keystore failure")
                }
                true
            }

        assertTrue(result)
        assertEquals(2, attempts)
    }

    @Test
    fun `retry yields the default when the failure persists`() {
        var attempts = 0

        val result =
            retryOnceOrDefault(false) {
                attempts++
                error("persistent Keystore failure")
            }

        assertFalse(result)
        assertEquals(2, attempts)
    }
}

/**
 * Stand-in matching Tink's shaded `InvalidProtocolBufferException` by simple name, which is how
 * production code recognizes it without depending on Tink's shaded protobuf package.
 */
private class InvalidProtocolBufferException : IOException()
