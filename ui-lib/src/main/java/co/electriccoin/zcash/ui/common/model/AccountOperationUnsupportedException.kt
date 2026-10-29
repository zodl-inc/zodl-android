package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * The selected account cannot perform the requested operation, and the path fails fast with this
 * instead of reaching signing code that cannot run for it.
 *
 * [Kind.LEDGER]: the operation (voting, migrating, the ephemeral gap-limit flow) is not implemented
 * for a [LedgerAccount] yet. [Kind.VIEW_ONLY]: the account is a [ViewOnlyAccount], which holds no
 * spend authority at all, so every spend, shield, swap, vote and migration is refused.
 *
 * The error mapping renders it with the copy of its [kind], [errorCopy]; it is never shown as a
 * stack trace.
 */
class AccountOperationUnsupportedException(
    val kind: Kind
) : Exception(
        when (kind) {
            Kind.LEDGER -> "This action is not available for a Ledger wallet yet."
            Kind.VIEW_ONLY -> "This action is not available for a view-only account."
        }
    ) {
    enum class Kind {
        LEDGER,
        VIEW_ONLY
    }

    companion object {
        fun ledger() = AccountOperationUnsupportedException(Kind.LEDGER)

        fun viewOnly() = AccountOperationUnsupportedException(Kind.VIEW_ONLY)
    }
}

/**
 * The title and message every error surface shows for an [AccountOperationUnsupportedException].
 */
internal data class UnsupportedOperationCopy(
    val title: StringResource,
    val message: StringResource,
)

/**
 * The copy for this kind: the Ledger one from ui-lib, the view-only one from the view-only feature
 * through [viewOnlyContent], so every error surface words the limitation the same way.
 */
internal fun AccountOperationUnsupportedException.Kind.errorCopy(viewOnlyContent: ViewOnlyContent) =
    when (this) {
        AccountOperationUnsupportedException.Kind.LEDGER -> {
            UnsupportedOperationCopy(
                title = stringRes(R.string.ledger_unsupported_title),
                message = stringRes(R.string.ledger_unsupported_message),
            )
        }

        AccountOperationUnsupportedException.Kind.VIEW_ONLY -> {
            UnsupportedOperationCopy(
                title = viewOnlyContent.unsupportedOperationTitle,
                message = viewOnlyContent.unsupportedOperationMessage,
            )
        }
    }

/**
 * Throws [AccountOperationUnsupportedException] of [AccountOperationUnsupportedException.Kind.VIEW_ONLY]
 * when this account cannot spend; returns it unchanged otherwise.
 */
fun <T : WalletAccount> T.requireSpendable(): T {
    if (!isSpendable) {
        throw AccountOperationUnsupportedException.viewOnly()
    }
    return this
}

/**
 * The full viewing key being imported belongs to an account the wallet already holds, either an
 * earlier import or one of the wallet's own accounts. Carries no detail of the key.
 */
class ViewOnlyAccountAlreadyExistsException : Exception("This viewing key is already imported.")

/**
 * Importing a full viewing key failed in the SDK. Deliberately carries neither the SDK's exception
 * as its cause nor its message: both can echo the key back, and the error screen prints and emails
 * the stack trace.
 */
class ViewOnlyAccountImportException : Exception("The viewing key could not be imported.")

/**
 * The key and name a view-only import collected are gone from memory, typically because the
 * process died mid-flow; the user has to start the import again.
 */
class ViewOnlyPendingImportMissingException : Exception("There is no pending view-only import.")
