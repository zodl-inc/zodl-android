package co.electriccoin.zcash.ui.common.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import cash.z.ecc.android.sdk.exception.LedgerException
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * Which Ledger flow an issue arose in; a few failures read differently when a transaction is being
 * signed than while an account is being connected.
 */
enum class LedgerIssueContext {
    ENROLLMENT,
    SIGNING,
}

/**
 * What "Try again" can do about an issue: repeat the request over the same device link, look for
 * the device again, or nothing at all.
 */
enum class LedgerIssueRetry {
    SAME_LINK,
    RECONNECT,
    NONE,
}

enum class LedgerIssueKind(
    @get:DrawableRes val icon: Int = R.drawable.ic_ledger_alert_circle
) {
    NO_DEVICES,
    BLUETOOTH_UNAVAILABLE(R.drawable.ic_ledger_bluetooth_off),
    BLUETOOTH_OFF(R.drawable.ic_ledger_bluetooth_off),
    PERMISSIONS,
    PAIRING_FAILED,
    LOCKED,
    APP_TOO_OLD,
    RESTART_APP,
    WRONG_DEVICE,
    REJECTED,
    DISCONNECTED,
    NOT_SIGNABLE,
    UNBOUND,
    UNKNOWN,
}

/**
 * A Ledger failure as the user sees it, shared by the connect flow and the signing sheet.
 */
data class LedgerIssue(
    val kind: LedgerIssueKind,
    val retry: LedgerIssueRetry,
    val title: StringResource,
    val message: StringResource,
) {
    @get:DrawableRes
    val icon: Int
        get() = kind.icon

    companion object {
        /**
         * A scan that found no Ledger before it timed out.
         */
        val noDevices: LedgerIssue =
            issue(
                LedgerIssueKind.NO_DEVICES,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_noDevices_title,
                R.string.ledger_error_noDevices_message
            )

        /**
         * The runtime Bluetooth permissions were denied.
         */
        val permissions: LedgerIssue =
            issue(
                LedgerIssueKind.PERMISSIONS,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_permissions_title,
                R.string.ledger_error_permissions_message
            )

        /**
         * A failure nothing more specific describes; looking for the device again may help.
         */
        val unknown: LedgerIssue = unknownIssue(LedgerIssueKind.UNKNOWN)

        /**
         * The selected account has no usable Ledger binding, so it cannot be signed for until it is
         * connected again.
         */
        val unbound: LedgerIssue =
            issue(
                LedgerIssueKind.UNBOUND,
                LedgerIssueRetry.NONE,
                R.string.ledger_sign_error_unbound_title,
                R.string.ledger_sign_error_unbound_message
            )

        /**
         * The link to the device was lost while a transaction was being signed.
         */
        val disconnectedWhileSigning: LedgerIssue =
            issue(
                LedgerIssueKind.DISCONNECTED,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_disconnected_title,
                R.string.ledger_sign_error_disconnected_message
            )
    }
}

/**
 * Maps a [LedgerException] to the issue shown for it.
 *
 * A [LedgerException.BluetoothUnavailable] carrying a scan error code means the scan itself failed
 * to start; without one the phone has no Bluetooth LE at all, which nothing in the app can fix. A
 * locked device answers with a transient [LedgerException.DeviceRefused]. A timeout while connecting
 * means pairing failed; while signing, it means the link to the device was lost.
 */
@Suppress("CyclomaticComplexMethod")
fun LedgerException.toLedgerIssue(context: LedgerIssueContext): LedgerIssue =
    when (this) {
        is LedgerException.UserRejected -> {
            rejected(context, isRestartable)
        }

        is LedgerException.WrongApp -> {
            locked()
        }

        is LedgerException.DeviceRefused -> {
            if (isTransient) locked() else LedgerIssue.unknown
        }

        is LedgerException.DerivationBudgetExhausted -> {
            issue(
                LedgerIssueKind.RESTART_APP,
                LedgerIssueRetry.SAME_LINK,
                R.string.ledger_error_restartApp_title,
                R.string.ledger_error_restartApp_message
            )
        }

        is LedgerException.AppTooOld -> {
            issue(
                LedgerIssueKind.APP_TOO_OLD,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_appTooOld_title,
                R.string.ledger_error_appTooOld_message
            )
        }

        is LedgerException.DeviceMismatch -> {
            wrongDevice(context)
        }

        is LedgerException.CapsMismatch,
        is LedgerException.MalformedReply,
        is LedgerException.Internal -> {
            LedgerIssue.unknown
        }

        is LedgerException.InvalidInput -> {
            invalidInput(context)
        }

        is LedgerException.TransactionNotSignable -> {
            notSignable(context)
        }

        is LedgerException.BluetoothUnavailable -> {
            bluetoothUnavailable(scanErrorCode)
        }

        is LedgerException.BluetoothUnauthorized -> {
            LedgerIssue.permissions
        }

        is LedgerException.BluetoothDisabled -> {
            issue(
                LedgerIssueKind.BLUETOOTH_OFF,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_bluetoothOff_title,
                R.string.ledger_error_bluetoothOff_message
            )
        }

        is LedgerException.DeviceNotFound,
        is LedgerException.ConnectionFailed,
        is LedgerException.PairingRefused,
        is LedgerException.Timeout -> {
            if (this is LedgerException.Timeout && context == LedgerIssueContext.SIGNING) {
                LedgerIssue.disconnectedWhileSigning
            } else {
                issue(
                    LedgerIssueKind.PAIRING_FAILED,
                    LedgerIssueRetry.RECONNECT,
                    R.string.ledger_error_pairingFailed_title,
                    R.string.ledger_error_pairingFailed_message
                )
            }
        }

        is LedgerException.Disconnected -> {
            disconnected(context)
        }
    }

/**
 * A rejection the device can be asked about again is retried over the same link; any other one
 * needs the device to be found again.
 */
private fun rejected(
    context: LedgerIssueContext,
    isRestartable: Boolean
): LedgerIssue {
    val retry = if (isRestartable) LedgerIssueRetry.SAME_LINK else LedgerIssueRetry.RECONNECT
    return when (context) {
        LedgerIssueContext.ENROLLMENT -> {
            issue(
                LedgerIssueKind.REJECTED,
                retry,
                R.string.ledger_error_importRejected_title,
                R.string.ledger_error_importRejected_message
            )
        }

        LedgerIssueContext.SIGNING -> {
            issue(
                LedgerIssueKind.REJECTED,
                retry,
                R.string.ledger_sign_error_rejected_title,
                R.string.ledger_sign_error_rejected_message
            )
        }
    }
}

private fun locked() =
    issue(
        LedgerIssueKind.LOCKED,
        LedgerIssueRetry.SAME_LINK,
        R.string.ledger_error_locked_title,
        R.string.ledger_error_locked_message
    )

private fun unknownIssue(kind: LedgerIssueKind) =
    issue(
        kind,
        LedgerIssueRetry.RECONNECT,
        R.string.ledger_error_unknown_title,
        R.string.ledger_error_unknown_message
    )

/**
 * A device mismatch cannot occur while pairing, so the connect flow has no copy of its own for it.
 */
private fun wrongDevice(context: LedgerIssueContext) =
    when (context) {
        LedgerIssueContext.ENROLLMENT -> {
            unknownIssue(LedgerIssueKind.WRONG_DEVICE)
        }

        LedgerIssueContext.SIGNING -> {
            issue(
                LedgerIssueKind.WRONG_DEVICE,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_sign_error_wrongDevice_title,
                R.string.ledger_sign_error_wrongDevice_message
            )
        }
    }

/**
 * While signing, invalid input means the account's stored pairing is unusable.
 */
private fun invalidInput(context: LedgerIssueContext) =
    when (context) {
        LedgerIssueContext.ENROLLMENT -> {
            LedgerIssue.unknown
        }

        LedgerIssueContext.SIGNING -> {
            LedgerIssue.unbound
        }
    }

private fun notSignable(context: LedgerIssueContext) =
    when (context) {
        LedgerIssueContext.ENROLLMENT -> {
            issue(
                LedgerIssueKind.NOT_SIGNABLE,
                LedgerIssueRetry.NONE,
                R.string.ledger_error_unknown_title,
                R.string.ledger_error_unknown_message
            )
        }

        LedgerIssueContext.SIGNING -> {
            issue(
                LedgerIssueKind.NOT_SIGNABLE,
                LedgerIssueRetry.NONE,
                R.string.ledger_sign_error_notSignable_title,
                R.string.ledger_sign_error_notSignable_message
            )
        }
    }

private fun bluetoothUnavailable(scanErrorCode: Int?) =
    if (scanErrorCode != null) {
        LedgerIssue.noDevices
    } else {
        issue(
            LedgerIssueKind.BLUETOOTH_UNAVAILABLE,
            LedgerIssueRetry.NONE,
            R.string.ledger_error_unavailable_title,
            R.string.ledger_error_unavailable_message
        )
    }

private fun disconnected(context: LedgerIssueContext) =
    when (context) {
        LedgerIssueContext.ENROLLMENT -> {
            issue(
                LedgerIssueKind.DISCONNECTED,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_disconnected_title,
                R.string.ledger_error_disconnected_message
            )
        }

        LedgerIssueContext.SIGNING -> {
            LedgerIssue.disconnectedWhileSigning
        }
    }

private fun issue(
    kind: LedgerIssueKind,
    retry: LedgerIssueRetry,
    @StringRes title: Int,
    @StringRes message: Int,
) = LedgerIssue(
    kind = kind,
    retry = retry,
    title = stringRes(title),
    message = stringRes(message),
)
