package co.electriccoin.zcash.ui.common.model

/**
 * The selected account is a [LedgerAccount] and the requested operation — exporting tax data,
 * voting, migrating or the ephemeral gap-limit flow — is not implemented for Ledger yet. Each of
 * those paths fails fast with this instead of reaching signing code that cannot run.
 *
 * The error mapping renders it with its own copy; it is never shown as a stack trace.
 */
class LedgerOperationUnsupportedException : Exception("This action is not available for a Ledger wallet yet.")
