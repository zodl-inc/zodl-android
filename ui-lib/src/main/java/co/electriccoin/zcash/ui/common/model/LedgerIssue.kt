package co.electriccoin.zcash.ui.common.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import cash.z.ecc.android.sdk.exception.LedgerException
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * Which Ledger flow an issue arose in; a few failures read differently when a transaction is being
 * signed than while an account is being connected, and while connecting, differently before the link
 * to the device is up than after.
 */
enum class LedgerIssueContext {
    /**
     * Enrollment while the phone connects to the device, the step that bonds the two, before the
     * link is up. A lost or refused connection here means pairing failed.
     */
    ENROLLMENT_PAIRING,

    /**
     * Enrollment once the link is up: opening the Zcash app and exporting the account. A lost
     * connection here means the device disconnected during setup.
     */
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
    APP_NOT_INSTALLED,
    OPEN_APP_REJECTED,
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
 *
 * [title], [message] and [icon] are the error sheet's; [inlineIcon], [inlineTitle] and
 * [inlineMessage] are what a connect page keeps showing over its placeholder rows, which the Figma
 * "Waiting Indicator" words differently for a few issues and otherwise shares with the sheet.
 */
data class LedgerIssue(
    val kind: LedgerIssueKind,
    val retry: LedgerIssueRetry,
    val title: StringResource,
    val message: StringResource,
    @get:DrawableRes
    val inlineIcon: Int = kind.icon,
    val inlineTitle: StringResource = title,
    val inlineMessage: StringResource = message,
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
            ).copy(inlineTitle = stringRes(R.string.ledger_error_noDevices_inlineTitle))

        /**
         * The runtime Bluetooth permissions were denied.
         */
        val permissions: LedgerIssue =
            issue(
                LedgerIssueKind.PERMISSIONS,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_permissions_title,
                R.string.ledger_error_permissions_message
            ).copy(
                inlineIcon = R.drawable.ic_ledger_bluetooth_on,
                inlineTitle = stringRes(R.string.ledger_error_permissions_inlineTitle),
                inlineMessage = stringRes(R.string.ledger_error_permissions_inlineMessage),
            )

        /**
         * A failure nothing more specific describes; looking for the device again may help.
         */
        val unknown: LedgerIssue = unknownIssue(LedgerIssueKind.UNKNOWN)

        /**
         * A failure nothing more specific describes and that trying again cannot fix, such as a
         * signing session with nothing left to sign.
         */
        val unknownWithoutRetry: LedgerIssue = unknown.copy(retry = LedgerIssueRetry.NONE)

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
         * Connecting to the device, the step that bonds it with the phone, failed or was refused.
         */
        val pairingFailed: LedgerIssue =
            issue(
                LedgerIssueKind.PAIRING_FAILED,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_pairingFailed_title,
                R.string.ledger_error_pairingFailed_message
            )

        /**
         * The link to the device was lost while an account was being connected, after the phone
         * had reached the device. A [LedgerPairingTimedOutException] reads the same: the transport
         * is gone either way.
         */
        val disconnectedDuringSetup: LedgerIssue =
            issue(
                LedgerIssueKind.DISCONNECTED,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_disconnected_title,
                R.string.ledger_error_disconnected_message
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
 * locked device answers with a transient [LedgerException.DeviceRefused], and a device that did not
 * reach the Zcash app with [LedgerException.WrongApp]. A lost connection while the phone connects
 * to the device means pairing failed; once the link is up, it means the device disconnected.
 *
 * While signing, a device that has to be unlocked, switched to the Zcash app or have that app
 * restarted is looked for again on Try again, so the fresh link opens the Zcash app first; during
 * enrollment every try connects anew anyway.
 */
@Suppress("CyclomaticComplexMethod")
fun LedgerException.toLedgerIssue(context: LedgerIssueContext): LedgerIssue =
    when (this) {
        is LedgerException.UserRejected -> {
            rejected(context, isRestartable)
        }

        is LedgerException.WrongApp -> {
            locked(context)
        }

        is LedgerException.DeviceRefused -> {
            if (isTransient) locked(context) else LedgerIssue.unknown
        }

        is LedgerException.DerivationBudgetExhausted -> {
            issue(
                LedgerIssueKind.RESTART_APP,
                context.appRetry,
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

        is LedgerException.AppNotInstalled -> {
            issue(
                LedgerIssueKind.APP_NOT_INSTALLED,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_appNotInstalled_title,
                R.string.ledger_error_appNotInstalled_message
            )
        }

        is LedgerException.AppOpenRejected -> {
            issue(
                LedgerIssueKind.OPEN_APP_REJECTED,
                LedgerIssueRetry.RECONNECT,
                R.string.ledger_error_openAppRejected_title,
                R.string.ledger_error_openAppRejected_message
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
            ).copy(inlineTitle = stringRes(R.string.ledger_error_bluetoothOff_inlineTitle))
        }

        is LedgerException.PairingRefused -> {
            LedgerIssue.pairingFailed
        }

        is LedgerException.DeviceNotFound,
        is LedgerException.ConnectionFailed,
        is LedgerException.Timeout -> {
            if (context == LedgerIssueContext.ENROLLMENT_PAIRING) LedgerIssue.pairingFailed else disconnected(context)
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
        LedgerIssueContext.ENROLLMENT_PAIRING,
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

private fun locked(context: LedgerIssueContext) =
    issue(
        LedgerIssueKind.LOCKED,
        context.appRetry,
        R.string.ledger_error_locked_title,
        R.string.ledger_error_locked_message
    )

/**
 * What Try again does once the user has fixed the app state on the device: while signing, a fresh
 * link, whose connect opens the Zcash app first; while enrolling, the device kept selected.
 */
private val LedgerIssueContext.appRetry: LedgerIssueRetry
    get() =
        when (this) {
            LedgerIssueContext.ENROLLMENT_PAIRING,
            LedgerIssueContext.ENROLLMENT -> {
                LedgerIssueRetry.SAME_LINK
            }

            LedgerIssueContext.SIGNING -> {
                LedgerIssueRetry.RECONNECT
            }
        }

/**
 * "Something Went Wrong"; the inline issue breaks its message over two lines where the sheet runs it
 * on.
 */
private fun unknownIssue(kind: LedgerIssueKind) =
    issue(
        kind,
        LedgerIssueRetry.RECONNECT,
        R.string.ledger_error_unknown_title,
        R.string.ledger_error_unknown_message
    ).copy(inlineMessage = stringRes(R.string.ledger_error_unknown_inlineMessage))

/**
 * A device mismatch cannot occur while pairing, so the connect flow has no copy of its own for it.
 */
private fun wrongDevice(context: LedgerIssueContext) =
    when (context) {
        LedgerIssueContext.ENROLLMENT_PAIRING,
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
        LedgerIssueContext.ENROLLMENT_PAIRING,
        LedgerIssueContext.ENROLLMENT -> {
            LedgerIssue.unknown
        }

        LedgerIssueContext.SIGNING -> {
            LedgerIssue.unbound
        }
    }

private fun notSignable(context: LedgerIssueContext) =
    when (context) {
        LedgerIssueContext.ENROLLMENT_PAIRING,
        LedgerIssueContext.ENROLLMENT -> {
            unknownIssue(LedgerIssueKind.NOT_SIGNABLE).copy(retry = LedgerIssueRetry.NONE)
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
        ).copy(inlineTitle = stringRes(R.string.ledger_error_unavailable_inlineTitle))
    }

private fun disconnected(context: LedgerIssueContext) =
    when (context) {
        LedgerIssueContext.ENROLLMENT_PAIRING,
        LedgerIssueContext.ENROLLMENT -> {
            LedgerIssue.disconnectedDuringSetup
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
