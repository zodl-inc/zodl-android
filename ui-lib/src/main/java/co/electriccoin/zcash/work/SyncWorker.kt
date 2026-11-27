package co.electriccoin.zcash.work

import android.content.Context
import androidx.annotation.Keep
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.PercentDecimal
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.migration.MigrationGate
import co.electriccoin.zcash.ui.common.provider.LastNetworkActivityStorageProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

// TODO [#1249]: Add documentation and tests on background syncing
// TODO [#1249]: https://github.com/Electric-Coin-Company/zashi-android/issues/1249
@Keep
class SyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters),
    KoinComponent {
    private val synchronizerProvider: SynchronizerProvider by inject()
    private val migrationGate: MigrationGate by inject()
    private val lastNetworkActivity: LastNetworkActivityStorageProvider by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        if (migrationGate.isMigrationActive()) {
            Twig.debug { "BG Sync: migration active — Lane A supersedes, skipping." }
            return Result.success()
        }

        Twig.debug { "BG Sync: starting..." }

        synchronizerProvider.synchronizer
            .flatMapLatest {
                Twig.debug { "BG Sync: synchronizer: $it" }

                it?.status?.combine(it.progress) { status, progress ->
                    StatusAndProgress(status, progress).also {
                        Twig.debug { "BG Sync: result: $it" }
                    }
                } ?: emptyFlow()
            }.takeWhile {
                it.status != Synchronizer.Status.DISCONNECTED &&
                    it.status != Synchronizer.Status.SYNCED
            }.collect()

        Twig.debug { "BG Sync: terminating..." }

        lastNetworkActivity.stampNow()
        return Result.success()
    }

    companion object {
        private val SYNC_PERIOD = 24.hours
        private val SYNC_DAY_SHIFT = 1.days // Move to tomorrow
        private val SYNC_START_TIME_HOURS = 3.hours // Start around 3 a.m. at night
        private val SYNC_START_TIME_MINUTES = 60.minutes // Randomize with minutes until 4 a.m.

        fun newWorkRequest(): PeriodicWorkRequest {
            val targetTimeDiff = calculateTargetTimeDifference()

            Twig.debug { "BG Sync: necessary trigger delay time: $targetTimeDiff" }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiresStorageNotLow(true)
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresCharging(true)
                    .build()

            // TODO [#1258]: Consider using flexInterval in BG sync trigger planning
            // TODO [#1258]: https://github.com/Electric-Coin-Company/zashi-android/issues/1258
            return PeriodicWorkRequestBuilder<SyncWorker>(SYNC_PERIOD.toJavaDuration())
                .setConstraints(constraints)
                .setInitialDelay(targetTimeDiff.toJavaDuration())
                .build()
        }

        private fun calculateTargetTimeDifference(): Duration {
            val currentTimeZone: TimeZone = TimeZone.currentSystemDefault()

            val now: Instant = Clock.System.now()

            val targetTime =
                now
                    .plus(SYNC_DAY_SHIFT)
                    .toLocalDateTime(currentTimeZone)
                    .date
                    .atTime(
                        hour = SYNC_START_TIME_HOURS.inWholeHours.toInt(),
                        // Even though the WorkManager will trigger the work approximately at the set time, it's
                        // better to randomize time in 3-4 a.m. This generates a number between 0 (inclusive) and 60
                        // (exclusive)
                        minute = Random.nextInt(0, SYNC_START_TIME_MINUTES.inWholeMinutes.toInt())
                    )

            Twig.debug { "BG Sync: calculated target time: ${targetTime.time}" }

            return now
                .until(
                    other = targetTime.toInstant(currentTimeZone),
                    unit = DateTimeUnit.MILLISECOND,
                    timeZone = currentTimeZone
                ).toDuration(DurationUnit.MILLISECONDS)
        }
    }
}

// Enhancement to this implementation would be returning a better status information
private data class StatusAndProgress(
    val status: Synchronizer.Status,
    val progress: PercentDecimal
)
