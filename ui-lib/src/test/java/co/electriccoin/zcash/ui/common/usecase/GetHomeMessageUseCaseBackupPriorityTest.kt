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

    /**
     * Regression guard for the other half of the branch: only [WalletBackupData.Available] jumps the
     * queue, so a wallet reported as [WalletBackupData.Unavailable] must leave the runtime message alone.
     * Which conditions collapse to `Unavailable` - no receive transaction yet, an already backed-up seed,
     * a still-running "remind me later" lockout - is covered directly in [WalletBackupMessageUseCaseImplTest].
     */
    @Test
    fun `unavailable backup does not outrank an active migration message`() {
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
    fun `unavailable backup does not outrank an active sync error message`() {
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

    /** Regression guard: the relative ordering of the rest of the Prioritized family is unaffected by MOB-1787. */
    @Test
    fun `no urgent backup and no runtime message preserves normal Prioritized ordering`() {
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

    /**
     * [prioritizeHomeMessage]: the urgent Backup message must also bypass the hysteresis that otherwise
     * protects against flicker between optional/dismissible Prioritized messages. Without the special case,
     * a previously-shown [RuntimeMessage] (priority `Int.MAX_VALUE`) cached as `lastShownMessage` would make
     * Backup's finite priority (5) look lower and get filtered out.
     */
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
