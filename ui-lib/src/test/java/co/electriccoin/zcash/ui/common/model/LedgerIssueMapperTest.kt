package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.exception.LedgerException
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mapping from every [LedgerException] to the issue the user sees, in both flows.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
class LedgerIssueMapperTest {
    @Test
    fun everyExceptionMapsToItsKindRetryAndCopyWhileConnecting() {
        rows().forEach { row ->
            assertIssue(row, LedgerIssueContext.ENROLLMENT, row.enrollmentKind, row.enrollment)
        }
    }

    @Test
    fun everyExceptionMapsToItsKindRetryAndCopyWhileSigning() {
        rows().forEach { row ->
            assertIssue(row, LedgerIssueContext.SIGNING, row.signingKind, row.signing)
        }
    }

    @Test
    fun onlyTheBluetoothKindsUseTheBluetoothOffIcon() {
        LedgerIssueKind.entries.forEach { kind ->
            val expected =
                when (kind) {
                    LedgerIssueKind.BLUETOOTH_OFF,
                    LedgerIssueKind.BLUETOOTH_UNAVAILABLE -> R.drawable.ic_ledger_bluetooth_off

                    else -> R.drawable.ic_ledger_alert_circle
                }
            assertEquals(expected, kind.icon, kind.name)
        }
    }

    @Test
    fun theIssueCarriesTheIconOfItsKind() {
        rows().forEach { row ->
            LedgerIssueContext.entries.forEach { context ->
                val issue = row.exception.toLedgerIssue(context)
                assertEquals(issue.kind.icon, issue.icon, row.name)
            }
        }
        assertEquals(
            R.drawable.ic_ledger_bluetooth_off,
            mockk<LedgerException.BluetoothDisabled>(relaxed = true)
                .toLedgerIssue(LedgerIssueContext.ENROLLMENT)
                .icon
        )
    }

    private fun assertIssue(
        row: Row,
        context: LedgerIssueContext,
        kind: LedgerIssueKind,
        copy: Copy,
    ) {
        val issue = row.exception.toLedgerIssue(context)
        val label = "${row.name} in $context"
        assertEquals(kind, issue.kind, label)
        val retry =
            when (context) {
                LedgerIssueContext.ENROLLMENT -> row.retry
                LedgerIssueContext.SIGNING -> row.signingRetry
            }
        assertEquals(retry, issue.retry, label)
        assertEquals(copy.title, issue.title.resourceId(), label)
        assertEquals(copy.message, issue.message.resourceId(), label)
    }

    private fun rows(): List<Row> {
        val importRejected =
            Copy(R.string.ledger_error_importRejected_title, R.string.ledger_error_importRejected_message)
        val signRejected = Copy(R.string.ledger_sign_error_rejected_title, R.string.ledger_sign_error_rejected_message)
        val locked = Copy(R.string.ledger_error_locked_title, R.string.ledger_error_locked_message)
        val unknown = Copy(R.string.ledger_error_unknown_title, R.string.ledger_error_unknown_message)
        val restartApp = Copy(R.string.ledger_error_restartApp_title, R.string.ledger_error_restartApp_message)
        val appTooOld = Copy(R.string.ledger_error_appTooOld_title, R.string.ledger_error_appTooOld_message)
        val appNotInstalled =
            Copy(R.string.ledger_error_appNotInstalled_title, R.string.ledger_error_appNotInstalled_message)
        val openAppRejected =
            Copy(R.string.ledger_error_openAppRejected_title, R.string.ledger_error_openAppRejected_message)
        val wrongDevice =
            Copy(R.string.ledger_sign_error_wrongDevice_title, R.string.ledger_sign_error_wrongDevice_message)
        val unbound = Copy(R.string.ledger_sign_error_unbound_title, R.string.ledger_sign_error_unbound_message)
        val notSignable =
            Copy(R.string.ledger_sign_error_notSignable_title, R.string.ledger_sign_error_notSignable_message)
        val noDevices = Copy(R.string.ledger_error_noDevices_title, R.string.ledger_error_noDevices_message)
        val unavailable = Copy(R.string.ledger_error_unavailable_title, R.string.ledger_error_unavailable_message)
        val permissions = Copy(R.string.ledger_error_permissions_title, R.string.ledger_error_permissions_message)
        val bluetoothOff = Copy(R.string.ledger_error_bluetoothOff_title, R.string.ledger_error_bluetoothOff_message)
        val pairingFailed =
            Copy(R.string.ledger_error_pairingFailed_title, R.string.ledger_error_pairingFailed_message)
        val disconnected =
            Copy(R.string.ledger_error_disconnected_title, R.string.ledger_error_disconnected_message)
        val signDisconnected =
            Copy(R.string.ledger_error_disconnected_title, R.string.ledger_sign_error_disconnected_message)

        return listOf(
            Row(
                "restartable UserRejected",
                mockk<LedgerException.UserRejected>(relaxed = true) { every { isRestartable } returns true },
                LedgerIssueKind.REJECTED,
                LedgerIssueRetry.SAME_LINK,
                importRejected,
                signRejected
            ),
            Row(
                "non-restartable UserRejected",
                mockk<LedgerException.UserRejected>(relaxed = true) { every { isRestartable } returns false },
                LedgerIssueKind.REJECTED,
                LedgerIssueRetry.RECONNECT,
                importRejected,
                signRejected
            ),
            Row(
                "WrongApp",
                mockk<LedgerException.WrongApp>(relaxed = true),
                LedgerIssueKind.LOCKED,
                LedgerIssueRetry.SAME_LINK,
                locked,
                locked
            ),
            Row(
                "transient DeviceRefused",
                mockk<LedgerException.DeviceRefused>(relaxed = true) { every { isTransient } returns true },
                LedgerIssueKind.LOCKED,
                LedgerIssueRetry.SAME_LINK,
                locked,
                locked
            ),
            Row(
                "permanent DeviceRefused",
                mockk<LedgerException.DeviceRefused>(relaxed = true) { every { isTransient } returns false },
                LedgerIssueKind.UNKNOWN,
                LedgerIssueRetry.RECONNECT,
                unknown,
                unknown
            ),
            Row(
                "DerivationBudgetExhausted",
                mockk<LedgerException.DerivationBudgetExhausted>(relaxed = true),
                LedgerIssueKind.RESTART_APP,
                LedgerIssueRetry.SAME_LINK,
                restartApp,
                restartApp
            ),
            Row(
                "AppTooOld",
                mockk<LedgerException.AppTooOld>(relaxed = true),
                LedgerIssueKind.APP_TOO_OLD,
                LedgerIssueRetry.RECONNECT,
                appTooOld,
                appTooOld
            ),
            Row(
                "AppNotInstalled",
                mockk<LedgerException.AppNotInstalled>(relaxed = true),
                LedgerIssueKind.APP_NOT_INSTALLED,
                LedgerIssueRetry.RECONNECT,
                appNotInstalled,
                appNotInstalled
            ),
            Row(
                "AppOpenRejected",
                mockk<LedgerException.AppOpenRejected>(relaxed = true),
                LedgerIssueKind.OPEN_APP_REJECTED,
                LedgerIssueRetry.RECONNECT,
                openAppRejected,
                openAppRejected
            ),
            Row(
                "DeviceMismatch",
                mockk<LedgerException.DeviceMismatch>(relaxed = true),
                LedgerIssueKind.WRONG_DEVICE,
                LedgerIssueRetry.RECONNECT,
                unknown,
                wrongDevice
            ),
            Row(
                "CapsMismatch",
                mockk<LedgerException.CapsMismatch>(relaxed = true),
                LedgerIssueKind.UNKNOWN,
                LedgerIssueRetry.RECONNECT,
                unknown,
                unknown
            ),
            Row(
                "MalformedReply",
                mockk<LedgerException.MalformedReply>(relaxed = true),
                LedgerIssueKind.UNKNOWN,
                LedgerIssueRetry.RECONNECT,
                unknown,
                unknown
            ),
            Row(
                "Internal",
                mockk<LedgerException.Internal>(relaxed = true),
                LedgerIssueKind.UNKNOWN,
                LedgerIssueRetry.RECONNECT,
                unknown,
                unknown
            ),
            Row(
                "InvalidInput",
                mockk<LedgerException.InvalidInput>(relaxed = true),
                LedgerIssueKind.UNKNOWN,
                LedgerIssueRetry.RECONNECT,
                unknown,
                unbound,
                signingKind = LedgerIssueKind.UNBOUND,
                signingRetry = LedgerIssueRetry.NONE
            ),
            Row(
                "TransactionNotSignable",
                mockk<LedgerException.TransactionNotSignable>(relaxed = true),
                LedgerIssueKind.NOT_SIGNABLE,
                LedgerIssueRetry.NONE,
                unknown,
                notSignable
            ),
            Row(
                "BluetoothUnavailable with a scan error code",
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) { every { scanErrorCode } returns 1 },
                LedgerIssueKind.NO_DEVICES,
                LedgerIssueRetry.RECONNECT,
                noDevices,
                noDevices
            ),
            Row(
                "BluetoothUnavailable without a scan error code",
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) { every { scanErrorCode } returns null },
                LedgerIssueKind.BLUETOOTH_UNAVAILABLE,
                LedgerIssueRetry.NONE,
                unavailable,
                unavailable
            ),
            Row(
                "BluetoothUnauthorized",
                mockk<LedgerException.BluetoothUnauthorized>(relaxed = true),
                LedgerIssueKind.PERMISSIONS,
                LedgerIssueRetry.RECONNECT,
                permissions,
                permissions
            ),
            Row(
                "BluetoothDisabled",
                mockk<LedgerException.BluetoothDisabled>(relaxed = true),
                LedgerIssueKind.BLUETOOTH_OFF,
                LedgerIssueRetry.RECONNECT,
                bluetoothOff,
                bluetoothOff
            ),
            Row(
                "DeviceNotFound",
                mockk<LedgerException.DeviceNotFound>(relaxed = true),
                LedgerIssueKind.PAIRING_FAILED,
                LedgerIssueRetry.RECONNECT,
                pairingFailed,
                pairingFailed
            ),
            Row(
                "ConnectionFailed",
                mockk<LedgerException.ConnectionFailed>(relaxed = true),
                LedgerIssueKind.PAIRING_FAILED,
                LedgerIssueRetry.RECONNECT,
                pairingFailed,
                pairingFailed
            ),
            Row(
                "PairingRefused",
                mockk<LedgerException.PairingRefused>(relaxed = true),
                LedgerIssueKind.PAIRING_FAILED,
                LedgerIssueRetry.RECONNECT,
                pairingFailed,
                pairingFailed
            ),
            Row(
                "Timeout",
                mockk<LedgerException.Timeout>(relaxed = true),
                LedgerIssueKind.PAIRING_FAILED,
                LedgerIssueRetry.RECONNECT,
                pairingFailed,
                signDisconnected,
                signingKind = LedgerIssueKind.DISCONNECTED
            ),
            Row(
                "Disconnected",
                mockk<LedgerException.Disconnected>(relaxed = true),
                LedgerIssueKind.DISCONNECTED,
                LedgerIssueRetry.RECONNECT,
                disconnected,
                signDisconnected
            ),
        )
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

    private data class Copy(
        val title: Int,
        val message: Int,
    )

    private data class Row(
        val name: String,
        val exception: LedgerException,
        val enrollmentKind: LedgerIssueKind,
        val retry: LedgerIssueRetry,
        val enrollment: Copy,
        val signing: Copy,
        val signingKind: LedgerIssueKind = enrollmentKind,
        val signingRetry: LedgerIssueRetry = retry,
    )
}
