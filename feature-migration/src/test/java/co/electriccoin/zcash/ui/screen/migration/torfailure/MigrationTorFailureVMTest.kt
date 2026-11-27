package co.electriccoin.zcash.ui.screen.migration.torfailure

import cash.z.ecc.android.sdk.fixture.AccountFixture
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.repository.PendingMigrationTorFailureDecisionRepository
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.screen.home.HomeArgs
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationTorFailureVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Try again must NOT retry Tor in place (that would infinite-loop on a persistent outage) —
    // it dismisses the sheet and sends the user to the standard overdue/missed-transfer
    // resolution screen, without recording any decision on the in-memory retry repository.
    @Test
    fun tryAgainNavigatesToProgressScreenWithoutRecordingADecision() =
        runTest {
            val router = mockk<NavigationRouter>(relaxed = true)
            val decisionRepository = mockk<PendingMigrationTorFailureDecisionRepository>(relaxed = true)
            val vm = vm(router = router, decisionRepository = decisionRepository)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.state.value
                ?.onTryAgain
                ?.invoke()
            advanceUntilIdle()

            verify(exactly = 1) { router.replaceAll(HomeArgs, MigrationProgressArgs) }
            verify(exactly = 0) { decisionRepository.set(any(), any()) }
            collectJob.cancel()
        }

    @Test
    fun continueWithoutTorPersistsMigrationScopedSettingAndRecordsDecision() =
        runTest {
            val router = mockk<NavigationRouter>(relaxed = true)
            val decisionRepository = mockk<PendingMigrationTorFailureDecisionRepository>(relaxed = true)
            val torProvider = mockk<IsMigrationTorEnabledStorageProvider>(relaxed = true)
            val vm = vm(router = router, decisionRepository = decisionRepository, torProvider = torProvider)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.state.value
                ?.onContinueWithoutTor
                ?.invoke()
            advanceUntilIdle()

            coVerify(exactly = 1) { torProvider.store(false) }
            verify(exactly = 1) { decisionRepository.set(any(), useTor = false) }
            verify(exactly = 1) { router.back() }
            collectJob.cancel()
        }

    private fun vm(
        router: NavigationRouter,
        decisionRepository: PendingMigrationTorFailureDecisionRepository,
        torProvider: IsMigrationTorEnabledStorageProvider = mockk(relaxed = true),
    ): MigrationTorFailureVM {
        val fakeAccount =
            AccountFixture.new(
                accountUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
            )
        val walletAccount: WalletAccount =
            mockk(relaxed = true) {
                every { sdkAccount } returns fakeAccount
            }
        val getSelectedWalletAccount =
            mockk<GetSelectedWalletAccountUseCase> {
                coEvery { this@mockk() } returns walletAccount
            }
        return MigrationTorFailureVM(
            navigationRouter = router,
            getSelectedWalletAccount = getSelectedWalletAccount,
            pendingMigrationTorFailureDecisionRepository = decisionRepository,
            isMigrationTorEnabledStorageProvider = torProvider,
        )
    }
}
