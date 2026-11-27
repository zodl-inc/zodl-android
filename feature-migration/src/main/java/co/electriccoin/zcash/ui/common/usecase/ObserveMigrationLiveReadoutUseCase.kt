package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.MigrationLiveReadout
import co.electriccoin.zcash.ui.common.repository.MigrationTransferStateRepository
import co.electriccoin.zcash.ui.common.repository.readUnreconciledLiveReadout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

/**
 * The shared shape for every screen-level display-only migration readout: replays
 * [MigrationTransferStateRepository]'s per-account cache (published by the live driver's own loop),
 * and falls back to one direct, mutex-free SDK read ([readUnreconciledLiveReadout]) whenever the
 * cache is still `null` for that account this session (before the driver's first publish, or if no
 * migration is `in_progress` for that account so the driver never runs at all).
 *
 * Extracted 2026-08-07 from two near-identical private implementations (Home banner, Progress
 * screen) that were at risk of drifting apart — no behavior change from either original. See
 * [MigrationTransferStateRepository]'s own kdoc for why the repository can legitimately stay `null`,
 * and [readUnreconciledLiveReadout]'s kdoc for why the fallback never takes
 * `MIGRATION_DB_ACCESS_MUTEX`.
 *
 * The internal ticker re-fires the combine on every beat even when the cached value hasn't changed,
 * so callers whose downstream rendering depends on wall-clock comparisons (e.g. "has this transfer's
 * due time now arrived") keep re-evaluating — a tick against a non-null cached value is a pure
 * re-emit (no extra SDK call); only a still-`null` cache re-reads the SDK on every tick.
 */
class ObserveMigrationLiveReadoutUseCase(
    private val migrationTransferStateRepository: MigrationTransferStateRepository,
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
) {
    operator fun invoke(accountKeyId: String): Flow<MigrationLiveReadout?> =
        combine(migrationTransferStateRepository.observe(accountKeyId), recheckTicker()) { published, _ -> published }
            .map { published -> published ?: fetchFreshReadout() }
            .flowOn(Dispatchers.Default)

    private suspend fun fetchFreshReadout(): MigrationLiveReadout? =
        runCatching { getOrchardMigrationSdk().readUnreconciledLiveReadout() }.getOrNull()

    private fun recheckTicker(): Flow<Unit> =
        flow {
            while (true) {
                emit(Unit)
                delay(RECHECK_INTERVAL)
            }
        }

    private companion object {
        val RECHECK_INTERVAL = 15.seconds
    }
}
