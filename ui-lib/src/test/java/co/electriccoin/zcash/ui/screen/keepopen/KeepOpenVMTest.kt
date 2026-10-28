package co.electriccoin.zcash.ui.screen.keepopen

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.ledger.LedgerNavigator
import co.electriccoin.zcash.ui.common.provider.KeepScreenOnSyncSessionProvider
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavigator
import co.electriccoin.zcash.ui.design.component.ZashiDisclaimerState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeystone.connected.KeystoneConnectedArgs
import io.mockk.coVerify
import io.mockk.every
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
 * Ledger gets the vendor-neutral wording, and each vendor continues to its own success screen. A
 * view-only import shows the view-only feature's copy and continues to its success screen.
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
        val ledgerNavigator = mockk<LedgerNavigator>(relaxed = true)

        vm(KeepOpenFlow.KEYSTONE, keystoneRouter)
            .state.value.button
            .onClick()
        vm(KeepOpenFlow.LEDGER, ledgerNavigator = ledgerNavigator)
            .state.value.button
            .onClick()

        verify(exactly = 1) { keystoneRouter.forward(KeystoneConnectedArgs) }
        verify(exactly = 1) { ledgerNavigator.forwardToConnected() }
    }

    @Test
    fun aViewOnlyImportShowsTheViewOnlyCopyAndContinuesToItsSuccessScreen() {
        val viewOnlyNavigator = mockk<ViewOnlyNavigator>(relaxed = true)
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)
        val state = vm(KeepOpenFlow.VIEW_ONLY, navigationRouter, viewOnlyNavigator = viewOnlyNavigator).state.value

        assertEquals(VIEW_ONLY_DESCRIPTION, state.description)
        assertEquals(VIEW_ONLY_SUBTITLE, state.subtitle)
        assertEquals(ZashiDisclaimerState.warning(VIEW_ONLY_DISCLAIMER), state.disclaimer)
        assertEquals(VIEW_ONLY_CHECKBOX, state.checkboxLabel)

        state.button.onClick()

        verify(exactly = 1) { viewOnlyNavigator.forwardToImported() }
        verify(exactly = 0) { navigationRouter.backToRoot() }
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
        ledgerNavigator: LedgerNavigator = mockk(relaxed = true),
        viewOnlyNavigator: ViewOnlyNavigator = mockk(relaxed = true),
    ) = KeepOpenVM(
        application = mockk(relaxed = true),
        flow = flow,
        isKeepScreenOnDuringRestoreProvider = mockk(relaxed = true),
        keepScreenOnSyncSessionProvider = session,
        navigationRouter = navigationRouter,
        ledgerNavigator = ledgerNavigator,
        viewOnlyNavigator = viewOnlyNavigator,
        viewOnlyContent =
            mockk<ViewOnlyContent> {
                every { keepOpenDescription } returns VIEW_ONLY_DESCRIPTION
                every { keepOpenSubtitle } returns VIEW_ONLY_SUBTITLE
                every { keepOpenDisclaimer } returns VIEW_ONLY_DISCLAIMER
                every { keepOpenCheckboxLabel } returns VIEW_ONLY_CHECKBOX
            },
    )
}

/**
 * Resource-backed texts: mockk cannot return the inline `StringResource.ByString` from a mocked
 * interface property.
 */
private val VIEW_ONLY_DESCRIPTION = StringResource.ByResource(resource = 1, args = emptyList())

private val VIEW_ONLY_SUBTITLE = StringResource.ByResource(resource = 2, args = emptyList())

private val VIEW_ONLY_DISCLAIMER = StringResource.ByResource(resource = 3, args = emptyList())

private val VIEW_ONLY_CHECKBOX = StringResource.ByResource(resource = 4, args = emptyList())
