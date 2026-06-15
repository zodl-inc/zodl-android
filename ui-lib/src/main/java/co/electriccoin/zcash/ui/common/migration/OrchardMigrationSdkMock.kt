package co.electriccoin.zcash.ui.common.migration

import cash.z.ecc.android.sdk.MigrationProgress
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.NoteSplitProposal
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferProposal
import cash.z.ecc.android.sdk.TransferResult
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.BuildConfig
import co.electriccoin.zcash.ui.common.model.migration.MigrationPlan
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferStatus
import co.electriccoin.zcash.ui.common.repository.MigrationPlanRepository
import co.electriccoin.zcash.ui.common.repository.MockOrchardBalanceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Mock implementation of [OrchardMigrationSdk] for the PoC branch.
 *
 * State is persisted via [MigrationPlanRepository] so WorkManager Workers survive process death.
 * The Orchard balance itself is faked via [MockOrchardBalanceRepository] (independent of the real
 * wallet balance) so it can actually be depleted as mocked transfers execute.
 * Replace with the real Rust-bridge implementation once the SDK is ready.
 */
class OrchardMigrationSdkMock(
    private val mockBalanceRepository: MockOrchardBalanceRepository,
    private val repository: MigrationPlanRepository,
) : OrchardMigrationSdk {

    // ── State ────────────────────────────────────────────────────────────────

    override fun getMigrationState(): MigrationState {
        val plan = runCatching { runBlocking { repository.load() } }.getOrNull()
        return when {
            plan == null -> MigrationState.NotStarted
            plan.isComplete -> MigrationState.Complete
            else -> MigrationState.InProgress(buildProgress(plan))
        }
    }

    override fun getMigrationProgress(): MigrationProgress? {
        val plan = runCatching { runBlocking { repository.load() } }.getOrNull() ?: return null
        return buildProgress(plan)
    }

    private fun buildProgress(plan: MigrationPlan): MigrationProgress {
        val remaining = plan.transfers
            .filter { it.status != MigrationTransferStatus.SENT }
            .sumOf { it.amountZatoshi }
        return MigrationProgress(
            completedTransfers = plan.completedCount,
            totalTransfers = plan.totalCount,
            remainingOrchardZatoshi = remaining,
            nextTransferReadyAtHeight = plan.nextPending?.scheduledAtEpochSeconds,
        )
    }

    // ── Note splitting ───────────────────────────────────────────────────────

    override fun isNoteSplitNeeded(): Boolean = true

    override suspend fun prepareNoteSplit(): NoteSplitProposal {
        val total = orchardBalance()
        return NoteSplitProposal(
            outputNotes = splitEvenly(total, count = 4),
            fee = 1_000L,
        )
    }

    override suspend fun submitNoteSplit(proposal: NoteSplitProposal): TransferResult {
        Twig.debug { "OrchardMigrationSdkMock: mock note split submitted" }
        delay(400)
        mockBalanceRepository.decrease(proposal.fee)
        return TransferResult.Success("mock_split_${System.currentTimeMillis()}")
    }

    // ── Migration proposal ───────────────────────────────────────────────────

    override suspend fun proposeMigrationTransfers(): MigrationSchedule {
        val total = orchardBalance()
        val amounts = splitEvenly(total, count = TRANSFER_COUNT)
        val intervalSeconds = if (BuildConfig.DEBUG) DEBUG_INTERVAL_SECONDS else PROD_INTERVAL_SECONDS
        val nowSeconds = Clock.System.now().epochSeconds

        val transfers = amounts.mapIndexed { i, amount ->
            TransferProposal(
                id = "transfer_$i",
                amountZatoshi = amount,
                // Nearest interval boundary (mock proxy for 288-block anchor bucket)
                anchorHeight = (nowSeconds / intervalSeconds) * intervalSeconds,
                // Each transfer is one interval after the previous
                nextExecutableAfterHeight = nowSeconds + (i * intervalSeconds),
                expiryHeight = nowSeconds + ((i + 2) * intervalSeconds),
            )
        }
        return MigrationSchedule(
            transfers = transfers,
            estimatedDurationHours = ((TRANSFER_COUNT - 1) * intervalSeconds / 3600L).toInt(),
        )
    }

    // signAndStoreMigrationSchedule: SDK perspective (signing). Persistence is handled
    // separately by MigrationSetupVM via MigrationPlanRepository.
    override suspend fun signAndStoreMigrationSchedule(schedule: MigrationSchedule) {
        Twig.debug { "OrchardMigrationSdkMock: schedule signed (${schedule.transfers.size} transfers)" }
    }

    // ── Background execution ─────────────────────────────────────────────────

    override fun isSyncRequiredBeforeNextTransfer(): Boolean = false

    override suspend fun executeNextPendingTransfer(options: NetworkPrivacyOptions): TransferResult? {
        val plan = repository.load() ?: return null
        val next = plan.nextPending ?: return null

        Twig.debug { "OrchardMigrationSdkMock: mock-sending transfer ${next.index + 1}/${plan.totalCount} (tor=${options.useTor})" }
        delay(200)

        repository.updateTransfer(next.index, MigrationTransferStatus.SENT)
        mockBalanceRepository.decrease(next.amountZatoshi)
        return TransferResult.Success("mock_tx_${System.currentTimeMillis()}")
    }

    // ── On-launch reconciliation ─────────────────────────────────────────────

    override fun hasOverdueTransfers(): Boolean {
        val plan = runCatching { runBlocking { repository.load() } }.getOrNull() ?: return false
        if (plan.isComplete) return false
        val next = plan.nextPending ?: return false
        return next.scheduledAt <= Clock.System.now()
    }

    override fun hasInvalidTransfers(): Boolean = false

    // ── Invalidity recovery ──────────────────────────────────────────────────

    override suspend fun restartCurrentMigrationStep(): MigrationSchedule = proposeMigrationTransfers()

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun initializePostUpgrade() {
        Twig.debug { "OrchardMigrationSdkMock: initializePostUpgrade (no-op in mock)" }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun orchardBalance(): Long = mockBalanceRepository.get()

    private fun splitEvenly(total: Long, count: Int): List<Long> {
        if (total <= 0L || count <= 0) return List(count) { 0L }
        val base = total / count
        val remainder = total % count
        val amounts = MutableList(count) { i -> if (i < remainder) base + 1L else base }
        // Small jitter (±5% of base) so amounts look organic in the UI
        val jitter = (base * 0.05).toLong().coerceAtLeast(1L)
        for (i in 0 until count - 1) {
            val shift = Random.nextLong(-jitter, jitter)
            amounts[i] += shift
            amounts[i + 1] -= shift
        }
        return amounts
    }

    companion object {
        private const val TRANSFER_COUNT = 3
        private const val DEBUG_INTERVAL_SECONDS = 30L
        private const val PROD_INTERVAL_SECONDS = 6 * 3600L
    }
}
