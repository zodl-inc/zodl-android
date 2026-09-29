package co.electriccoin.zcash.ui.screen.connectledger.connect

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedVM
import co.electriccoin.zcash.ui.screen.connectledger.turnon.LedgerTurnOnArgs
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/**
 * The two ends of the Ledger flow: the intro screen leads into the turn-on step, and the success
 * screen only unwinds to the wallet root.
 */
class LedgerConnectVMTest {
    @Test
    fun theIntroScreenContinuesToTheTurnOnStep() {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        LedgerConnectVM(navigationRouter)
            .state.value
            .onContinueClick()

        verify(exactly = 1) { navigationRouter.forward(LedgerTurnOnArgs) }
    }

    @Test
    fun theIntroScreenCloseGoesBack() {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        LedgerConnectVM(navigationRouter)
            .state.value
            .onBackClick()

        verify(exactly = 1) { navigationRouter.back() }
    }

    @Test
    fun theSuccessScreenClosesToTheWalletRoot() {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        LedgerConnectedVM(navigationRouter)
            .state.value
            .onClose()

        verify(exactly = 1) { navigationRouter.backToRoot() }
    }
}
