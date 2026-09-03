package co.electriccoin.zcash.preference.androidsecurity

/**
 * Stand-in matching Android's `android.security.KeyStoreException` by simple name (that class
 * extends `java.lang.Exception`, unlike [java.security.KeyStoreException]), used by
 * [co.electriccoin.zcash.preference.EncryptedPreferenceRecoveryTest] to simulate an AOSP-wrapped
 * Keystore-daemon failure in a JVM unit test without depending on the Android framework class.
 */
class KeyStoreException : Exception()
