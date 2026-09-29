package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.exception.LedgerException
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mapping from every [LedgerException] to the issue the user sees, in each of the three
 * contexts: enrollment while the phone connects to the device, enrollment once the link is up, and
 * signing.
 *
 * Every [LedgerException] subclass has an internal constructor in the SDK, so the tests stub
 * instances rather than building them.
 */
class LedgerIssueMapperTest {
    @Test
    fun everyExceptionMapsToItsKindRetryAndCopyWhileConnecting() {
        rows().forEach { row -> assertIssue(row, LedgerIssueContext.ENROLLMENT_PAIRING, row.pairing) }
    }

    @Test
    fun everyExceptionMapsToItsKindRetryAndCopyOnceTheLinkIsUp() {
        rows().forEach { row -> assertIssue(row, LedgerIssueContext.ENROLLMENT, row.enrollment) }
    }

    @Test
    fun everyExceptionMapsToItsKindRetryAndCopyWhileSigning() {
        rows().forEach { row -> assertIssue(row, LedgerIssueContext.SIGNING, row.signing) }
    }

    @Test
    fun theNamedIssuesCarryTheirKindRetryAndCopy() {
        assertExpected(
            LedgerIssue.disconnectedDuringSetup,
            Expected(LedgerIssueKind.DISCONNECTED, LedgerIssueRetry.RECONNECT, Copies.disconnected),
            "disconnectedDuringSetup"
        )
        assertExpected(
            LedgerIssue.pairingFailed,
            Expected(LedgerIssueKind.PAIRING_FAILED, LedgerIssueRetry.RECONNECT, Copies.pairingFailed),
            "pairingFailed"
        )
        assertExpected(
            LedgerIssue.unknownWithoutRetry,
            Expected(LedgerIssueKind.UNKNOWN, LedgerIssueRetry.NONE, Copies.unknown),
            "unknownWithoutRetry"
        )
    }

    @Test
    fun theUnknownIssueWithoutRetryOnlyDiffersInItsRetry() {
        assertEquals(LedgerIssue.unknown.copy(retry = LedgerIssueRetry.NONE), LedgerIssue.unknownWithoutRetry)
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

    /**
     * The Figma "Waiting Indicator" rewords five issues; every other one shows the sheet's copy.
     */
    @Test
    fun theInlineCopyFollowsTheWaitingIndicatorAndOtherwiseTheSheet() {
        val context = LedgerIssueContext.ENROLLMENT_PAIRING
        val permissions = LedgerIssue.permissions
        assertEquals(R.drawable.ic_ledger_bluetooth_on, permissions.inlineIcon)
        assertEquals(R.drawable.ic_ledger_alert_circle, permissions.icon)
        assertInline(
            permissions,
            R.string.ledger_error_permissions_inlineTitle,
            R.string.ledger_error_permissions_inlineMessage
        )

        val bluetoothOff = mockk<LedgerException.BluetoothDisabled>(relaxed = true).toLedgerIssue(context)
        assertEquals(R.drawable.ic_ledger_bluetooth_off, bluetoothOff.inlineIcon)
        assertInline(
            bluetoothOff,
            R.string.ledger_error_bluetoothOff_inlineTitle,
            R.string.ledger_error_bluetoothOff_message
        )

        val unavailable =
            mockk<LedgerException.BluetoothUnavailable>(relaxed = true) { every { scanErrorCode } returns null }
                .toLedgerIssue(context)
        assertEquals(R.drawable.ic_ledger_bluetooth_off, unavailable.inlineIcon)
        assertInline(
            unavailable,
            R.string.ledger_error_unavailable_inlineTitle,
            R.string.ledger_error_unavailable_message
        )

        assertInline(
            LedgerIssue.noDevices,
            R.string.ledger_error_noDevices_inlineTitle,
            R.string.ledger_error_noDevices_message
        )

        listOf(
            mockk<LedgerException.Internal>(relaxed = true).toLedgerIssue(context),
            LedgerIssue.unknownWithoutRetry,
            mockk<LedgerException.TransactionNotSignable>(relaxed = true).toLedgerIssue(context),
        ).forEach { issue ->
            assertInline(issue, R.string.ledger_error_unknown_title, R.string.ledger_error_unknown_inlineMessage)
        }

        listOf(
            LedgerIssue.pairingFailed,
            LedgerIssue.disconnectedDuringSetup,
            mockk<LedgerException.AppTooOld>(relaxed = true).toLedgerIssue(context),
            mockk<LedgerException.AppNotInstalled>(relaxed = true).toLedgerIssue(context),
            mockk<LedgerException.AppOpenRejected>(relaxed = true).toLedgerIssue(context),
        ).forEach { issue ->
            assertEquals(issue.title, issue.inlineTitle, issue.kind.name)
            assertEquals(issue.message, issue.inlineMessage, issue.kind.name)
            assertEquals(issue.icon, issue.inlineIcon, issue.kind.name)
        }
    }

    private fun assertInline(
        issue: LedgerIssue,
        title: Int,
        message: Int,
    ) {
        assertEquals(title, issue.inlineTitle.resourceId(), issue.kind.name)
        assertEquals(message, issue.inlineMessage.resourceId(), issue.kind.name)
    }

    private fun assertIssue(
        row: Row,
        context: LedgerIssueContext,
        expected: Expected,
    ) = assertExpected(row.exception.toLedgerIssue(context), expected, "${row.name} in $context")

    private fun assertExpected(
        issue: LedgerIssue,
        expected: Expected,
        label: String,
    ) {
        assertEquals(expected.kind, issue.kind, label)
        assertEquals(expected.retry, issue.retry, label)
        assertEquals(expected.copy.title, issue.title.resourceId(), label)
        assertEquals(expected.copy.message, issue.message.resourceId(), label)
    }

    @Suppress("LongMethod")
    private fun rows(): List<Row> {
        val importRejected = Copies.importRejected
        val signRejected = Copies.signRejected
        val locked = Copies.locked
        val unknown = Copies.unknown
        val pairingFailed = Expected(LedgerIssueKind.PAIRING_FAILED, LedgerIssueRetry.RECONNECT, Copies.pairingFailed)
        val setupDisconnected =
            Expected(LedgerIssueKind.DISCONNECTED, LedgerIssueRetry.RECONNECT, Copies.disconnected)
        val signDisconnected =
            Expected(LedgerIssueKind.DISCONNECTED, LedgerIssueRetry.RECONNECT, Copies.signDisconnected)

        return listOf(
            Row.same(
                "restartable UserRejected",
                mockk<LedgerException.UserRejected>(relaxed = true) { every { isRestartable } returns true },
                enrollment = Expected(LedgerIssueKind.REJECTED, LedgerIssueRetry.SAME_LINK, importRejected),
                signing = Expected(LedgerIssueKind.REJECTED, LedgerIssueRetry.SAME_LINK, signRejected),
            ),
            Row.same(
                "non-restartable UserRejected",
                mockk<LedgerException.UserRejected>(relaxed = true) { every { isRestartable } returns false },
                enrollment = Expected(LedgerIssueKind.REJECTED, LedgerIssueRetry.RECONNECT, importRejected),
                signing = Expected(LedgerIssueKind.REJECTED, LedgerIssueRetry.RECONNECT, signRejected),
            ),
            Row.same(
                "WrongApp with a status word",
                mockk<LedgerException.WrongApp>(relaxed = true) { every { statusWord } returns WRONG_APP_STATUS },
                enrollment = Expected(LedgerIssueKind.LOCKED, LedgerIssueRetry.SAME_LINK, locked),
                signing = Expected(LedgerIssueKind.LOCKED, LedgerIssueRetry.RECONNECT, locked),
            ),
            Row.same(
                "WrongApp without a status word (app switch timed out)",
                mockk<LedgerException.WrongApp>(relaxed = true) { every { statusWord } returns null },
                enrollment = Expected(LedgerIssueKind.DISCONNECTED, LedgerIssueRetry.SAME_LINK, Copies.disconnected),
                signing = Expected(LedgerIssueKind.DISCONNECTED, LedgerIssueRetry.RECONNECT, Copies.signDisconnected),
            ),
            Row.same(
                "transient DeviceRefused",
                mockk<LedgerException.DeviceRefused>(relaxed = true) { every { isTransient } returns true },
                enrollment = Expected(LedgerIssueKind.LOCKED, LedgerIssueRetry.SAME_LINK, locked),
                signing = Expected(LedgerIssueKind.LOCKED, LedgerIssueRetry.RECONNECT, locked),
            ),
            Row.all(
                "permanent DeviceRefused",
                mockk<LedgerException.DeviceRefused>(relaxed = true) { every { isTransient } returns false },
                Expected(LedgerIssueKind.UNKNOWN, LedgerIssueRetry.RECONNECT, unknown),
            ),
            Row.same(
                "DerivationBudgetExhausted",
                mockk<LedgerException.DerivationBudgetExhausted>(relaxed = true),
                enrollment = Expected(LedgerIssueKind.RESTART_APP, LedgerIssueRetry.SAME_LINK, Copies.restartApp),
                signing = Expected(LedgerIssueKind.RESTART_APP, LedgerIssueRetry.RECONNECT, Copies.restartApp),
            ),
            Row.all(
                "AppTooOld",
                mockk<LedgerException.AppTooOld>(relaxed = true),
                Expected(LedgerIssueKind.APP_TOO_OLD, LedgerIssueRetry.RECONNECT, Copies.appTooOld),
            ),
            Row.all(
                "AppNotInstalled",
                mockk<LedgerException.AppNotInstalled>(relaxed = true),
                Expected(LedgerIssueKind.APP_NOT_INSTALLED, LedgerIssueRetry.RECONNECT, Copies.appNotInstalled),
            ),
            Row.all(
                "AppOpenRejected",
                mockk<LedgerException.AppOpenRejected>(relaxed = true),
                Expected(LedgerIssueKind.OPEN_APP_REJECTED, LedgerIssueRetry.RECONNECT, Copies.openAppRejected),
            ),
            Row.same(
                "DeviceMismatch",
                mockk<LedgerException.DeviceMismatch>(relaxed = true),
                enrollment = Expected(LedgerIssueKind.WRONG_DEVICE, LedgerIssueRetry.RECONNECT, unknown),
                signing = Expected(LedgerIssueKind.WRONG_DEVICE, LedgerIssueRetry.RECONNECT, Copies.wrongDevice),
            ),
            Row.all(
                "CapsMismatch",
                mockk<LedgerException.CapsMismatch>(relaxed = true),
                Expected(LedgerIssueKind.UNKNOWN, LedgerIssueRetry.RECONNECT, unknown),
            ),
            Row.all(
                "MalformedReply",
                mockk<LedgerException.MalformedReply>(relaxed = true),
                Expected(LedgerIssueKind.UNKNOWN, LedgerIssueRetry.RECONNECT, unknown),
            ),
            Row.all(
                "Internal",
                mockk<LedgerException.Internal>(relaxed = true),
                Expected(LedgerIssueKind.UNKNOWN, LedgerIssueRetry.RECONNECT, unknown),
            ),
            Row.same(
                "InvalidInput",
                mockk<LedgerException.InvalidInput>(relaxed = true),
                enrollment = Expected(LedgerIssueKind.UNKNOWN, LedgerIssueRetry.RECONNECT, unknown),
                signing = Expected(LedgerIssueKind.UNBOUND, LedgerIssueRetry.NONE, Copies.unbound),
            ),
            Row.same(
                "TransactionNotSignable",
                mockk<LedgerException.TransactionNotSignable>(relaxed = true),
                enrollment = Expected(LedgerIssueKind.NOT_SIGNABLE, LedgerIssueRetry.NONE, unknown),
                signing = Expected(LedgerIssueKind.NOT_SIGNABLE, LedgerIssueRetry.NONE, Copies.notSignable),
            ),
            Row.all(
                "BluetoothUnavailable with a scan error code",
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) { every { scanErrorCode } returns 1 },
                Expected(LedgerIssueKind.NO_DEVICES, LedgerIssueRetry.RECONNECT, Copies.noDevices),
            ),
            Row.all(
                "BluetoothUnavailable without a scan error code",
                mockk<LedgerException.BluetoothUnavailable>(relaxed = true) { every { scanErrorCode } returns null },
                Expected(LedgerIssueKind.BLUETOOTH_UNAVAILABLE, LedgerIssueRetry.NONE, Copies.unavailable),
            ),
            Row.all(
                "BluetoothUnauthorized",
                mockk<LedgerException.BluetoothUnauthorized>(relaxed = true),
                Expected(LedgerIssueKind.PERMISSIONS, LedgerIssueRetry.RECONNECT, Copies.permissions),
            ),
            Row.all(
                "BluetoothDisabled",
                mockk<LedgerException.BluetoothDisabled>(relaxed = true),
                Expected(LedgerIssueKind.BLUETOOTH_OFF, LedgerIssueRetry.RECONNECT, Copies.bluetoothOff),
            ),
            Row(
                "DeviceNotFound",
                mockk<LedgerException.DeviceNotFound>(relaxed = true),
                pairing = pairingFailed,
                enrollment = setupDisconnected,
                signing = signDisconnected,
            ),
            Row(
                "ConnectionFailed",
                mockk<LedgerException.ConnectionFailed>(relaxed = true),
                pairing = pairingFailed,
                enrollment = setupDisconnected,
                signing = signDisconnected,
            ),
            Row(
                "Timeout",
                mockk<LedgerException.Timeout>(relaxed = true),
                pairing = pairingFailed,
                enrollment = setupDisconnected,
                signing = signDisconnected,
            ),
            Row.all(
                "PairingRefused",
                mockk<LedgerException.PairingRefused>(relaxed = true),
                pairingFailed,
            ),
            Row(
                "Disconnected",
                mockk<LedgerException.Disconnected>(relaxed = true),
                pairing = setupDisconnected,
                enrollment = setupDisconnected,
                signing = signDisconnected,
            ),
        )
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

    private object Copies {
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
    }

    private data class Copy(
        val title: Int,
        val message: Int,
    )

    private data class Expected(
        val kind: LedgerIssueKind,
        val retry: LedgerIssueRetry,
        val copy: Copy,
    )

    private data class Row(
        val name: String,
        val exception: LedgerException,
        val pairing: Expected,
        val enrollment: Expected,
        val signing: Expected,
    ) {
        companion object {
            /**
             * An exception both enrollment contexts map alike.
             */
            fun same(
                name: String,
                exception: LedgerException,
                enrollment: Expected,
                signing: Expected,
            ) = Row(name, exception, enrollment, enrollment, signing)

            /**
             * An exception every context maps alike.
             */
            fun all(
                name: String,
                exception: LedgerException,
                expected: Expected,
            ) = Row(name, exception, expected, expected, expected)
        }
    }
}

private const val WRONG_APP_STATUS = 0x6E00
