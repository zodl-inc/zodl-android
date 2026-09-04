package co.electriccoin.zcash.preference.androidsecurity.legacy

/**
 * Stand-in for `android.security.KeyStoreException` as it exists below API 33: the same simple
 * name and the same messages, but none of the classification methods the production code probes
 * for reflectively. Devices on API 27 to 32 raise exactly this, so the message is the only
 * permanence signal available there.
 */
class KeyStoreException(
    message: String? = null
) : Exception(message)
