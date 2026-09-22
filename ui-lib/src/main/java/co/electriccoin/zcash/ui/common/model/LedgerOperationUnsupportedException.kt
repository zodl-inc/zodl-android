package co.electriccoin.zcash.ui.common.model

/**
 * The selected account is a [LedgerAccount] and the requested operation — spending, signing,
 * shielding, voting, migrating, quoting a swap or exporting tax data — is not implemented for
 * Ledger yet (MOB-2080). Enrollment (MOB-2039) ships without those paths, so every one of them
 * fails fast with this instead of reaching signing code that cannot run.
 *
 * The error mapping renders it with its own copy; it is never shown as a stack trace.
 */
class LedgerOperationUnsupportedException : Exception("This action is not available for a Ledger wallet yet.")
