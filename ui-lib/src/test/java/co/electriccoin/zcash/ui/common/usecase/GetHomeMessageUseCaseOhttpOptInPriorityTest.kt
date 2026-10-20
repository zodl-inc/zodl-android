package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The private connection opt-in has no close button, so it ranks below every opt-in message the user can dismiss:
 * those must not stay hidden behind it for good.
 */
class GetHomeMessageUseCaseOhttpOptInPriorityTest {
    private fun message(
        isTorVisible: Boolean = false,
        isCurrencyConversionEnabled: Boolean = false,
        isCoinholderPollingVisible: Boolean = false,
        isCrashReportingVisible: Boolean = false,
        isOhttpOptInVisible: Boolean = true,
    ) = createHomeMessage(
        runtimeMessage = null,
        backup = WalletBackupData.Unavailable,
        isTorVisible = isTorVisible,
        isCurrencyConversionEnabled = isCurrencyConversionEnabled,
        isCoinholderPollingVisible = isCoinholderPollingVisible,
        isCrashReportingVisible = isCrashReportingVisible,
        isOhttpOptInVisible = isOhttpOptInVisible,
    )

    @Test
    fun `shown when it is the only message`() {
        assertEquals(HomeMessageData.OhttpOptIn, message())
    }

    @Test
    fun `dismissible opt-ins go first`() {
        assertEquals(HomeMessageData.EnableTor, message(isTorVisible = true))
        assertEquals(HomeMessageData.EnableCurrencyConversion, message(isCurrencyConversionEnabled = true))
        assertEquals(HomeMessageData.CrashReport, message(isCrashReportingVisible = true))
        assertEquals(HomeMessageData.CoinholderPolling, message(isCoinholderPollingVisible = true))
    }

    @Test
    fun `nothing is shown once the user opted out`() {
        assertNull(message(isOhttpOptInVisible = false))
    }

    @Test
    fun `ranks below every other prioritized message`() {
        val others =
            listOf(
                HomeMessageData.CrashReport,
                HomeMessageData.EnableCurrencyConversion,
                HomeMessageData.EnableTor,
                HomeMessageData.CoinholderPolling,
                HomeMessageData.Backup,
            )
        assertTrue(others.all { it.priority > HomeMessageData.OhttpOptIn.priority })
        assertTrue(HomeMessageData.OhttpOptIn.priority > 0)
    }

    @Test
    fun `is not held back by the hysteresis when nothing else was shown`() {
        assertEquals(
            HomeMessageData.OhttpOptIn,
            prioritizeHomeMessage(
                message = HomeMessageData.OhttpOptIn,
                lastMessage = null,
                lastShownMessage = null,
            )
        )
    }
}
