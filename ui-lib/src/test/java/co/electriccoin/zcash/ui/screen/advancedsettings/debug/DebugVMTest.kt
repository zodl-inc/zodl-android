package co.electriccoin.zcash.ui.screen.advancedsettings.debug

import co.electriccoin.zcash.ui.common.migration.MigrationGate
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MOB-1397 review item 8: "Simulate SeedNotRelevant" irreversibly overwrites the real stored seed
 * with random entropy and kills the process — it must be gated behind an explicit confirmation
 * rather than firing on a single tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebugVMTest {
    private val simulateSeedNotRelevant = mockk<SimulateSeedNotRelevantUseCase>(relaxed = true)
    private val migrationGate = mockk<MigrationGate>(relaxed = true)

    private fun vm() =
        DebugVM(
            copyToClipboardUseCase = mockk(relaxed = true),
            ephemeralAddressRepository = mockk(relaxed = true),
            accountDataSource = mockk(relaxed = true),
            migrationDebugActions = mockk(relaxed = true),
            migrationAppHooks = mockk(relaxed = true),
            migrationGate = migrationGate,
            context = mockk(relaxed = true),
            navigationRouter = mockk(relaxed = true),
            simulateSeedNotRelevant = simulateSeedNotRelevant,
        )

    private fun DebugVM.simulateSeedNotRelevantItem() =
        state.value.items.single {
            (it.title as? StringResource.ByString)?.value == "Simulate SeedNotRelevant"
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clickingSimulateSeedNotRelevantShowsConfirmationWithoutRunningItYet() =
        runTest {
            val vm = vm()
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()
            assertNull(vm.state.value.confirmationDialog)

            vm.simulateSeedNotRelevantItem().onClick?.invoke()
            advanceUntilIdle()

            assertNotNull(vm.state.value.confirmationDialog)
            coVerify(exactly = 0) { simulateSeedNotRelevant.invoke() }
            collectJob.cancel()
        }

    @Test
    fun confirmingRunsTheSimulationAndDismissesTheDialog() =
        runTest {
            val vm = vm()
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()
            vm.simulateSeedNotRelevantItem().onClick?.invoke()
            advanceUntilIdle()
            val dialog = assertNotNull(vm.state.value.confirmationDialog)

            dialog.primaryAction.onClick()
            advanceUntilIdle()

            assertNull(vm.state.value.confirmationDialog)
            coVerify(exactly = 1) { simulateSeedNotRelevant.invoke() }
            collectJob.cancel()
        }

    @Test
    fun dismissingTheConfirmationDoesNotRunTheSimulation() =
        runTest {
            val vm = vm()
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()
            vm.simulateSeedNotRelevantItem().onClick?.invoke()
            advanceUntilIdle()
            val dialog = assertNotNull(vm.state.value.confirmationDialog)

            dialog.onBack()
            advanceUntilIdle()

            assertNull(vm.state.value.confirmationDialog)
            coVerify(exactly = 0) { simulateSeedNotRelevant.invoke() }
            collectJob.cancel()
        }

    @Test
    fun cancelSecondaryActionDoesNotRunTheSimulation() =
        runTest {
            val vm = vm()
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()
            vm.simulateSeedNotRelevantItem().onClick?.invoke()
            advanceUntilIdle()
            val dialog = assertNotNull(vm.state.value.confirmationDialog)
            val secondaryAction = assertNotNull(dialog.secondaryAction)

            secondaryAction.onClick()
            advanceUntilIdle()

            assertNull(vm.state.value.confirmationDialog)
            coVerify(exactly = 0) { simulateSeedNotRelevant.invoke() }
            collectJob.cancel()
        }

    private fun DebugVM.gateBypassItem() =
        state.value.items.single {
            (it.title as? StringResource.ByString)?.value?.startsWith("Migration: ignore the NU7 pause") == true
        }

    @Test
    fun theGateBypassItemShowsTheStoredValueAndTogglesIt() =
        runTest {
            var stored = true
            coEvery { migrationGate.isNetworkUpgradeGateBypassEnabled() } answers { stored }
            coEvery { migrationGate.setNetworkUpgradeGateBypass(any()) } answers { stored = firstArg() }
            val vm = vm()
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()
            assertTrue((vm.gateBypassItem().title as StringResource.ByString).value.endsWith("ON"))

            vm.gateBypassItem().onClick?.invoke()
            advanceUntilIdle()

            coVerify(exactly = 1) { migrationGate.setNetworkUpgradeGateBypass(false) }
            assertTrue((vm.gateBypassItem().title as StringResource.ByString).value.endsWith("OFF"))
            collectJob.cancel()
        }
}
