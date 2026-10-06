package co.electriccoin.zcash.ui.screen.migration.review

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.NoteSplitProposal
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferProposal
import cash.z.ecc.android.sdk.TransferResult
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.ZashiSpendingKeyDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.FinalizeMigrationScheduleUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitProposalUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationReviewVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun immediateConfirmOnZashiAccountHandsSweepProposalToSharedSendPipeline() =
        runTest {
            val proposal =
                mockk<Proposal> {
                    coEvery { totalFeeRequired() } returns Zatoshi(1_000L)
                }
            val router = FakeNavigationRouter()
            val zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true)
            val submitProposal = mockk<SubmitProposalUseCase>(relaxed = true)
            val vm =
                vm(
                    router = router,
                    zashiProposalRepository = zashiProposalRepository,
                    submitProposal = submitProposal,
                )

            invokeOnConfirmImmediate(vm, proposal)
            advanceUntilIdle()

            // IMMEDIATE confirm on a Zashi account no longer signs/broadcasts in this VM — it adopts the
            // send-max sweep proposal into the shared send pipeline (SubmitProposalUseCase owns biometrics,
            // broadcast, and the Transaction Progress navigation).
            coVerifyOrder {
                zashiProposalRepository.setMigrationSweepProposal(proposal, Zatoshi(500_000L))
                submitProposal()
            }
        }

    @Test
    fun immediateConfirmOnKeystoneAccountAdoptsIntoKeystoneRepositoryAndNavigatesToSign() =
        runTest {
            val proposal =
                mockk<Proposal> {
                    coEvery { totalFeeRequired() } returns Zatoshi(1_000L)
                }
            val router = FakeNavigationRouter()
            val keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true)
            val vm =
                vm(
                    router = router,
                    keystoneProposalRepository = keystoneProposalRepository,
                    getSelectedWalletAccount =
                        mockk {
                            coEvery { this@mockk() } returns mockk<KeystoneAccount>(relaxed = true)
                            every { observe() } returns flowOf(mockk<KeystoneAccount>(relaxed = true))
                        },
                )

            invokeOnConfirmImmediate(vm, proposal)
            advanceUntilIdle()

            coVerifyOrder {
                keystoneProposalRepository.setMigrationSweepProposal(proposal, Zatoshi(500_000L))
                keystoneProposalRepository.createPCZTFromProposal()
            }
            assertEquals(
                listOf<Any>(SignKeystoneTransactionArgs),
                router.forwardedRoutes,
            )
        }

    // Covers item 5 of the plan-update/expired-transfer fixes: restartCurrentMigrationStep()'s own
    // doc requires its returned schedule to go through this normal confirmation flow rather than
    // being discarded in favor of an independently re-proposed one.
    @Test
    fun automaticModeReusesPendingRestartScheduleInsteadOfProposingAFreshOne() =
        runTest {
            val router = FakeNavigationRouter()
            val restartSchedule =
                MigrationSchedule(
                    transfers =
                        listOf(
                            TransferProposal(
                                id = 100L,
                                amountZatoshi = 900_000L,
                                anchorHeight = 0L,
                                nextExecutableAfterHeight = 100L,
                                expiryHeight = 200L,
                            )
                        ),
                    estimatedDurationHours = 1,
                    proposalHandle = 0L,
                )
            val restartRepo =
                mockk<RestartMigrationScheduleRepository>(relaxed = true) {
                    every { consume(any()) } returns restartSchedule
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val vm =
                vm(
                    router = router,
                    getOrchardMigrationSdk = mockk { coEvery { this@mockk() } returns sdk },
                    mode = MigrationMode.AUTOMATIC,
                    restartMigrationScheduleRepository = restartRepo,
                )
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(
                1,
                vm.state.value.content
                    ?.transfers
                    ?.size
            )
            coVerify(exactly = 0) { sdk.proposeMigrationTransfers(any()) }
            coVerify(exactly = 1) { restartRepo.consume(any()) }
            collectJob.cancel()
        }

    @Test
    fun automaticModeProposesFreshScheduleWhenNoRestartIsPending() =
        runTest {
            val router = FakeNavigationRouter()
            val freshSchedule =
                MigrationSchedule(
                    transfers =
                        listOf(
                            TransferProposal(
                                id = 200L,
                                amountZatoshi = 100_000L,
                                anchorHeight = 0L,
                                nextExecutableAfterHeight = 100L,
                                expiryHeight = 200L,
                            ),
                            TransferProposal(
                                id = 201L,
                                amountZatoshi = 200_000L,
                                anchorHeight = 0L,
                                nextExecutableAfterHeight = 200L,
                                expiryHeight = 300L,
                            ),
                        ),
                    estimatedDurationHours = 2,
                    proposalHandle = 0L,
                )
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { proposeMigrationTransfers(any()) } returns freshSchedule
                }
            val vm =
                vm(
                    router = router,
                    getOrchardMigrationSdk = mockk { coEvery { this@mockk() } returns sdk },
                    mode = MigrationMode.AUTOMATIC,
                )
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(
                2,
                vm.state.value.content
                    ?.transfers
                    ?.size
            )
            coVerify(exactly = 1) { sdk.proposeMigrationTransfers(any()) }
            collectJob.cancel()
        }

    // Regression (root cause of the live "No pending migration proposal for this account — call
    // propose/prepare first" crash): on the Zashi in-process signing split path,
    // proposeMigrationTransfersFromSplit() MUST run before submitNoteSplit(). submitNoteSplit()
    // signs the split via the SDK's commit_or_reuse, which clears the in-memory migration-plan
    // cache the handle identifies — so deriving the from-split schedule AFTER submitting throws,
    // because the plan the handle points at is already gone. Mirrors MigrationKeystoneSignVM, which
    // likewise derives the from-split schedule before its first commit.
    @Test
    fun automaticConfirmWithNoteSplitDerivesScheduleFromSplitBeforeSubmittingIt() =
        runTest {
            val usk = mockk<UnifiedSpendingKey>()
            val router = FakeNavigationRouter()
            val splitProposal =
                NoteSplitProposal(
                    outputNotes = listOf(100_000L),
                    fee = 1_000L,
                    proposalHandle = 42L,
                )
            val scheduleFromSplit =
                MigrationSchedule(
                    transfers =
                        listOf(
                            TransferProposal(
                                id = 300L,
                                amountZatoshi = 100_000L,
                                anchorHeight = 0L,
                                nextExecutableAfterHeight = 100L,
                                expiryHeight = 200L,
                            )
                        ),
                    estimatedDurationHours = 1,
                    proposalHandle = 42L,
                )
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { getMigrationState() } returns MigrationState.NotStarted
                    coEvery { isNoteSplitNeeded() } returns true
                    coEvery { prepareNoteSplit() } returns splitProposal
                    coEvery { proposeMigrationTransfersFromSplit(splitProposal) } returns scheduleFromSplit
                    coEvery { submitNoteSplit(splitProposal, usk) } returns
                        TransferResult.Success(TransactionId.new("splittx".toByteArray()))
                }
            val finalizeMigrationSchedule = mockk<FinalizeMigrationScheduleUseCase>(relaxed = true)
            val vm =
                vm(
                    router = router,
                    getOrchardMigrationSdk = mockk { coEvery { this@mockk() } returns sdk },
                    zashiSpendingKeyDataSource = mockk { coEvery { getZashiSpendingKey() } returns usk },
                    finalizeMigrationSchedule = finalizeMigrationSchedule,
                    mode = MigrationMode.AUTOMATIC,
                )

            invokeOnConfirmAutomatic(vm, scheduleFromSplit)
            advanceUntilIdle()

            // Schedule is derived from the split before the split is submitted (the engine's own
            // committed state is the recovery signal — no app-side write-ahead exists anymore).
            coVerifyOrder {
                sdk.proposeMigrationTransfersFromSplit(splitProposal)
                sdk.submitNoteSplit(splitProposal, usk)
            }
        }

    private fun invokeOnConfirmAutomatic(vm: MigrationReviewVM, sched: MigrationSchedule) {
        val method =
            MigrationReviewVM::class.java.getDeclaredMethod("onConfirmAutomatic", MigrationSchedule::class.java)
        method.isAccessible = true
        method.invoke(vm, sched)
    }

    // MigrationReviewVM's IMMEDIATE-mode `onConfirm` callback only becomes reachable through
    // `state.value.content`, which is backed by a `combine(...).stateIn(WhileSubscribed)` chain
    // that (by design, same as every other LCE-driven VM in this codebase, e.g.
    // MigrationCompleteVM) only starts computing once actively collected — exercising it through a
    // real subscriber is exactly the kind of Flow-timing plumbing this test isn't meant to be
    // about. `MigrationCompleteVMTest.invokeOnDone` establishes the same
    // call-the-private-handler-via-reflection pattern for the identical reason.
    private fun invokeOnConfirmImmediate(
        vm: MigrationReviewVM,
        proposal: Proposal,
        amountZatoshi: Zatoshi = Zatoshi(500_000L)
    ) {
        val method =
            MigrationReviewVM::class.java.getDeclaredMethod(
                "onConfirmImmediate",
                Proposal::class.java,
                Zatoshi::class.java,
            )
        method.isAccessible = true
        method.invoke(vm, proposal, amountZatoshi)
    }

    private fun vm(
        router: NavigationRouter,
        getSelectedWalletAccount: GetSelectedWalletAccountUseCase =
            mockk {
                coEvery { this@mockk() } returns mockk<ZashiAccount>(relaxed = true)
                every { observe() } returns flowOf(mockk<ZashiAccount>(relaxed = true))
            },
        zashiSpendingKeyDataSource: ZashiSpendingKeyDataSource =
            mockk {
                coEvery { getZashiSpendingKey() } returns mockk<UnifiedSpendingKey>()
            },
        getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase = mockk<GetOrchardMigrationSdkUseCase>(relaxed = true),
        keystoneProposalRepository: KeystoneProposalRepository = mockk(relaxed = true),
        zashiProposalRepository: ZashiProposalRepository = mockk(relaxed = true),
        submitProposal: SubmitProposalUseCase = mockk(relaxed = true),
        finalizeMigrationSchedule: FinalizeMigrationScheduleUseCase = mockk(relaxed = true),
        mode: MigrationMode = MigrationMode.IMMEDIATE,
        restartMigrationScheduleRepository: RestartMigrationScheduleRepository =
            mockk<RestartMigrationScheduleRepository>(relaxed = true) { every { consume(any()) } returns null },
        synchronizerProvider: SynchronizerProvider = mockk(relaxed = true),
    ) = MigrationReviewVM(
        args = MigrationReviewArgs(mode = mode),
        getOrchardMigrationSdk = getOrchardMigrationSdk,
        pendingMigrationScheduleRepository = mockk<PendingMigrationScheduleRepository>(relaxed = true),
        restartMigrationScheduleRepository = restartMigrationScheduleRepository,
        finalizeMigrationSchedule = finalizeMigrationSchedule,
        navigationRouter = router,
        exchangeRateRepository =
            mockk<ExchangeRateRepository>(relaxed = true) {
                every { state } returns MutableStateFlow(ExchangeRateState.OptedOut)
            },
        getSelectedWalletAccount = getSelectedWalletAccount,
        getOrchardBalance = mockk<GetOrchardBalanceUseCase> { coEvery { this@mockk() } returns Zatoshi(500_000L) },
        errorStateMapper = mockk<ErrorMapperUseCase>(relaxed = true),
        zashiSpendingKeyDataSource = zashiSpendingKeyDataSource,
        biometricRepository = mockk<BiometricRepository>(relaxed = true),
        zashiProposalRepository = zashiProposalRepository,
        keystoneProposalRepository = keystoneProposalRepository,
        submitProposal = submitProposal,
        synchronizerProvider = synchronizerProvider,
    )

    private class FakeNavigationRouter : NavigationRouter {
        val forwardedRoutes = mutableListOf<Any>()

        override fun forward(vararg routes: Any) {
            forwardedRoutes.addAll(routes)
        }

        override fun replace(vararg routes: Any) = Unit

        override fun replaceAll(vararg routes: Any) = Unit

        override fun replaceFrom(route: KClass<*>, vararg routes: Any) = Unit

        override fun back() = Unit

        override fun backTo(route: KClass<*>) = Unit

        override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

        override fun backToRoot() = Unit

        override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
    }
}
