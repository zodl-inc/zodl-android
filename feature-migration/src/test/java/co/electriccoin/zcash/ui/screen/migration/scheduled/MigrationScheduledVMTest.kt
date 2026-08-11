package co.electriccoin.zcash.ui.screen.migration.scheduled

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferProposal
import cash.z.ecc.android.sdk.TransferResult
import cash.z.ecc.android.sdk.fixture.AccountFixture
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.IsBackgroundExecutionAvailableProvider
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPczts
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.FinalizeMigrationScheduleUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationSnapshotUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationScheduledVMTest {
    private val testSdkAccount =
        AccountFixture.new(
            accountUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        )
    private val testAccountKeyId = testSdkAccount.accountUuid.toStorageKeyId()
    private val testWalletAccount: WalletAccount =
        mockk(relaxed = true) {
            every { sdkAccount } returns testSdkAccount
        }
    private val testGetSelectedWalletAccount: GetSelectedWalletAccountUseCase =
        mockk {
            coEvery { this@mockk() } returns testWalletAccount
            every { observe() } returns flowOf(testWalletAccount)
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
    fun noPendingKeystoneBatchSkipsFinalizingImmediately() =
        runTest {
            val vm = vm()

            advanceUntilIdle()

            assertFalse(vm.isFinalizing.value)
        }

    @Test
    fun backgroundNotAvailableShowsHint() =
        runTest {
            val backgroundAvailable =
                mockk<IsBackgroundExecutionAvailableProvider> {
                    every { isAvailable() } returns false
                }
            val vm = vm(isBackgroundExecutionAvailable = backgroundAvailable)

            advanceUntilIdle()
            val state = vm.state.first { !it.isLoading }
            assertNotNull(
                state.content?.backgroundHint,
                "backgroundHint should not be null when background execution is unavailable",
            )
        }

    @Test
    fun backgroundAvailableHidesHint() =
        runTest {
            val backgroundAvailable =
                mockk<IsBackgroundExecutionAvailableProvider> {
                    every { isAvailable() } returns true
                }
            val vm = vm(isBackgroundExecutionAvailable = backgroundAvailable)

            advanceUntilIdle()
            val state = vm.state.first { !it.isLoading }
            assertNull(
                state.content?.backgroundHint,
                "backgroundHint should be null when background execution is available",
            )
        }

    @Test
    fun pendingKeystoneBatchIsAppliedStoredAndFinalizedThenCleared() =
        runTest {
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply {
                        set(
                            testAccountKeyId,
                            PendingKeystoneMigrationPczts(
                                requestId = byteArrayOf(1, 2, 3),
                                splitUnsignedPczt = null,
                                transferUnsignedPczts = listOf(11L to byteArrayOf(9, 9)),
                                roundIndex = 1,
                                accumulatedTransferSigned = listOf(11L to byteArrayOf(1)),
                            )
                        )
                    }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val finalize = mockk<FinalizeMigrationScheduleUseCase>(relaxed = true)
            val vm =
                vm(
                    pendingSchedule = pendingSchedule,
                    pendingKeystonePczts = pendingPczts,
                    getOrchardMigrationSdk = mockk { coEvery { this@mockk() } returns sdk },
                    finalizeMigrationSchedule = finalize,
                )

            advanceUntilIdle()

            assertFalse(vm.isFinalizing.value)
            coVerify(exactly = 1) { sdk.storeSignedSchedulePczts(any()) }
            // MOB-1669: the post-Keystone-scan path opts out of eagerly starting the live driver
            // here — see FinalizeMigrationScheduleUseCase's doc for why.
            coVerify(exactly = 1) { finalize(any(), any(), startLiveDriverImmediately = false) }
            assertNull(pendingSchedule.get(testAccountKeyId))
            assertNull(pendingPczts.get(testAccountKeyId))
        }

    @Test
    fun failedNoteSplitStoreShowsRetryableFailureSheet() =
        runTest {
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply {
                        set(
                            testAccountKeyId,
                            PendingKeystoneMigrationPczts(
                                requestId = byteArrayOf(1, 2, 3),
                                splitUnsignedPczt = byteArrayOf(5),
                                transferUnsignedPczts = emptyList(),
                                roundIndex = 1,
                                accumulatedSplitSigned = byteArrayOf(6),
                            )
                        )
                    }
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { storeSignedNoteSplitPczt(any(), any()) } returns
                        TransferResult.NetworkError(retryable = true)
                }
            val finalize = mockk<FinalizeMigrationScheduleUseCase>(relaxed = true)
            val vm =
                vm(
                    pendingSchedule = pendingSchedule,
                    pendingKeystonePczts = pendingPczts,
                    getOrchardMigrationSdk = mockk { coEvery { this@mockk() } returns sdk },
                    finalizeMigrationSchedule = finalize,
                    // Relaxed mockk can't safely default a generic suspend fun's return type
                    // (throws ClassCastException on unstubbed call) — stub explicitly.
                    isMigrationTorEnabledStorageProvider =
                        mockk { coEvery { get() } returns false },
                )

            advanceUntilIdle()

            assertNotNull(vm.failureSheet.value)
            coVerify(exactly = 0) { finalize(any(), any()) }
            // Still pending — a retry should be able to pick this back up.
            assertNotNull(pendingSchedule.get(testAccountKeyId))
            assertNotNull(pendingPczts.get(testAccountKeyId))
        }

    @Test
    fun unexpectedThrowDuringFinalizeShowsRetryableFailureSheetAndStaysFinalizing() =
        runTest {
            // Any unguarded throw in finalizeIfPendingKeystoneBatch (e.g. transient "database is
            // locked" from the migration engine mutex) must not crash the app right after the user
            // completes a physical Keystone signing ceremony — see the try/catch added around its
            // body. isFinalizing deliberately stays true so the screen keeps its loading state
            // under the failure sheet, rather than racing the snapshot flow against a schedule
            // that was never actually finalized.
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply {
                        set(
                            testAccountKeyId,
                            PendingKeystoneMigrationPczts(
                                requestId = byteArrayOf(1, 2, 3),
                                splitUnsignedPczt = null,
                                transferUnsignedPczts = listOf(11L to byteArrayOf(9, 9)),
                                roundIndex = 1,
                                accumulatedTransferSigned = listOf(11L to byteArrayOf(1)),
                            )
                        )
                    }
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { storeSignedSchedulePczts(any()) } throws RuntimeException("database is locked")
                }
            val finalize = mockk<FinalizeMigrationScheduleUseCase>(relaxed = true)
            val vm =
                vm(
                    pendingSchedule = pendingSchedule,
                    pendingKeystonePczts = pendingPczts,
                    getOrchardMigrationSdk = mockk { coEvery { this@mockk() } returns sdk },
                    finalizeMigrationSchedule = finalize,
                )

            advanceUntilIdle()

            assertNotNull(vm.failureSheet.value)
            assertTrue(vm.isFinalizing.value)
            coVerify(exactly = 0) { finalize(any(), any()) }
            // Still pending — a retry should be able to pick this back up.
            assertNotNull(pendingSchedule.get(testAccountKeyId))
            assertNotNull(pendingPczts.get(testAccountKeyId))
        }

    @Suppress("LongParameterList")
    private fun vm(
        getMigrationSnapshot: GetMigrationSnapshotUseCase =
            mockk {
                // A REAL (empty) snapshot: a null return now means "SDK not ready yet" and keeps
                // the LCE loading (review L3) — these tests assert on the rendered state.
                coEvery { this@mockk(null) } returns
                    co.electriccoin.zcash.ui.common.model.migration
                        .LiveMigrationSnapshot(transfers = emptyList(), preparations = emptyList(), tipHeight = 0L)
            },
        isBackgroundExecutionAvailable: IsBackgroundExecutionAvailableProvider = mockk(relaxed = true),
        navigationRouter: NavigationRouter = FakeNavigationRouter(),
        errorStateMapper: ErrorMapperUseCase = mockk(relaxed = true),
        getSelectedWalletAccount: GetSelectedWalletAccountUseCase = testGetSelectedWalletAccount,
        getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase = mockk(relaxed = true),
        pendingSchedule: PendingMigrationScheduleRepositoryImpl = PendingMigrationScheduleRepositoryImpl(),
        pendingKeystonePczts: PendingKeystoneMigrationPcztsRepositoryImpl =
            PendingKeystoneMigrationPcztsRepositoryImpl(),
        finalizeMigrationSchedule: FinalizeMigrationScheduleUseCase = mockk(relaxed = true),
        isMigrationTorEnabledStorageProvider: IsMigrationTorEnabledStorageProvider = mockk(relaxed = true),
        synchronizerProvider: SynchronizerProvider = mockk(relaxed = true),
    ) = MigrationScheduledVM(
        getMigrationSnapshot = getMigrationSnapshot,
        navigationRouter = navigationRouter,
        errorStateMapper = errorStateMapper,
        isBackgroundExecutionAvailableProvider = isBackgroundExecutionAvailable,
        getSelectedWalletAccount = getSelectedWalletAccount,
        getOrchardMigrationSdk = getOrchardMigrationSdk,
        pendingSchedule = pendingSchedule,
        pendingKeystonePczts = pendingKeystonePczts,
        finalizeMigrationSchedule = finalizeMigrationSchedule,
        isMigrationTorEnabledStorageProvider = isMigrationTorEnabledStorageProvider,
        synchronizerProvider = synchronizerProvider,
    )

    private fun schedule() =
        MigrationSchedule(
            transfers =
                listOf(
                    TransferProposal(
                        id = 11L,
                        amountZatoshi = 100_000L,
                        anchorHeight = 100L,
                        nextExecutableAfterHeight = 200L,
                        expiryHeight = 300L,
                    )
                ),
            estimatedDurationHours = 1,
            proposalHandle = 0L,
        )

    private class FakeNavigationRouter : NavigationRouter {
        override fun forward(vararg routes: Any) = Unit

        override fun replace(vararg routes: Any) = Unit

        override fun replaceAll(vararg routes: Any) = Unit

        override fun back() = Unit

        override fun backTo(route: KClass<*>) = Unit

        override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

        override fun backToRoot() = Unit

        override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
    }
}
