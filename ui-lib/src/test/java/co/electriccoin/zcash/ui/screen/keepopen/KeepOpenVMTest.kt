package co.electriccoin.zcash.ui.screen.keepopen

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.KeepScreenOnSyncSessionProvider
import co.electriccoin.zcash.ui.design.component.ZashiDisclaimerState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeystone.connected.KeystoneConnectedArgs
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedArgs
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Both hardware vendors share the keep-open screen, but only Keystone's warning names Keystone;
 * Ledger gets the vendor-neutral wording, and each vendor continues to its own success screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepOpenVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theKeystoneWarningIsUnchanged() {
        val state = vm(KeepOpenFlow.KEYSTONE).state.value

        assertEquals(
            ZashiDisclaimerState.warning(stringRes(R.string.keep_open_keystone_warning)),
            state.disclaimer
        )
    }

    @Test
    fun theLedgerWarningIsVendorNeutral() {
        val state = vm(KeepOpenFlow.LEDGER).state.value

        assertEquals(
            ZashiDisclaimerState.warning(stringRes(R.string.keep_open_hw_wallet_warning)),
            state.disclaimer
        )
    }

    @Test
    fun eachVendorContinuesToItsOwnSuccessScreen() {
        val keystoneRouter = mockk<NavigationRouter>(relaxed = true)
        val ledgerRouter = mockk<NavigationRouter>(relaxed = true)

        vm(KeepOpenFlow.KEYSTONE, keystoneRouter)
            .state.value.button
            .onClick()
        vm(KeepOpenFlow.LEDGER, ledgerRouter)
            .state.value.button
            .onClick()

        verify(exactly = 1) { keystoneRouter.forward(KeystoneConnectedArgs) }
        verify(exactly = 1) { ledgerRouter.forward(LedgerConnectedArgs) }
    }

    @Test
    fun theRestoreAndResyncFlowsStillUnwindToTheRoot() {
        listOf(KeepOpenFlow.RESTORE, KeepOpenFlow.RESYNC).forEach { flow ->
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)

            vm(flow, navigationRouter)
                .state.value.button
                .onClick()

            verify(exactly = 1) { navigationRouter.backToRoot() }
        }
    }

    @Test
    fun confirmingTheLedgerScreenStartsAKeepScreenOnSession() {
        val session = mockk<KeepScreenOnSyncSessionProvider>(relaxed = true)

        vm(KeepOpenFlow.LEDGER, session = session)
            .state.value.button
            .onClick()

        coVerify(exactly = 1) { session.store(true) }
    }

    @Test
    fun anUncheckedBoxDoesNotStartASession() {
        val session = mockk<KeepScreenOnSyncSessionProvider>(relaxed = true)
        val vm = vm(KeepOpenFlow.LEDGER, session = session)

        vm.state.value.onCheckedChange(false)
        vm.state.value.button
            .onClick()

        coVerify(exactly = 1) { session.store(false) }
    }

    private fun vm(
        flow: KeepOpenFlow,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        session: KeepScreenOnSyncSessionProvider = mockk(relaxed = true),
    ) = KeepOpenVM(
        application = mockk(relaxed = true),
        flow = flow,
        isKeepScreenOnDuringRestoreProvider = mockk(relaxed = true),
        keepScreenOnSyncSessionProvider = session,
        navigationRouter = navigationRouter,
    )
}
