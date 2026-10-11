package co.electriccoin.zcash.ui.screen.texunsupported

import co.electriccoin.zcash.ui.NavigationRouter
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The TEX-unsupported sheet must know whether it was opened for a Ledger or a Keystone account,
 * since [TEXUnsupportedView] picks the warning copy off that flag.
 */
class TEXUnsupportedVMTest {
    @Test
    fun aLedgerArgIsReflectedInTheStateFlag() {
        val vm = vm(TEXUnsupportedArgs(isLedger = true))

        assertTrue(assertNotNull(vm.state.value).isLedger)
    }

    @Test
    fun theDefaultArgsAreNotLedgerWhichMatchesTheKeystoneCopy() {
        val vm = vm(TEXUnsupportedArgs())

        assertFalse(assertNotNull(vm.state.value).isLedger)
    }

    private fun vm(args: TEXUnsupportedArgs) =
        TEXUnsupportedVM(
            args = args,
            navigationRouter = mockk<NavigationRouter>(relaxed = true),
        )
}
