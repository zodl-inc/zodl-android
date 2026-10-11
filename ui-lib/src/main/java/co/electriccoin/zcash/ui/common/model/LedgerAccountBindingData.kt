package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Zip32AccountIndex

/**
 * What the app persists next to a Ledger account so it can sign for it later: which device the
 * account belongs to, and which ZIP 32 account on that device.
 *
 * [deviceIdentityEncoding] is `LedgerDeviceIdentity.encoding`; it is privacy-sensitive and must
 * never be logged or sent anywhere.
 */
data class LedgerAccountBindingData(
    val deviceIdentityEncoding: String,
    val zip32AccountIndex: Zip32AccountIndex,
) {
    /**
     * Overridden to keep the device identity out of logs.
     */
    override fun toString() = "LedgerAccountBindingData(deviceIdentityEncoding=***, index=$zip32AccountIndex)"
}
