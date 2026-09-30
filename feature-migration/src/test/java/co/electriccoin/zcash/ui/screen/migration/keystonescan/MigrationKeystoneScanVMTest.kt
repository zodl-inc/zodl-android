package co.electriccoin.zcash.ui.screen.migration.keystonescan

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.KeystoneBatchDecodeResult
import cash.z.ecc.android.sdk.KeystoneBatchSignedPczts
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferProposal
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.model.Pczt
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPczts
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationKeystoneScanVMTest {
    // Fixed test account — all tests use the same account key so the repo guard is satisfied.
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
    fun outdatedFirmwareOnFirstRoundBlocksWithoutNavigating() =
        runTest {
            val sdk = fakeSdk(firmwareVersion = byteArrayOf(2, 9, 9))
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply { set(testAccountKeyId, pending(roundIndex = 0)) }
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)

            vm.onScanned("frame")
            advanceUntilIdle()

            assertNotNull(vm.failureSheet.value)
            assertTrue(router.forwardedRoutes.isEmpty())
            assertNotNull(pendingSchedule.get(testAccountKeyId))
            assertNotNull(pendingPczts.get(testAccountKeyId))
            assertEquals(0, pendingPczts.get(testAccountKeyId)?.roundIndex)

            vm.failureSheet.value
                ?.onDismiss
                ?.invoke()
            assertNull(vm.failureSheet.value)
            assertEquals(1, router.backCount)
        }

    @Test
    fun upToDateFirmwareOnFirstRoundNavigatesToScheduled() =
        runTest {
            val sdk = fakeSdk(firmwareVersion = byteArrayOf(3, 0, 2))
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply { set(testAccountKeyId, pending(roundIndex = 0)) }
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)

            vm.onScanned("frame")
            advanceUntilIdle()

            assertNull(vm.failureSheet.value)
            assertEquals(listOf<Any>(MigrationScheduledArgs), router.forwardedRoutes)
            // The final accumulated batch stays in the repositories — MigrationScheduledVM (the
            // navigation target) is what applies/stores/finalizes and clears them.
            assertNotNull(pendingSchedule.get(testAccountKeyId))
            assertEquals(1, pendingPczts.get(testAccountKeyId)?.roundIndex)
        }

    @Test
    fun outdatedFirmwareOnLaterRoundSkipsCheckAndProceeds() =
        runTest {
            val sdk = fakeSdk(firmwareVersion = byteArrayOf(2, 9, 9))
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
                                transferUnsignedPczts = (0 until 36).map { it.toLong() to byteArrayOf(it.toByte()) },
                                roundIndex = 1,
                                accumulatedTransferSigned =
                                    (0 until 35).map { it.toLong() to byteArrayOf(it.toByte()) },
                            )
                        )
                    }
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)

            vm.onScanned("frame")
            advanceUntilIdle()

            assertNull(vm.failureSheet.value)
            assertEquals(listOf<Any>(MigrationScheduledArgs), router.forwardedRoutes)
        }

    @Test
    fun reEntrantScanAfterCompletedRoundIsIgnored() =
        runTest {
            // The Keystone device doesn't rotate/clear its response QR after being scanned, and
            // the camera's ImageAnalysis keeps re-decoding that still-visible frame. A round that
            // already completed successfully must not process a second identical scan — see the
            // isProcessing comment in MigrationKeystoneScanVM's hand-off branches for why: the
            // real-world failure this guards against crashed Rust's apply_batch_signatures with
            // "expected 0" when a stale, past-the-end roundIndex produced an all-empty slice.
            val sdk = fakeSdk(firmwareVersion = byteArrayOf(3, 0, 2))
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply { set(testAccountKeyId, pending(roundIndex = 0)) }
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)

            vm.onScanned("frame")
            advanceUntilIdle()
            vm.onScanned("frame")
            advanceUntilIdle()

            coVerify(exactly = 1) { sdk.applyKeystoneBatchSignatures(any(), any(), any()) }
            assertEquals(listOf<Any>(MigrationScheduledArgs), router.forwardedRoutes)
        }

    @Test
    fun unexpectedThrowShowsRetryableFailureSheetAndAllowsRescan() =
        runTest {
            // Any unguarded throw here (e.g. a transient "database is locked" from the migration
            // engine mutex) must not crash the app mid-ceremony — see the try/catch added around
            // onScanned's body. isProcessing must also reset so the still-visible QR can be
            // rescanned instead of getting permanently stuck behind the `if (isProcessing) return`
            // guard at the top of onScanned.
            val sdk = fakeSdk(firmwareVersion = byteArrayOf(3, 0, 2))
            coEvery { sdk.applyKeystoneBatchSignatures(any(), any(), any()) } throws
                RuntimeException("database is locked")
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply { set(testAccountKeyId, pending(roundIndex = 0)) }
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)

            vm.onScanned("frame")
            advanceUntilIdle()

            assertNotNull(vm.failureSheet.value)
            assertTrue(router.forwardedRoutes.isEmpty())

            // isProcessing reset to false in the catch — a second scan must not be silently
            // ignored by the re-entrancy guard.
            vm.onScanned("frame")
            advanceUntilIdle()

            coVerify(exactly = 2) { sdk.decodeKeystoneSignBatchPart(any(), any()) }
        }

    private fun vm(
        sdk: OrchardMigrationSdk,
        pendingSchedule: PendingMigrationScheduleRepositoryImpl,
        pendingPczts: PendingKeystoneMigrationPcztsRepositoryImpl,
        router: FakeNavigationRouter,
    ) = MigrationKeystoneScanVM(
        args = MigrationKeystoneScanArgs(mode = MigrationMode.IMMEDIATE),
        getOrchardMigrationSdk = mockk<GetOrchardMigrationSdkUseCase> { coEvery { this@mockk() } returns sdk },
        getSelectedWalletAccount = testGetSelectedWalletAccount,
        pendingSchedule = pendingSchedule,
        pendingKeystonePczts = pendingPczts,
        navigationRouter = router,
    )

    private fun fakeSdk(firmwareVersion: ByteArray?): OrchardMigrationSdk =
        mockk(relaxed = true) {
            coEvery { keystoneSigningRoundBudget() } returns
                cash.z.ecc.android.sdk
                    .KeystoneSigningRoundBudget(96, 16, 3)
            coEvery { decodeKeystoneSignBatchPart(any(), any()) } returns
                KeystoneBatchDecodeResult(
                    complete = true,
                    progress = 100,
                    data = ByteArray(1),
                    firmwareVersion = firmwareVersion,
                )
            coEvery { applyKeystoneBatchSignatures(any(), any(), any()) } returns
                KeystoneBatchSignedPczts(splitSignedPczt = null, transferSignedPczts = listOf(Pczt(byteArrayOf(0))))
        }

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

    private fun pending(roundIndex: Int) =
        PendingKeystoneMigrationPczts(
            requestId = byteArrayOf(1, 2, 3),
            splitUnsignedPczt = null,
            transferUnsignedPczts = listOf(11L to byteArrayOf(9, 9)),
            roundIndex = roundIndex,
        )

    private class FakeNavigationRouter : NavigationRouter {
        var backCount = 0
            private set
        val forwardedRoutes = mutableListOf<Any>()

        override fun forward(vararg routes: Any) {
            forwardedRoutes.addAll(routes)
        }

        override fun replace(vararg routes: Any) = Unit

        override fun replaceAll(vararg routes: Any) = Unit

        override fun replaceFrom(route: KClass<*>, vararg routes: Any) = Unit

        override fun back() {
            backCount++
        }

        override fun backTo(route: KClass<*>) = Unit

        override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

        override fun backToRoot() = Unit

        override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
    }
}
