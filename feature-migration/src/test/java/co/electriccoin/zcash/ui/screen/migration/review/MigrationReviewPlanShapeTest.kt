package co.electriccoin.zcash.ui.screen.migration.review

import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.PreparationStep
import cash.z.ecc.android.sdk.TransferProposal
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.ZashiSpendingKeyDataSource
import co.electriccoin.zcash.ui.common.model.ZodlAccount
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
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that [MigrationReviewVM.createState] produces the correct [MigrationReviewState]
 * shape for three distinct plan forms:
 *
 * 1. **N-prep plan** — 4 preparations across 3 layers + 11 transfers (mirrors a live multi-note
 *    wallet). More than one preparation collapses into a single "Split Balance" summary row with a
 *    "Show details" sheet (Figma "PR App Designs Q3'26" node 5207:16023, 2026-08-03) — `preparations`
 *    is empty and `preparationsSummarySubtitle`/`preparationDetails` carry the per-step breakdown
 *    instead.
 *
 * 2. **0-prep plan** — a single-note direct-funding wallet: `preparations = emptyList()`, 1
 *    transfer. The VM must emit an empty preparations list so the screen's fallback collapsed row
 *    triggers.
 *
 * 3. **1-prep plan** — exactly one note-split preparation + 2 transfers, the boundary case between
 *    "no split needed" and "multi-split" rendering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MigrationReviewPlanShapeTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Shape 1 — N-prep (4 preparations × 3 layers, 11 transfers)
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * A realistic 4-preparation, 11-transfer schedule is mapped to 4 numbered rows and 11 transfer
     * rows. The preparation numbers must run 1..4 in iteration order, every preparation must carry
     * a non-null scheduledLabel, and every transfer must carry a non-zero amount.
     */
    @Test
    fun nPrepPlan_preparationsAndTransfersMappedCorrectly() =
        runTest {
            val anchorHeight = 4_000_000L

            // Layer 0: two independent root splits (broadcastHeight = anchorHeight).
            val prep0 =
                PreparationStep(id = 10L, layer = 0, index = 0, broadcastHeight = anchorHeight, dependsOn = emptyList())
            val prep1 =
                PreparationStep(id = 11L, layer = 0, index = 1, broadcastHeight = anchorHeight, dependsOn = emptyList())
            // Layer 1: one split that depends on both layer-0 outputs.
            val prep2 =
                PreparationStep(
                    id = 20L,
                    layer = 1,
                    index = 0,
                    broadcastHeight = anchorHeight + 10L,
                    dependsOn = listOf(10L, 11L)
                )
            // Layer 2: a final split combining everything.
            val prep3 =
                PreparationStep(
                    id = 30L,
                    layer = 2,
                    index = 0,
                    broadcastHeight = anchorHeight + 25L,
                    dependsOn = listOf(20L)
                )

            val transfers =
                (1..11).map { i ->
                    TransferProposal(
                        id = (100 + i).toLong(),
                        amountZatoshi = 500_000L * i,
                        anchorHeight = anchorHeight,
                        nextExecutableAfterHeight = anchorHeight + 50L + (i * 10L),
                        expiryHeight = anchorHeight + 200L + (i * 10L),
                    )
                }

            val schedule =
                MigrationSchedule(
                    transfers = transfers,
                    preparations = listOf(prep0, prep1, prep2, prep3),
                    estimatedDurationHours = 4,
                    proposalHandle = 99L,
                )

            val vm = vmWithSchedule(schedule)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            val state = assertNotNull(vm.state.value.content, "state must be loaded")

            // ── preparations collapse into a single "Split Balance" summary + sheet ──────────────────

            assertTrue(
                state.preparations.isEmpty(),
                "preparations must be empty once there's more than one — the summary row replaces them",
            )

            val subtitle =
                assertNotNull(state.preparationsSummarySubtitle, "summary subtitle must be present for >1 preparations")
            assertTrue(
                subtitle.asString().contains("4 steps"),
                "summary subtitle must mention the step count, got ${subtitle.asString()}",
            )

            val details =
                assertNotNull(state.preparationDetails, "preparation details must be present for >1 preparations")
            assertEquals(4, details.stepCount, "must report exactly 4 steps")
            assertEquals(4, details.steps.size, "must have exactly 4 step rows in the sheet")

            // Titles must be 1-indexed and monotonically increasing (iteration order = list order).
            assertEquals(
                (1..4).map { "Transaction $it of 4" },
                details.steps.map { it.title.asString() },
            )

            // Every step must carry a non-null, non-empty time label and status label.
            details.steps.forEach { step ->
                assertTrue(
                    step.timeLabel.asString().isNotEmpty(),
                    "step '${step.title.asString()}' must have a non-empty timeLabel",
                )
                assertTrue(
                    step.statusLabel.asString().isNotEmpty(),
                    "step '${step.title.asString()}' must have a non-empty statusLabel",
                )
            }

            // The two layer-0 splits (steps 1 & 2) have no dependency, so they must NOT read "Waits
            // on" — only step 3 (depends on 1 & 2) and step 4 (depends on 3) should.
            val statuses = details.steps.map { it.statusLabel.asString() }
            assertTrue(!statuses[0].contains("Waits"), "step 1 has no dependency, got '${statuses[0]}'")
            assertTrue(!statuses[1].contains("Waits"), "step 2 has no dependency, got '${statuses[1]}'")
            assertEquals("Waits on steps 1 & 2", statuses[2], "step 3 depends on steps 1 & 2")
            assertEquals("Waits on step 3", statuses[3], "step 4 depends on step 3")

            // ── transfers ─────────────────────────────────────────────────────────────────────────────

            assertEquals(11, state.transfers.size, "must have exactly 11 transfer rows")

            // The headline count (totalCount field shared by all rows) must reflect the 11 crossings,
            // not any preparation count.
            state.transfers.forEach { t ->
                assertEquals(11, t.totalCount, "every transfer row must report totalCount=11")
            }

            // Transfer 1-indexes must run 1..11.
            assertEquals((1..11).toList(), state.transfers.map { it.index })

            // Every transfer must carry a non-null amount (ExchangeRate is OptedOut so fiatAmount is null).
            state.transfers.forEach { t ->
                assertNotNull(t.amount, "transfer #${t.index} must carry an amount")
                assertNull(t.fiatAmount, "fiatAmount must be null when exchange rate is opted out")
            }

            // The amount values must differ (they were built with distinct amountZatoshi values above).
            val distinctAmounts = state.transfers.map { it.amount }.toSet()
            assertEquals(11, distinctAmounts.size, "each transfer must have a unique amount value")

            // ── preparations carry no amount ───────────────────────────────────────────────────────────
            // MigrationReviewPreparationState has no amount field — the compiler enforces this, but we
            // verify the absence structurally by confirming the data class only exposes number + label.
            val prepFields =
                MigrationReviewPreparationState::class.java.declaredFields
                    .map { it.name }
                    .toSet()
            assertTrue(
                "number" in prepFields && "scheduledLabel" in prepFields,
                "preparation state must have number + scheduledLabel",
            )
            assertTrue(
                "amount" !in prepFields && "fiatAmount" !in prepFields,
                "preparation state must NOT have amount/fiatAmount",
            )

            collectJob.cancel()
        }

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Shape 2 — 0-prep plan (single-note direct funding: no note-split needed, 1 transfer)
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * When the wallet contains a single note that maps directly to one transfer with no split
     * preparation required, `preparations` must be empty so the screen's fallback collapsed row
     * can trigger. The single transfer must still carry its amount.
     */
    @Test
    fun zeroPrepPlan_preparationsListIsEmptyAndSingleTransferCarriesAmount() =
        runTest {
            val anchorHeight = 4_100_000L

            val schedule =
                MigrationSchedule(
                    transfers =
                        listOf(
                            TransferProposal(
                                id = 1L,
                                amountZatoshi = 1_000_000L,
                                anchorHeight = anchorHeight,
                                nextExecutableAfterHeight = anchorHeight + 10L,
                                expiryHeight = anchorHeight + 100L,
                            )
                        ),
                    preparations = emptyList(),
                    estimatedDurationHours = 0,
                    proposalHandle = 1L,
                )

            val vm = vmWithSchedule(schedule)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            val state = assertNotNull(vm.state.value.content, "state must be loaded")

            // ── preparations must be empty ─────────────────────────────────────────────────────────────
            assertTrue(state.preparations.isEmpty(), "preparations must be empty for a no-split wallet")

            // ── single transfer must be present and carry its amount ───────────────────────────────────
            assertEquals(1, state.transfers.size)
            val transfer = state.transfers.single()
            assertEquals(1, transfer.index)
            assertEquals(1, transfer.totalCount)
            assertNotNull(transfer.amount, "the lone transfer must carry an amount")

            collectJob.cancel()
        }

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Shape 3 — 1-prep plan (one note-split preparation + 2 transfers)
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The boundary case: exactly one preparation (a single note-split) and two resulting transfers.
     * The preparation is numbered 1 and its scheduledLabel is non-empty; the two transfers are
     * numbered 1 and 2 with totalCount=2.
     */
    @Test
    fun onePrepPlan_singlePreparationAndTwoTransfersMappedCorrectly() =
        runTest {
            val anchorHeight = 4_200_000L

            val schedule =
                MigrationSchedule(
                    transfers =
                        listOf(
                            TransferProposal(
                                id = 1L,
                                amountZatoshi = 400_000L,
                                anchorHeight = anchorHeight,
                                nextExecutableAfterHeight = anchorHeight + 15L,
                                expiryHeight = anchorHeight + 150L,
                            ),
                            TransferProposal(
                                id = 2L,
                                amountZatoshi = 600_000L,
                                anchorHeight = anchorHeight,
                                nextExecutableAfterHeight = anchorHeight + 30L,
                                expiryHeight = anchorHeight + 200L,
                            ),
                        ),
                    preparations =
                        listOf(
                            PreparationStep(
                                id = 50L,
                                layer = 0,
                                index = 0,
                                broadcastHeight = anchorHeight,
                                dependsOn = emptyList(),
                            )
                        ),
                    estimatedDurationHours = 1,
                    proposalHandle = 2L,
                )

            val vm = vmWithSchedule(schedule)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            val state = assertNotNull(vm.state.value.content, "state must be loaded")

            // ── preparations ──────────────────────────────────────────────────────────────────────────
            assertEquals(1, state.preparations.size, "must have exactly 1 preparation row")
            val prep = state.preparations.single()
            assertEquals(1, prep.number, "the single preparation must be numbered 1")
            assertTrue(
                prep.scheduledLabel is StringResource.ByString && prep.scheduledLabel.value.isNotEmpty(),
                "preparation scheduledLabel must be a non-empty ByString, got ${prep.scheduledLabel}",
            )

            // ── transfers ─────────────────────────────────────────────────────────────────────────────
            assertEquals(2, state.transfers.size, "must have exactly 2 transfer rows")
            assertEquals(listOf(1, 2), state.transfers.map { it.index })
            state.transfers.forEach { t ->
                assertEquals(2, t.totalCount, "every transfer row must report totalCount=2")
                assertNotNull(t.amount, "transfer #${t.index} must carry an amount")
            }

            // The two transfers must have distinct amounts (400 000 vs 600 000 zatoshi).
            val amounts = state.transfers.map { it.amount }.toSet()
            assertEquals(2, amounts.size, "the two transfer rows must have different amount values")

            collectJob.cancel()
        }

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    /** Build a VM whose [GetOrchardMigrationSdkUseCase] returns an SDK stub that proposes [schedule]. */
    private fun vmWithSchedule(schedule: MigrationSchedule): MigrationReviewVM {
        val sdk =
            mockk<OrchardMigrationSdk>(relaxed = true) {
                coEvery { proposeMigrationTransfers(any()) } returns schedule
                coEvery { estimateMigrationRunCount() } returns 1
                coEvery { estimatedSecondsPerBlock() } returns 75L
            }
        return MigrationReviewVM(
            args = MigrationReviewArgs(mode = MigrationMode.AUTOMATIC),
            getOrchardMigrationSdk = mockk { coEvery { this@mockk() } returns sdk },
            pendingMigrationScheduleRepository = mockk<PendingMigrationScheduleRepository>(relaxed = true),
            restartMigrationScheduleRepository =
                mockk<RestartMigrationScheduleRepository>(relaxed = true) {
                    every { consume(any()) } returns null
                },
            finalizeMigrationSchedule = mockk<FinalizeMigrationScheduleUseCase>(relaxed = true),
            navigationRouter = mockk<NavigationRouter>(relaxed = true),
            exchangeRateRepository =
                mockk<ExchangeRateRepository>(relaxed = true) {
                    every { state } returns MutableStateFlow(ExchangeRateState.OptedOut)
                },
            getSelectedWalletAccount =
                mockk<GetSelectedWalletAccountUseCase> {
                    coEvery { this@mockk() } returns mockk<ZodlAccount>(relaxed = true)
                    every { observe() } returns flowOf(mockk<ZodlAccount>(relaxed = true))
                },
            getOrchardBalance =
                mockk<GetOrchardBalanceUseCase> {
                    coEvery { this@mockk() } returns Zatoshi(500_000L)
                },
            errorStateMapper = mockk<ErrorMapperUseCase>(relaxed = true),
            zashiSpendingKeyDataSource = mockk<ZashiSpendingKeyDataSource>(relaxed = true),
            biometricRepository = mockk<BiometricRepository>(relaxed = true),
            zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true),
            keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true),
            submitProposal = mockk<SubmitProposalUseCase>(relaxed = true),
            synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true),
        )
    }

    // preparationsSummarySubtitle/preparationDetails are built by concatenating StringResources
    // (see MigrationReviewVM's `+` usage), which produces a CompositeStringResource rather than a
    // plain ByString — this walks that composite via reflection to flatten it back to text.
    @Suppress("UNCHECKED_CAST")
    private fun StringResource.asString(): String =
        when (this) {
            is StringResource.ByString -> {
                value
            }

            else -> {
                val resourcesField = this::class.java.getDeclaredField("resources").also { it.isAccessible = true }
                val parts = resourcesField.get(this) as List<StringResource>
                parts.joinToString(separator = "") { it.asString() }
            }
        }
}
