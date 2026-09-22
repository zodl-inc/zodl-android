package co.electriccoin.zcash.ui.common.model

/**
 * The pending Ledger pairing was gone when the account was about to be imported — the enrollment
 * screens are held in memory only, so process death empties them. The screens guard against this
 * by returning to the wallet root; this is the backstop for any path that does not.
 */
class LedgerPairingMissingException :
    Exception(
        "No pending Ledger pairing: the device has to be paired again before its account can be imported."
    )
