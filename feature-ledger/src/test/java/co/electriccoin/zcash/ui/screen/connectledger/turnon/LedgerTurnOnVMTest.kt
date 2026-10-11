package co.electriccoin.zcash.ui.screen.connectledger.turnon

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanArgs
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/**
 * The turn-on step only moves on to the device picker or back to the intro.
 */
class LedgerTurnOnVMTest {
    @Test
    fun continueGoesOnToTheDevicePicker() {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        LedgerTurnOnVM(navigationRouter)
            .state.value
            .onContinueClick()

        verify(exactly = 1) { navigationRouter.forward(LedgerDeviceScanArgs) }
    }

    @Test
    fun backReturnsToTheIntro() {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        LedgerTurnOnVM(navigationRouter)
            .state.value
            .onBack()

        verify(exactly = 1) { navigationRouter.back() }
    }
}
