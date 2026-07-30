package co.electriccoin.zcash.ui.screen.error

import cash.z.ecc.android.sdk.exception.CompactBlockProcessorException
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.design.component.listitem.SimpleListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.chooseserver.ChooseServerArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The Sync Error sheet's diagnostics: when sync stops because ZODL and the server are incompatible,
 * the sheet explains why and names the server and both sides of the disagreement. Generic and
 * transient sync errors, where retrying is the remedy, keep the existing copy and gain no detail.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncErrorVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun genericSyncErrorShowsNoDiagnostics() =
        runTest {
            val vm = vm(cause = IOException("connection reset"))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            assertNull(vm.state.value.diagnostics)
        }

    @Test
    fun consensusBranchMismatchExplainsItselfWithoutBlamingEitherSide() =
        runTest {
            val vm = vm(cause = consensusMismatch())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            val diagnostics = assertNotNull(vm.state.value.diagnostics)
            assertEquals(stringRes(R.string.sync_error_incompatible_consensus_message), diagnostics.explanation)
        }

    @Test
    fun consensusBranchMismatchNamesTheServerAndBothBranchIds() =
        runTest {
            val vm = vm(cause = consensusMismatch())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            assertEquals(
                listOf(
                    fact(R.string.sync_error_detail_server, "zec.rocks:443"),
                    fact(R.string.sync_error_detail_expected_branch, "0x$CLIENT_BRANCH_ID"),
                    fact(R.string.sync_error_detail_server_branch, "0x$SERVER_BRANCH_ID"),
                    fact(R.string.sync_error_detail_error_type, "MismatchedConsensusBranch")
                ),
                assertNotNull(vm.state.value.diagnostics).facts
            )
        }

    @Test
    fun serverLineIsOmittedWhenNoEndpointIsSelected() =
        runTest {
            val vm = vm(cause = consensusMismatch(), endpoint = null)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            val facts = assertNotNull(vm.state.value.diagnostics).facts
            assertEquals(
                listOf(
                    fact(R.string.sync_error_detail_expected_branch, "0x$CLIENT_BRANCH_ID"),
                    fact(R.string.sync_error_detail_server_branch, "0x$SERVER_BRANCH_ID"),
                    fact(R.string.sync_error_detail_error_type, "MismatchedConsensusBranch")
                ),
                facts
            )
        }

    @Test
    fun networkMismatchOmitsASideThatTheSdkCouldNotName() =
        runTest {
            val vm =
                vm(
                    cause =
                        CompactBlockProcessorException.MismatchedNetwork(
                            clientNetwork = "mainnet",
                            serverNetwork = null
                        )
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            assertEquals(
                listOf(
                    fact(R.string.sync_error_detail_server, "zec.rocks:443"),
                    fact(R.string.sync_error_detail_expected_network, "mainnet"),
                    fact(R.string.sync_error_detail_error_type, "MismatchedNetwork")
                ),
                assertNotNull(vm.state.value.diagnostics).facts
            )
        }

    @Test
    fun switchServerRoutesToTheServerPickerAndNoButtonIsAdded() =
        runTest {
            val router = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(cause = consensusMismatch(), router = router)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            val state = vm.state.value
            // The remedy already existed; this change must not introduce another button.
            assertEquals(stringRes(R.string.sync_error_switch_server), state.switchServer.text)
            assertEquals(stringRes(R.string.sync_error_contact_support), state.support.text)
            assertNull(state.disableTor)

            state.switchServer.onClick()
            verify(exactly = 1) { router.forward(ChooseServerArgs) }
        }

    private fun vm(
        cause: Throwable?,
        endpoint: LightWalletEndpoint? = LightWalletEndpoint("zec.rocks", 443, true),
        router: NavigationRouter = mockk(relaxed = true)
    ): SyncErrorVM {
        val isTorEnabled = mockk<IsTorEnabledUseCase>()
        every { isTorEnabled.observe() } returns flowOf(false)
        val getSelectedEndpoint = mockk<GetSelectedEndpointUseCase>()
        every { getSelectedEndpoint.observe() } returns flowOf(endpoint)

        return SyncErrorVM(
            args = ErrorArgs.SyncError(SynchronizerError.Processor(cause)),
            navigationRouter = router,
            sendEmailUseCase = mockk<SendEmailUseCase>(relaxed = true),
            synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true),
            getSelectedEndpointUseCase = getSelectedEndpoint,
            isTorEnabledUseCase = isTorEnabled
        )
    }
}

private fun consensusMismatch() =
    CompactBlockProcessorException.MismatchedConsensusBranch(
        clientBranchId = CLIENT_BRANCH_ID,
        serverBranchId = SERVER_BRANCH_ID
    )

private fun fact(
    titleRes: Int,
    value: String
) = SimpleListItemState(title = stringRes(titleRes), text = stringRes(value))

// NU6.2, which the SDK expects, and NU6.3/Ironwood, observed on a server ahead of the app.
private const val CLIENT_BRANCH_ID = "5437f330"
private const val SERVER_BRANCH_ID = "37a5165b"
