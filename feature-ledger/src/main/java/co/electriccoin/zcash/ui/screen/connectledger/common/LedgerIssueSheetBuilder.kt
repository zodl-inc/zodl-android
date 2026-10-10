package co.electriccoin.zcash.ui.screen.connectledger.common

import cash.z.ecc.android.sdk.exception.LedgerException
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.toLedgerIssue
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * Builds the error sheets of the Ledger enrollment screens, so every screen that talks to the
 * device labels and wires an issue the same way.
 *
 * @param canRequestPermissionsAgain whether a permissions issue can still be answered with another
 * in-app request, rather than only in Settings
 */
internal class LedgerIssueSheetBuilder(
    private val canRequestPermissionsAgain: Boolean,
    private val onTryAgain: () -> Unit,
    private val onRequestPermissionsAgain: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onEnableBluetooth: () -> Unit,
    private val onClose: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    /**
     * An issue that trying again cannot fix offers no Try again; Bluetooth being unavailable keeps
     * its Close.
     */
    fun sheet(issue: LedgerIssue) =
        LedgerErrorSheetState(
            icon = issue.icon,
            isBadge = issue.isBadge,
            title = issue.title,
            message = issue.message,
            primary =
                if (hasAction(issue)) {
                    ButtonState(
                        text = actionText(issue),
                        onClick = action(issue),
                    )
                } else {
                    null
                },
            secondary = null,
            onBack = onDismiss,
        )

    /**
     * Whether the sheet, and the page with it, offers a button for [issue] at all.
     */
    fun hasAction(issue: LedgerIssue): Boolean =
        issue.retry != LedgerIssueRetry.NONE || issue.kind == LedgerIssueKind.BLUETOOTH_UNAVAILABLE

    /**
     * @param tryAgainText the label for an issue that trying again fixes; a page's own button may
     * word it after its step rather than as the sheet's Try again
     */
    fun actionText(
        issue: LedgerIssue,
        tryAgainText: StringResource = stringRes(R.string.ledger_error_tryAgain),
    ): StringResource =
        when {
            issue.kind == LedgerIssueKind.BLUETOOTH_UNAVAILABLE -> {
                stringRes(R.string.ledger_error_unavailable_cta)
            }

            issue.kind == LedgerIssueKind.PERMISSIONS && !canRequestPermissionsAgain -> {
                stringRes(R.string.ledger_error_permissions_cta)
            }

            else -> {
                tryAgainText
            }
        }

    /**
     * What the sheet's primary button does, and the page's own button with it.
     */
    fun action(issue: LedgerIssue): () -> Unit =
        when (issue.kind) {
            LedgerIssueKind.PERMISSIONS -> {
                if (canRequestPermissionsAgain) onRequestPermissionsAgain else onOpenSettings
            }

            LedgerIssueKind.BLUETOOTH_OFF -> {
                onEnableBluetooth
            }

            LedgerIssueKind.BLUETOOTH_UNAVAILABLE -> {
                onClose
            }

            else -> {
                onTryAgain
            }
        }
}

/**
 * The sheet shown when the Ledger's account is already in the wallet.
 */
internal fun ledgerAlreadyAddedSheet(
    onGoToAccount: () -> Unit,
    onDismiss: () -> Unit,
) = LedgerErrorSheetState(
    icon = R.drawable.ic_ledger_alert_circle,
    isBadge = true,
    title = stringRes(R.string.ledger_error_alreadyAdded_title),
    message = stringRes(R.string.ledger_error_alreadyAdded_message),
    primary =
        ButtonState(
            text = stringRes(R.string.ledger_error_alreadyAdded_primary),
            onClick = onGoToAccount,
        ),
    secondary =
        ButtonState(
            text = stringRes(R.string.ledger_error_alreadyAdded_secondary),
            style = ButtonStyle.SECONDARY,
            onClick = onDismiss,
        ),
    onBack = onDismiss,
)

/**
 * Logs only the exception's class and its [LedgerException.reason], which the SDK keeps free of
 * device identifiers.
 */
internal fun LedgerException.toEnrollmentIssue(context: LedgerIssueContext): LedgerIssue {
    Twig.warn { "Ledger enrollment failed: ${javaClass.simpleName}, reason: $reason" }
    return toLedgerIssue(context)
}
