package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.preference.model.entry.TimestampPreferenceDefault
import java.time.Instant

/**
 * Wallet-global (not per-account) store for the timestamp of the most recent successful
 * network broadcast. Written by every migration transfer broadcast path; read by the two-lane
 * scheduler to detect network stalls and determine whether a lane switch is warranted.
 *
 * Key: `"last_network_activity_epoch"` — backed by regular (non-encrypted) app storage.
 */
interface LastNetworkActivityStorageProvider {
    /** Writes the current epoch as the last-seen network activity timestamp. */
    suspend fun stampNow()

    /** Returns the last-stamped instant, or `null` when never written. */
    suspend fun get(): Instant?
}

class LastNetworkActivityStorageProviderImpl(
    private val preferenceHolder: StandardPreferenceProvider,
) : LastNetworkActivityStorageProvider {
    private val default = TimestampPreferenceDefault(PreferenceKey("last_network_activity_epoch"))

    override suspend fun stampNow() {
        default.putValue(preferenceHolder(), Instant.now())
    }

    override suspend fun get(): Instant? = default.getValue(preferenceHolder())
}
