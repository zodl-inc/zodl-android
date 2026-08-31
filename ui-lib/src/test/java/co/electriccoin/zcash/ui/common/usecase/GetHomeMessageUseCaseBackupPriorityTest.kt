package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MOB-1787 — an unbacked seed that has received a balance (WalletBackupMessageUseCase's
 * WalletBackupData.Available, MOB-909's balance-gated condition) must outrank every home-screen
 * widget, including RuntimeMessage states like migration and sync errors, which previously always
 * won unconditionally via [createHomeMessage]'s `runtimeMessage != null` short-circuit.
 */
class GetHomeMessageUseCaseBackupPriorityTest {
    /** Minimal concrete [MigrationHomeMessage] stand-in — the real implementation lives in the
     * feature-migration module, which ui-lib does not depend on. */
    private object FakeMigrationMessage : MigrationHomeMessage()

    private val syncError = HomeMessageData.Error(SynchronizerError.Critical(cause = null))

    @Test
    fun `balance received and not backed up outranks active migration message`() {
        val result =
            createHomeMessage(
                runtimeMessage = FakeMigrationMessage,
                backup = WalletBackupData.Available(WalletBackupLockoutDuration.TWO_DAYS),
                isTorVisible = false,
                isCurrencyConversionEnabled = false,
                isCoinholderPollingVisible = false,
                isCrashReportingVisible = false,
            )

        assertEquals(HomeMessageData.Backup, result)
    }

    @Test
    fun `balance received and not backed up outranks active sync error message`() {
        val result =
            createHomeMessage(
                runtimeMessage = syncError,
                backup = WalletBackupData.Available(WalletBackupLockoutDuration.TWO_DAYS),
                isTorVisible = false,
                isCurrencyConversionEnabled = false,
                isCoinholderPollingVisible = false,
                isCrashReportingVisible = false,
            )

        assertEquals(HomeMessageData.Backup, result)
    }

    @Test
    fun `zero balance regression guard leaves runtime message unaffected`() {
        // MOB-909: WalletBackupMessageUseCaseImpl reports Unavailable when there's no
        // ReceiveTransaction yet (balance = 0), regardless of the backup flag itself.
        val result =
            createHomeMessage(
                runtimeMessage = FakeMigrationMessage,
                backup = WalletBackupData.Unavailable,
                isTorVisible = false,
                isCurrencyConversionEnabled = false,
                isCoinholderPollingVisible = false,
                isCrashReportingVisible = false,
            )

        assertEquals(FakeMigrationMessage, result)
    }

    @Test
    fun `already backed up seed leaves runtime message unaffected`() {
        val result =
            createHomeMessage(
                runtimeMessage = syncError,
                backup = WalletBackupData.Unavailable,
                isTorVisible = false,
                isCurrencyConversionEnabled = false,
                isCoinholderPollingVisible = false,
                isCrashReportingVisible = false,
            )

        assertEquals(syncError, result)
    }

    @Test
    fun `no urgent backup and no runtime message preserves normal Prioritized ordering`() {
        // Regression guard for task 4 — relative ordering of the rest of the Prioritized family
        // must be unaffected by the MOB-1787 reordering.
        val result =
            createHomeMessage(
                runtimeMessage = null,
                backup = WalletBackupData.Unavailable,
                isTorVisible = true,
                isCurrencyConversionEnabled = true,
                isCoinholderPollingVisible = true,
                isCrashReportingVisible = true,
            )

        assertEquals(HomeMessageData.CoinholderPolling, result)
    }

    // --- prioritizeHomeMessage: the urgent Backup message must also bypass the hysteresis that
    // otherwise protects against flicker between optional/dismissible Prioritized messages, since
    // otherwise a previously-shown RuntimeMessage (priority Int.MAX_VALUE) cached as
    // lastShownMessage would make Backup's finite priority (5) look "lower" and get filtered out.

    @Test
    fun `urgent backup is shown immediately even right after a RuntimeMessage was last shown`() {
        val result =
            prioritizeHomeMessage(
                message = HomeMessageData.Backup,
                lastMessage = FakeMigrationMessage,
                lastShownMessage = FakeMigrationMessage,
            )

        assertEquals(HomeMessageData.Backup, result)
    }

    @Test
    fun `urgent backup is shown immediately on first ever message`() {
        val result =
            prioritizeHomeMessage(
                message = HomeMessageData.Backup,
                lastMessage = null,
                lastShownMessage = null,
            )

        assertEquals(HomeMessageData.Backup, result)
    }
}
