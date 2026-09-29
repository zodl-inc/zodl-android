package co.electriccoin.zcash.ui.screen.connectledger.openapp

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeArgs
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/**
 * The open-the-app screen only moves on to the handshake or back to the device picker.
 */
class LedgerOpenAppVMTest {
    @Test
    fun continueGoesOnToTheHandshake() {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        LedgerOpenAppVM(navigationRouter)
            .state.value
            .onContinueClick()

        verify(exactly = 1) { navigationRouter.forward(LedgerHandshakeArgs) }
    }

    @Test
    fun backReturnsToTheDevicePicker() {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        LedgerOpenAppVM(navigationRouter)
            .state.value
            .onBackClick()

        verify(exactly = 1) { navigationRouter.back() }
    }
}
