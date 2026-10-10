package co.electriccoin.zcash.ui.common.model

/**
 * Pairing a Ledger took longer than enrollment allows the user to approve it on the device. The
 * transports the pairing opened are closed by then; the user starts over with Try again.
 */
class LedgerPairingTimedOutException(
    cause: Throwable? = null
) : Exception("Pairing the Ledger timed out.", cause)
