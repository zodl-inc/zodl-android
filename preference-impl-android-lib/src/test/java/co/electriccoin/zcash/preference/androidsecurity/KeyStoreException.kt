package co.electriccoin.zcash.preference.androidsecurity

/**
 * Stand-in matching Android's `android.security.KeyStoreException` by simple name (that class
 * extends `java.lang.Exception`, unlike [java.security.KeyStoreException]), used by
 * [co.electriccoin.zcash.preference.EncryptedPreferenceRecoveryTest] to simulate an AOSP-wrapped
 * Keystore failure in a JVM unit test without depending on the Android framework class.
 *
 * [numericErrorCode] and [isTransientFailure] reproduce the API 33 classification the production
 * code reads reflectively; Kotlin names their getters `getNumericErrorCode()` and
 * `isTransientFailure()`, exactly as AOSP does. The defaults are AOSP's own fallback for an
 * unmapped KeyMint error — non-transient, [ERROR_KEYMINT_FAILURE] — which is the shape a wedged
 * Keystore reports, and which the production code must refuse to read as permanent.
 *
 * See [co.electriccoin.zcash.preference.androidsecurity.legacy.KeyStoreException] for the pre-API
 * 33 counterpart that carries no classification at all.
 */
class KeyStoreException(
    message: String? = null,
    val numericErrorCode: Int = ERROR_KEYMINT_FAILURE,
    val isTransientFailure: Boolean = false
) : Exception(message) {
    companion object {
        const val ERROR_KEY_DOES_NOT_EXIST = 6
        const val ERROR_KEY_CORRUPTED = 7
        const val ERROR_KEYMINT_FAILURE = 10
    }
}
