package co.electriccoin.zcash.ui.screen.chooseserver

import android.app.Application
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.model.ServerConnectionMode
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveServerConnectionModeUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshFastestServersUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.chooseserver.ohttpinfo.OhttpInfoArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChooseServerVMTest {
    private val dispatcher = StandardTestDispatcher()

    private val gateway = LightWalletEndpoint(host = "ohttp-gateway.example", port = 443, isSecure = true)
    private val clearnet = LightWalletEndpoint(host = "zec.rocks", port = 443, isSecure = true)
    private val otherClearnet = LightWalletEndpoint(host = "na.zec.rocks", port = 443, isSecure = true)

    private val mode = MutableStateFlow(ServerConnectionMode.PRIVATE)
    private val selectedEndpoint = MutableStateFlow<LightWalletEndpoint?>(gateway)
    private val fastest = MutableStateFlow(FastestServersState(servers = null, isLoading = false))
    private val ohttpUnavailable = MutableStateFlow(false)
    private val fallbackActive = MutableStateFlow(false)
    private var relayIndependent = true

    private val persist = mockk<PersistServerSelectionUseCase>(relaxed = true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(): ChooseServerVM {
        val endpointProvider =
            mockk<LightWalletEndpointProvider> {
                every { getEndpoints() } returns listOf(gateway, clearnet, otherClearnet)
                every { getDefaultEndpoint() } returns gateway
                every { getOhttpEndpoint() } returns gateway
                every { getFallbackEndpoints() } returns listOf(clearnet, otherClearnet)
                every { isOhttpGateway(any()) } answers { firstArg<LightWalletEndpoint>() == gateway }
                coEvery { usesIndependentRelay(any()) } answers { relayIndependent }
            }
        return ChooseServerVM(
            application = mockk<Application>(relaxed = true),
            observeFastestServers =
                mockk<ObserveFastestServersUseCase> { every { this@mockk.invoke() } returns fastest },
            isOhttpFallbackActive = mockk<IsOhttpFallbackActiveProvider> { every { observe() } returns fallbackActive },
            getSelectedEndpoint = mockk<GetSelectedEndpointUseCase> { every { observe() } returns selectedEndpoint },
            observeServerConnectionMode =
                mockk<ObserveServerConnectionModeUseCase> { every { this@mockk.invoke() } returns mode },
            automaticServerRepository =
                mockk<AutomaticServerRepository> {
                    every { ohttpUnavailable } returns this@ChooseServerVMTest.ohttpUnavailable
                },
            lightWalletEndpointProvider = endpointProvider,
            refreshFastestServersUseCase = mockk<RefreshFastestServersUseCase>(relaxed = true),
            persistServerSelection = persist,
            navigationRouter = navigationRouter,
        )
    }

    @Test
    fun privateIsCheckedAndActiveWhileTheGatewayAnswers() =
        runTest(dispatcher) {
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            val state = vm.state.value!!
            assertTrue(state.connectionMode.private.isChecked)
            assertEquals(ModeBadgeStyle.SUCCESS, state.connectionMode.privateBadge?.style)
            assertFalse(state.saveButton.isEnabled)
            job.cancel()
        }

    @Test
    fun privateShowsUnavailableWhenTheGatewayIsDown() =
        runTest(dispatcher) {
            ohttpUnavailable.value = true
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            assertEquals(
                ModeBadgeStyle.ERROR,
                vm.state.value!!
                    .connectionMode.privateBadge
                    ?.style
            )
            job.cancel()
        }

    @Test
    fun privateIsNotPrivateNotActiveThroughARelayTheGatewayOperatorRuns() =
        runTest(dispatcher) {
            relayIndependent = false
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            val badge =
                vm.state.value!!
                    .connectionMode.privateBadge
            assertEquals(ModeBadgeStyle.WARNING, badge?.style)
            assertEquals(stringRes(R.string.serverSetup_notPrivate), badge?.text)
            job.cancel()
        }

    @Test
    fun theGatewayIsNotOfferedInTheManualLists() =
        runTest(dispatcher) {
            fastest.value = FastestServersState(servers = listOf(gateway, clearnet), isLoading = false)
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            val state = vm.state.value!!
            val keys =
                (state.fastest.servers + state.other.servers.filterIsInstance<ServerState.Default>()).map { it.key }
            assertTrue(keys.none { it.toString().contains("ohttp-gateway") })
            assertTrue(keys.any { it.toString().contains("zec.rocks") })
            job.cancel()
        }

    @Test
    fun choosingPublicEnablesSaveAndSavesPublic() =
        runTest(dispatcher) {
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            vm.state.value!!
                .connectionMode.public
                .onClick()
            advanceUntilIdle()
            val state = vm.state.value!!
            assertTrue(state.connectionMode.public.isChecked)
            assertTrue(state.saveButton.isEnabled)
            state.saveButton.onClick()
            advanceUntilIdle()
            coVerify(exactly = 1) { persist.persistPublic(afterPrivateUnavailable = false, fastest = null) }
            job.cancel()
        }

    @Test
    fun savingPublicWaitsForTheServerTest() =
        runTest(dispatcher) {
            fastest.value = FastestServersState(servers = null, isLoading = true)
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            vm.state.value!!
                .connectionMode.public
                .onClick()
            advanceUntilIdle()
            val state = vm.state.value!!
            assertEquals(ModeBadgeStyle.WARNING, state.connectionMode.publicBadge?.style)
            assertFalse(state.saveButton.isEnabled)
            job.cancel()
        }

    @Test
    fun switchingBackToPrivateFromPublicSavesPrivate() =
        runTest(dispatcher) {
            mode.value = ServerConnectionMode.PUBLIC
            selectedEndpoint.value = clearnet
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            assertNull(
                vm.state.value!!
                    .connectionMode.privateBadge
            )
            vm.state.value!!
                .connectionMode.private
                .onClick()
            advanceUntilIdle()
            vm.state.value!!
                .saveButton
                .onClick()
            advanceUntilIdle()
            coVerify(exactly = 1) { persist.persistPrivate() }
            job.cancel()
        }

    @Test
    fun manualWithoutAChangedServerCannotBeSaved() =
        runTest(dispatcher) {
            mode.value = ServerConnectionMode.MANUAL
            selectedEndpoint.value = clearnet
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            val state = vm.state.value!!
            assertTrue(state.connectionMode.isManualSelected)
            assertFalse(state.saveButton.isEnabled)
            job.cancel()
        }

    @Test
    fun privateInfoIconOpensTheInfoSheet() =
        runTest(dispatcher) {
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            vm.state.value!!
                .connectionMode
                .onPrivateInfoClick()
            verify { navigationRouter.forward(OhttpInfoArgs) }
            job.cancel()
        }

    @Test
    fun savingPublicStartsFromTheFastestPublicServerOfTheTest() =
        runTest(dispatcher) {
            fastest.value = FastestServersState(servers = listOf(gateway, otherClearnet, clearnet), isLoading = false)
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            vm.state.value!!
                .connectionMode.public
                .onClick()
            advanceUntilIdle()
            vm.state.value!!
                .saveButton
                .onClick()
            advanceUntilIdle()
            coVerify(exactly = 1) { persist.persistPublic(afterPrivateUnavailable = false, fastest = otherClearnet) }
            job.cancel()
        }

    /**
     * Switching to Manual mode from Private requires selecting a server before save is enabled.
     * No server is preselected since the gateway is Private-only.
     */
    @Test
    fun manualFromPrivateNeedsAServerBeforeItCanBeSaved() =
        runTest(dispatcher) {
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            vm.state.value!!
                .connectionMode.manual
                .onClick()
            advanceUntilIdle()
            val state = vm.state.value!!
            assertTrue(state.connectionMode.isManualSelected)
            assertFalse(state.saveButton.isEnabled)
            assertTrue(
                state.other.servers
                    .filterIsInstance<ServerState.Default>()
                    .none { it.radioButtonState.isChecked }
            )
            job.cancel()
        }

    /**
     * An outage-induced Public mode can be confirmed as a deliberate choice. The endpoint
     * stays the same even if the speed test ranked a different server first.
     */
    @Test
    fun anOutageInducedPublicCanBeConfirmedAsDeliberate() =
        runTest(dispatcher) {
            mode.value = ServerConnectionMode.PUBLIC
            selectedEndpoint.value = clearnet
            fallbackActive.value = true
            fastest.value = FastestServersState(servers = listOf(otherClearnet, clearnet), isLoading = false)
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            val state = vm.state.value!!
            assertTrue(state.saveButton.isEnabled)
            state.saveButton.onClick()
            advanceUntilIdle()
            coVerify(exactly = 1) { persist.persistPublic(afterPrivateUnavailable = false, fastest = clearnet) }
            job.cancel()
        }

    @Test
    fun aDeliberatePublicHasNothingToSave() =
        runTest(dispatcher) {
            mode.value = ServerConnectionMode.PUBLIC
            selectedEndpoint.value = clearnet
            val vm = createVm()
            val job = launchCollector(vm)
            advanceUntilIdle()
            assertFalse(
                vm.state.value!!
                    .saveButton.isEnabled
            )
            job.cancel()
        }

    private fun kotlinx.coroutines.test.TestScope.launchCollector(vm: ChooseServerVM) =
        backgroundScope.launch(dispatcher) { vm.state.collect { } }
}
