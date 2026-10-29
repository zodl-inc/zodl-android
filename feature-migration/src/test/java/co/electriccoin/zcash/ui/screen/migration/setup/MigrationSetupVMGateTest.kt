package co.electriccoin.zcash.ui.screen.migration.setup

import cash.z.ecc.android.sdk.MigrationAvailability
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.migration.MigrationGateInfo
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationGateInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The setup screen waits for the SDK's gate, and follows it while the wallet has no chain tip yet. */
@OptIn(ExperimentalCoroutinesApi::class)
class MigrationSetupVMGateTest {
    private val scheduler = TestCoroutineScheduler()
    private val spendable =
        mockk<WalletAccount>(relaxed = true) {
            every { isSpendable } returns true
        }
    private val open = MigrationGateInfo.OPEN
    private val unsynced =
        MigrationGateInfo(
            MigrationAvailability.PausedUntilNetworkUpgrade(2_000L),
            estimatedActivation = null,
            isWalletTipUnknown = true,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun MigrationSetupVM.currentGate() = state.value.content?.gate

    private fun vm(gateInfo: suspend () -> MigrationGateInfo) =
        MigrationSetupVM(
            getSelectedWalletAccount =
                mockk<GetSelectedWalletAccountUseCase> {
                    coEvery { this@mockk() } returns spendable
                    every { observe() } returns MutableStateFlow(spendable)
                },
            getOrchardBalance =
                mockk<GetOrchardBalanceUseCase> {
                    coEvery { this@mockk() } returns Zatoshi(100_000_000L)
                },
            exchangeRateRepository =
                mockk<ExchangeRateRepository>(relaxed = true) {
                    every { state } returns MutableStateFlow(ExchangeRateState.OptIn)
                },
            navigationRouter = mockk(relaxed = true),
            errorStateMapper =
                ErrorMapperUseCase(sendEmail = mockk(relaxed = true), viewOnlyContent = mockk(relaxed = true)),
            getMigrationPrivacyOrReviewDestination = mockk(relaxed = true),
            getMigrationGateInfo =
                mockk<GetMigrationGateInfoUseCase> {
                    coEvery { this@mockk() } coAnswers { gateInfo() }
                },
            migrationUpgradeNotice = mockk(relaxed = true),
        )

    @Test
    fun theScreenIsLoadingUntilTheGateHasAnswered() =
        runTest(UnconfinedTestDispatcher(scheduler)) {
            val answer = CompletableDeferred<MigrationGateInfo>()
            val vm = vm { answer.await() }
            backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { vm.state.collect { } }

            assertNull(vm.state.value.content)
            assertTrue(vm.state.value.isLoading)

            answer.complete(open)

            assertNotNull(vm.state.value.content)
            assertEquals(MigrationSetupGate.Open, vm.currentGate())
            assertEquals(false, vm.state.value.isLoading)
        }

    @Test
    fun anUnsyncedWalletIsAskedAgainUntilItHasAChainTip() =
        runTest(UnconfinedTestDispatcher(scheduler)) {
            val answers = ArrayDeque(listOf(unsynced, unsynced, open))
            val vm = vm { if (answers.size > 1) answers.removeFirst() else answers.first() }
            backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { vm.state.collect { } }

            assertIs<MigrationSetupGate.ScheduledPaused>(vm.currentGate())

            advanceTimeBy(7_000L)

            assertEquals(MigrationSetupGate.Open, vm.currentGate())
        }
}
