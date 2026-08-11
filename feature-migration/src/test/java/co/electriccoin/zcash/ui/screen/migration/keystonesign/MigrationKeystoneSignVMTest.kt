package co.electriccoin.zcash.ui.screen.migration.keystonesign

import androidx.navigation.NavBackStackEntry
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
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.migration.keystonescan.MigrationKeystoneScanArgs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import co.electriccoin.zcash.ui.design.R as DesignR

/**
 * Unit tests for [MigrationKeystoneSignVM].
 *
 * The VM's sole active work is [buildBatch], which runs in [init]. Everything else is either
 * reactive state derived from flows or simple navigation callbacks. The tests therefore rely on
 * [advanceUntilIdle] to let the init coroutine complete before asserting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MigrationKeystoneSignVMTest {
    // Fixed account shared across all tests — same UUID → same storage key → repo guards pass.
    private val testSdkAccount =
        AccountFixture.new(
            accountUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")
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

    // -------------------------------------------------------------------------
    // Happy path: simple single-round batch (no note-split, single transfer)
    // -------------------------------------------------------------------------

    @Test
    fun successfulBuildPopulatesQrPartsAndState() =
        runTest {
            val sdk = fakeSdk(qrParts = listOf("frame0", "frame1"))
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            // State is non-null once the batch is built.
            assertNotNull(vm.state.value)
            // No failure sheet on success.
            assertNull(vm.failureSheet.value)
            // QR parts were stored in PendingKeystoneMigrationPczts so the scan screen can use them.
            assertNotNull(pendingPczts.get(testAccountKeyId))

            collectJob.cancel()
        }

    @Test
    fun successfulBuildStateContainsExpectedButtonLabels() =
        runTest {
            val sdk = fakeSdk()
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            val state = vm.state.value
            assertNotNull(state)
            // Positive button says "Get Signature".
            assertEquals(
                DesignR.string.migrationKeystoneSign_getSignature,
                (state.positiveButton.text as? StringResource.ByResource)?.resource,
            )
            // Negative button says "Reject".
            assertEquals(
                DesignR.string.migrationKeystoneSign_reject,
                (state.negativeButton.text as? StringResource.ByResource)?.resource,
            )

            collectJob.cancel()
        }

    @Test
    fun singleRoundBatchHasNoRoundSuffixInTitle() =
        runTest {
            // A single transfer never exceeds the 96-action round budget → one round, no "(1 of 1)" suffix.
            val sdk = fakeSdk()
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            val title = vm.state.value?.title
            assertEquals(
                DesignR.string.migrationKeystoneSign_scanWithKeystone,
                (title as? StringResource.ByResource)?.resource,
            )
            val roundSuffix = title?.roundSuffixArg()
            assertTrue(
                roundSuffix is StringResource.ByString && roundSuffix.value.isEmpty(),
                "Single-round title should not contain a round suffix, got: $roundSuffix"
            )

            collectJob.cancel()
        }

    @Test
    fun multiRoundBatchAppendsSuffixForRoundZero() =
        runTest {
            // Simulate a resumed round-0 for a batch whose total round count > 1.
            // We do this by pre-populating pendingKeystonePczts with roundIndex=0 and enough
            // transferUnsignedPczts that totalRounds > 1. The VM reads "existing" and skips the
            // SDK build path, so the SDK's createUnsignedTransferPczts won't be called.
            val transferCount = NO_SPLIT_ROUND_CAPACITY + 1 // 33 transfers = 99 actions → totalRounds = 2
            val existingPczts =
                PendingKeystoneMigrationPczts(
                    requestId = byteArrayOf(0x01, 0x02),
                    splitUnsignedPczt = null,
                    transferUnsignedPczts = (0 until transferCount).map { it.toLong() to byteArrayOf(it.toByte()) },
                    roundIndex = 0,
                )
            val sdk = fakeSdk()
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply { set(testAccountKeyId, existingPczts) }
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            // Title should carry a "(1 of 2)" round suffix since roundIndex=0, totalRounds=2.
            val title = vm.state.value?.title
            val roundSuffix = title?.roundSuffixArg()
            assertEquals(
                DesignR.string.migrationKeystoneSign_roundSuffix,
                (roundSuffix as? StringResource.ByResource)?.resource,
                "Multi-round title should contain round suffix, got: $roundSuffix"
            )
            assertEquals(listOf(1, 2), (roundSuffix as? StringResource.ByResource)?.args)

            collectJob.cancel()
        }

    @Test
    fun multiRoundBatchRoundIndexOneAppendsSuffix() =
        runTest {
            // Simulate being on round 1 (0-based index 1) of a 2-round batch.
            val transferCount = NO_SPLIT_ROUND_CAPACITY + 1
            val existingPczts =
                PendingKeystoneMigrationPczts(
                    requestId = byteArrayOf(0x01, 0x02),
                    splitUnsignedPczt = null,
                    transferUnsignedPczts = (0 until transferCount).map { it.toLong() to byteArrayOf(it.toByte()) },
                    roundIndex = 1,
                    accumulatedTransferSigned =
                        (0 until NO_SPLIT_ROUND_CAPACITY).map { it.toLong() to byteArrayOf(it.toByte()) },
                )
            val sdk = fakeSdk()
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply { set(testAccountKeyId, existingPczts) }
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            val title = vm.state.value?.title
            val roundSuffix = title?.roundSuffixArg()
            assertEquals(
                DesignR.string.migrationKeystoneSign_roundSuffix,
                (roundSuffix as? StringResource.ByResource)?.resource,
                "Round-1 title should carry a round suffix, got: $roundSuffix"
            )
            assertEquals(listOf(2, 2), (roundSuffix as? StringResource.ByResource)?.args)

            collectJob.cancel()
        }

    // -------------------------------------------------------------------------
    // Failure / error arm in buildBatch
    // -------------------------------------------------------------------------

    @Test
    fun sdkFailureOnBuildBatchShowsFailureSheet() =
        runTest {
            // SDK throws during createUnsignedTransferPczts (or any other suspension point).
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { keystoneSigningRoundBudget() } returns
                        cash.z.ecc.android.sdk
                            .KeystoneSigningRoundBudget(96, 16, 3)
                    coEvery { isNoteSplitNeeded() } returns false
                    coEvery { createUnsignedTransferPczts(any()) } throws RuntimeException("PCZT build failed")
                }
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)

            advanceUntilIdle()

            val sheet = vm.failureSheet.value
            assertNotNull(sheet, "Failure sheet must appear when buildBatch throws")
            assertTrue(!sheet.message.isEmpty())
            // A retry callback must exist (the sheet re-runs buildBatch on retry).
            assertNotNull(sheet.onRetry)
        }

    @Test
    fun failureSheetRetryDismissesSheetAndRetriesBuild() =
        runTest {
            var callCount = 0
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { keystoneSigningRoundBudget() } returns
                        cash.z.ecc.android.sdk
                            .KeystoneSigningRoundBudget(96, 16, 3)
                    coEvery { isNoteSplitNeeded() } returns false
                    coEvery { createUnsignedTransferPczts(any()) } answers {
                        callCount++
                        if (callCount == 1) throw RuntimeException("first attempt fails")
                        listOf(1L to Pczt(byteArrayOf(0x01)))
                    }
                    coEvery { buildKeystoneSignBatchQrParts(any(), any(), any(), any()) } returns listOf("frame0")
                }
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            // First build failed → sheet visible.
            assertNotNull(vm.failureSheet.value)
            // Retry: re-triggers buildBatch with a fresh schedule.
            pendingSchedule.set(testAccountKeyId, schedule()) // re-arm so second build can read it
            vm.failureSheet.value!!
                .onRetry!!
                .invoke()
            advanceUntilIdle()

            // After successful retry the sheet clears.
            assertNull(vm.failureSheet.value)

            collectJob.cancel()
        }

    @Test
    fun failureSheetDismissOnlyHidesSheet() =
        runTest {
            // Simulate a resumed mid-batch round: pendingKeystonePczts already has an earlier
            // round's accumulated signed PCZTs. When the retry build for the CURRENT round fails
            // (buildKeystoneSignBatchQrParts throws — the only SDK call the "existing" branch of
            // buildBatch makes), dismissing the resulting failure sheet must only hide the sheet,
            // never discard the already-signed rounds by clearing pendingKeystonePczts.
            val existingPczts =
                PendingKeystoneMigrationPczts(
                    requestId = byteArrayOf(0x01),
                    splitUnsignedPczt = null,
                    transferUnsignedPczts = listOf(1L to byteArrayOf(0x02)),
                    roundIndex = 0,
                    accumulatedSplitSigned = null,
                    accumulatedPrepSigned = emptyList(),
                    accumulatedTransferSigned = listOf(1L to byteArrayOf(0x03)),
                )
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { keystoneSigningRoundBudget() } returns
                        cash.z.ecc.android.sdk
                            .KeystoneSigningRoundBudget(96, 16, 3)
                    coEvery { buildKeystoneSignBatchQrParts(any(), any(), any(), any()) } throws
                        RuntimeException("SDK error")
                }
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts =
                PendingKeystoneMigrationPcztsRepositoryImpl()
                    .apply { set(testAccountKeyId, existingPczts) }
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)

            advanceUntilIdle()

            assertNotNull(vm.failureSheet.value)
            // onDismiss only hides the sheet — it must not discard already-signed rounds by
            // rejecting the whole batch (that's a separate, explicit user action).
            vm.failureSheet.value!!
                .onDismiss
                .invoke()

            assertEquals(0, router.backCount)
            assertNull(vm.failureSheet.value)
            assertNotNull(pendingSchedule.peek(testAccountKeyId))
            assertNotNull(pendingPczts.get(testAccountKeyId))
        }

    @Test
    fun sdkBuildBatchSkippedWhenScheduleIsAbsent() =
        runTest {
            // No schedule stored → buildBatch returns null early, no failure sheet, state stays null.
            val sdk = fakeSdk()
            val pendingSchedule = PendingMigrationScheduleRepositoryImpl() // empty — no schedule set
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            // Schedule was absent → buildBatch returned early; state is null (no QR loaded),
            // but no failure sheet either (this is the "early return null" path, not the onFailure path).
            assertNull(vm.failureSheet.value)
            // State depends on combine which bounces back when schedule is missing via navigationRouter.back().
            // The back() call happens from the combine block when pendingSchedule.peek() is null.
            // Characterization: router.backCount >= 1 because combine fires with the account present
            // but the schedule absent.
            assertTrue(router.backCount >= 1, "Missing schedule should trigger navigationRouter.back()")

            collectJob.cancel()
        }

    // -------------------------------------------------------------------------
    // onGetSignature (positive button / "Get Signature")
    // -------------------------------------------------------------------------

    @Test
    fun onGetSignatureNavigatesForwardToScanScreenWithCorrectArgs() =
        runTest {
            val sdk = fakeSdk()
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val mode = MigrationMode.AUTOMATIC
            val vm = vm(sdk, pendingSchedule, pendingPczts, router, mode = mode)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            assertNotNull(vm.state.value)
            vm.state.value!!
                .positiveButton.onClick
                .invoke()

            assertEquals(1, router.forwardedRoutes.size)
            val forwarded = router.forwardedRoutes.first()
            assertTrue(forwarded is MigrationKeystoneScanArgs)
            assertEquals(mode, forwarded.mode)

            collectJob.cancel()
        }

    @Test
    fun onGetSignatureWithImmediateModeForwardsImmediateArgs() =
        runTest {
            val sdk = fakeSdk()
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router, mode = MigrationMode.IMMEDIATE)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            vm.state.value!!
                .positiveButton.onClick
                .invoke()

            val forwarded = router.forwardedRoutes.firstOrNull()
            assertTrue(forwarded is MigrationKeystoneScanArgs)
            assertEquals(MigrationMode.IMMEDIATE, forwarded.mode)

            collectJob.cancel()
        }

    // -------------------------------------------------------------------------
    // onReject / back / cancel behavior
    // -------------------------------------------------------------------------

    @Test
    fun onRejectNavigatesBackAndClearsBothRepositories() =
        runTest {
            val sdk = fakeSdk()
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            assertNotNull(vm.state.value)
            // Invoke onBack on the state — which calls onReject.
            vm.state.value!!
                .onBack
                .invoke()

            assertEquals(1, router.backCount)
            // Characterization: onReject clears schedule AND pczts repos.
            assertNull(pendingSchedule.peek(testAccountKeyId))
            assertNull(pendingPczts.get(testAccountKeyId))

            collectJob.cancel()
        }

    @Test
    fun negativeButtonClickAlsoCallsOnReject() =
        runTest {
            val sdk = fakeSdk()
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            val state = vm.state.value
            assertNotNull(state)
            assertNotNull(state.negativeButton)
            state.negativeButton.onClick.invoke()

            assertEquals(1, router.backCount)

            collectJob.cancel()
        }

    // -------------------------------------------------------------------------
    // generateNextQrCode cycling
    // -------------------------------------------------------------------------

    @Test
    fun generateNextQrCodeCyclesFrameIndexForMultiPartQr() =
        runTest {
            // Provide two QR frames — clicking generateNextQrCode should advance the displayed frame.
            val sdk = fakeSdk(qrParts = listOf("frame0", "frame1"))
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            val stateBefore = vm.state.value
            assertNotNull(stateBefore)
            // Frame 0 should be showing first (index 0 → "frame0").
            assertEquals("frame0", stateBefore.qrData)

            // Advance to frame 1.
            stateBefore.generateNextQrCode.invoke()
            advanceUntilIdle()

            assertEquals("frame1", vm.state.value?.qrData)

            // Wraps around back to frame 0.
            vm.state.value!!
                .generateNextQrCode
                .invoke()
            advanceUntilIdle()

            assertEquals("frame0", vm.state.value?.qrData)

            collectJob.cancel()
        }

    // -------------------------------------------------------------------------
    // Note-split path
    // -------------------------------------------------------------------------

    @Test
    fun whenNoteSplitNeededSdkBuildPathIsFollowed() =
        runTest {
            // When isNoteSplitNeeded() = true, buildBatch calls:
            //   prepareNoteSplit() → proposeMigrationTransfersFromSplit(proposal) →
            //   createUnsignedNoteSplitPczt(proposal) → createUnsignedTransferPczts(scheduleFromSplit)
            // Stub all four so the happy path completes; assert failureSheet == null.
            val fakeProposal =
                cash.z.ecc.android.sdk.NoteSplitProposal(
                    outputNotes = listOf(50_000L, 50_000L),
                    fee = 1_000L,
                    proposalHandle = 42L,
                )
            val fakeScheduleFromSplit = schedule()
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { keystoneSigningRoundBudget() } returns
                        cash.z.ecc.android.sdk
                            .KeystoneSigningRoundBudget(96, 16, 3)
                    coEvery { isNoteSplitNeeded() } returns true
                    coEvery { prepareNoteSplit() } returns fakeProposal
                    coEvery { proposeMigrationTransfersFromSplit(fakeProposal) } returns fakeScheduleFromSplit
                    coEvery { createUnsignedNoteSplitPczt(fakeProposal) } returns Pczt(byteArrayOf(0x02))
                    coEvery { createUnsignedTransferPczts(fakeScheduleFromSplit) } returns
                        listOf(1L to Pczt(byteArrayOf(0x01)))
                    coEvery { buildKeystoneSignBatchQrParts(any(), any(), any(), any()) } returns listOf("frame0")
                }
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)
            val collectJob = launch { vm.state.collect {} }

            advanceUntilIdle()

            // No failure sheet → the split path completed without error.
            assertNull(vm.failureSheet.value)

            collectJob.cancel()
        }

    @Test
    fun whenNoteSplitNeededAndSdkFailsShowsFailureSheet() =
        runTest {
            val sdk =
                mockk<OrchardMigrationSdk>(relaxed = true) {
                    coEvery { keystoneSigningRoundBudget() } returns
                        cash.z.ecc.android.sdk
                            .KeystoneSigningRoundBudget(96, 16, 3)
                    coEvery { isNoteSplitNeeded() } returns true
                    coEvery { prepareNoteSplit() } throws RuntimeException("split failed")
                }
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm = vm(sdk, pendingSchedule, pendingPczts, router)

            advanceUntilIdle()

            assertNotNull(vm.failureSheet.value)
        }

    // -------------------------------------------------------------------------
    // No SDK available (throws from GetOrchardMigrationSdkUseCase)
    // -------------------------------------------------------------------------

    @Test
    fun noSdkAvailableShowsFailureSheet() =
        runTest {
            // SDK unavailable (throws — no wallet) → runCatching in buildBatch() → onFailure → sheet.
            val pendingSchedule =
                PendingMigrationScheduleRepositoryImpl()
                    .apply { set(testAccountKeyId, schedule()) }
            val pendingPczts = PendingKeystoneMigrationPcztsRepositoryImpl()
            val router = FakeNavigationRouter()
            val vm =
                MigrationKeystoneSignVM(
                    args = MigrationKeystoneSignArgs(mode = MigrationMode.AUTOMATIC),
                    getSelectedWalletAccount = testGetSelectedWalletAccount,
                    getOrchardMigrationSdk =
                        mockk {
                            coEvery { this@mockk() } throws IllegalStateException("no wallet available")
                        },
                    pendingSchedule = pendingSchedule,
                    pendingKeystonePczts = pendingPczts,
                    navigationRouter = router,
                )

            advanceUntilIdle()

            assertNotNull(vm.failureSheet.value)
        }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun fakeSdk(qrParts: List<String> = listOf("frame0")): OrchardMigrationSdk =
        mockk(relaxed = true) {
            coEvery { isNoteSplitNeeded() } returns false
            coEvery { createUnsignedTransferPczts(any()) } returns listOf(1L to Pczt(byteArrayOf(0x01)))
            coEvery { buildKeystoneSignBatchQrParts(any(), any(), any(), any()) } returns qrParts
            // The engine's real signing-round constants (signing_rounds.rs).
            coEvery { keystoneSigningRoundBudget() } returns
                cash.z.ecc.android.sdk
                    .KeystoneSigningRoundBudget(maxActions = 96, preparationActions = 16, transferActions = 3)
        }

    private fun schedule(): MigrationSchedule =
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

    private fun vm(
        sdk: OrchardMigrationSdk,
        pendingSchedule: PendingMigrationScheduleRepositoryImpl,
        pendingPczts: PendingKeystoneMigrationPcztsRepositoryImpl,
        router: FakeNavigationRouter,
        mode: MigrationMode = MigrationMode.AUTOMATIC,
    ) = MigrationKeystoneSignVM(
        args = MigrationKeystoneSignArgs(mode = mode),
        getSelectedWalletAccount = testGetSelectedWalletAccount,
        getOrchardMigrationSdk =
            mockk<GetOrchardMigrationSdkUseCase> {
                coEvery { this@mockk() } returns sdk
            },
        pendingSchedule = pendingSchedule,
        pendingKeystonePczts = pendingPczts,
        navigationRouter = router,
    )

    private class FakeNavigationRouter : NavigationRouter {
        val forwardedRoutes = mutableListOf<Any>()
        var backCount = 0
            private set

        override fun forward(vararg routes: Any) {
            forwardedRoutes.addAll(routes.toList())
        }

        override fun replace(vararg routes: Any) = Unit

        override fun replaceAll(vararg routes: Any) = Unit

        override fun back() {
            backCount++
        }

        override fun backTo(route: KClass<*>) = Unit

        override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

        override fun backToRoot() = Unit

        override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
    }

    // The title is DesignR.string.migrationKeystoneSign_scanWithKeystone with the round suffix as
    // its single %1$s arg — this pulls that nested StringResource back out for structural assertions
    // (a resource-backed StringResource carries no resolvable text without an Android Context).
    private fun StringResource.roundSuffixArg(): StringResource? =
        (this as? StringResource.ByResource)?.args?.singleOrNull() as? StringResource
}

// 96 actions / 3 actions-per-transfer with no split in the round (engine signing_rounds constants).
private const val NO_SPLIT_ROUND_CAPACITY = 32
