package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.exception.LedgerException

/**
 * [ledgerException] was raised while the phone was connecting to the Ledger, the step that bonds
 * the two, before any command reached the device. Enrollment maps it in
 * [LedgerIssueContext.ENROLLMENT_PAIRING], where a lost connection means pairing failed.
 */
class LedgerBondingFailedException(
    val ledgerException: LedgerException
) : Exception("Connecting to the Ledger failed.", ledgerException)
