package co.electriccoin.zcash.ui.screen.editaccount

import kotlinx.serialization.Serializable

/**
 * Edits the Zodl or view-only account [accountKeyId] names (`AccountUuid.toStorageKeyId()`). The
 * route carries only that id, never a key of the account.
 */
@Serializable
data class EditAccountArgs(
    val accountKeyId: String
)
