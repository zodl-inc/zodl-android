package co.electriccoin.zcash.ui.common.model

/**
 * A Ledger found while looking for the device to sign with.
 *
 * [identifier] is the device's Bluetooth address: it keys the choice and is never rendered or logged.
 */
data class LedgerSigningDevice(
    val identifier: String,
    val name: String,
) {
    /**
     * Overridden to keep the Bluetooth address out of logs.
     */
    override fun toString() = "LedgerSigningDevice(identifier=***, name=$name)"
}

/**
 * Where a Ledger signing session is, from looking for the device to the signed transaction.
 */
sealed interface LedgerSigningState {
    data object Scanning : LedgerSigningState

    /**
     * More than one Ledger is in range, or the last session met the wrong one; the session waits
     * for the user to pick one.
     */
    data class Selecting(
        val devices: List<LedgerSigningDevice>,
    ) : LedgerSigningState {
        /**
         * Overridden to keep the Bluetooth addresses out of logs.
         */
        override fun toString() = "Selecting(devices=${devices.size})"
    }

    data object Connecting : LedgerSigningState

    /**
     * The device was connected on its dashboard or in another app and is being asked to open the
     * Zcash app; it shows "Open Zcash?" until the user answers.
     */
    data object OpeningZcashApp : LedgerSigningState

    data object Preparing : LedgerSigningState

    /**
     * The transaction is being sent to the device; identifying the device reports `Streaming(0, 0)`.
     */
    data class Streaming(
        val sent: Int,
        val total: Int,
    ) : LedgerSigningState

    data object AwaitingReview : LedgerSigningState

    data object Signing : LedgerSigningState

    data object Signed : LedgerSigningState

    data class Failed(
        val issue: LedgerIssue,
    ) : LedgerSigningState
}
