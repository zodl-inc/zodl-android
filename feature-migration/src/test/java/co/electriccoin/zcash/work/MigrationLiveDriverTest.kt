package co.electriccoin.zcash.work

import cash.z.ecc.android.sdk.OrchardMigrationSdk
import co.electriccoin.zcash.ui.common.repository.MigrationTransferStateRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationLiveDriverTest {
    @Test
    fun `starting twice for the same account only ever runs one loop`() =
        runTest {
            var runCallCount = 0
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        runCallCount++
                        DriveOnceResult.Terminal // stops the loop on the very first call
                    }
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = mockk(relaxed = true),
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            driver.startIfNotRunning("account-1")
            advanceUntilIdle()

            assertEquals(
                1,
                runCallCount,
                "a second start for the SAME account while the first is still active must be a no-op",
            )
        }

    @Test
    fun `the loop re-arms using the ReArmed delay, and stops on Terminal`() =
        runTest {
            var runCallCount = 0
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        runCallCount++
                        if (runCallCount < 3) DriveOnceResult.ReArmed(90.seconds) else DriveOnceResult.Terminal
                    }
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = mockk(relaxed = true),
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            advanceUntilIdle()

            assertEquals(
                3,
                runCallCount,
                "the loop must call run() again after each returned delay, and stop on Terminal",
            )
        }

    @Test
    fun `a floorless ReArmed delay is floored, not spun on tightly`() =
        runTest {
            var runCallCount = 0
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        runCallCount++
                        // 0 seconds is exactly what nextWake's floorless privacy-gap term can
                        // return — the loop must not spin on this.
                        if (runCallCount < 3) {
                            DriveOnceResult.ReArmed(kotlin.time.Duration.ZERO)
                        } else {
                            DriveOnceResult.Terminal
                        }
                    }
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = mockk(relaxed = true),
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            // Advance by less than 2x the floor — if the loop weren't flooring, all 3 calls would
            // already be done; if it IS flooring at 60s, at most one call should have happened by
            // t=1s.
            advanceTimeBy(1_000)
            assertEquals(1, runCallCount, "the loop must floor a zero/near-zero ReArmed delay, not spin")
            advanceUntilIdle()
            assertEquals(3, runCallCount)
        }

    @Test
    fun `an unfloored ReArmed delay (respectAntiSpinFloor=false) is NOT floored, chains back-to-back`() =
        runTest {
            var runCallCount = 0
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        runCallCount++
                        // A deliberate short constant (e.g. PREP_FAST_TRACK_REARM) opts out of the
                        // anti-spin floor — the loop must honor that and chain back-to-back.
                        if (runCallCount < 3) {
                            DriveOnceResult.ReArmed(1.seconds, respectAntiSpinFloor = false)
                        } else {
                            DriveOnceResult.Terminal
                        }
                    }
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = mockk(relaxed = true),
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            // Advance by far less than the 60s floor — if the loop were flooring this, at most one
            // call would have happened by now; all 3 should complete well before the floor.
            advanceTimeBy(5_000)
            advanceUntilIdle()

            assertEquals(
                3,
                runCallCount,
                "respectAntiSpinFloor=false must NOT be floored — the loop must chain back-to-back",
            )
        }

    @Test
    fun `LockBusy waits the given retry delay and tries again`() =
        runTest {
            var runCallCount = 0
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        runCallCount++
                        if (runCallCount < 2) DriveOnceResult.LockBusy(5.seconds) else DriveOnceResult.Terminal
                    }
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = mockk(relaxed = true),
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            advanceUntilIdle()

            assertEquals(2, runCallCount, "LockBusy must retry using its own retryDelay, not the floored ReArmed floor")
        }

    @Test
    fun `a step that actually ran publishes fresh transfer states, LockBusy does not`() =
        runTest {
            var runCallCount = 0
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        runCallCount++
                        when (runCallCount) {
                            1 -> DriveOnceResult.LockBusy(5.seconds)

                            // must NOT publish
                            2 -> DriveOnceResult.ReArmed(1.seconds)

                            // must publish
                            else -> DriveOnceResult.Terminal // must also publish
                        }
                    }
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val repository = mockk<MigrationTransferStateRepository>(relaxed = true)
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = repository,
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            advanceUntilIdle()

            // +1 for the priming publish at loop start (before the first driveOnce.run() call,
            // 2026-08-0X) on top of the 2 step-driven publishes (ReArmed, Terminal — LockBusy still
            // does not publish).
            verify(exactly = 3) { repository.publish("account-1", any()) }
        }

    @Test
    fun `the priming publish happens before the first driveOnce_run call`() =
        runTest {
            // 2026-08-0X: the whole point of the priming publish is to populate the cache BEFORE
            // the (potentially many-seconds-long) first driveOnce.run() call — verify the ordering
            // directly, not just the total count (which the other publish-count tests already
            // cover).
            val callOrder = mutableListOf<String>()
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        callOrder.add("run")
                        DriveOnceResult.Terminal
                    }
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val repository =
                mockk<MigrationTransferStateRepository>(relaxed = true) {
                    every { publish(any(), any()) } answers { callOrder.add("publish") }
                }
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = repository,
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            advanceUntilIdle()

            assertEquals(
                listOf("publish", "run", "publish"),
                callOrder,
                "priming publish, then the first run() call, then Terminal's own publish"
            )
        }

    @Test
    fun `a failed publish read does not kill the loop`() =
        runTest {
            var runCallCount = 0
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        runCallCount++
                        if (runCallCount < 3) DriveOnceResult.ReArmed(1.seconds) else DriveOnceResult.Terminal
                    }
                }
            // Every read throws — mirrors a "database is locked" failure outlasting the SDK's own
            // bounded retry (loggedRetryLoop rethrows once exhausted; observed live). Before the
            // 2026-08-06 Fable-review fix, this escaped to the loop's outer catch and stopped the
            // whole live-driver loop mid-migration.
            val sdk =
                mockk<OrchardMigrationSdk> {
                    coEvery { getMigrationTransferStates() } throws IllegalStateException("database is locked")
                }
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = mockk(relaxed = true),
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            advanceUntilIdle()

            assertEquals(
                3,
                runCallCount,
                "a failed publish read must not stop the loop — it must keep driving to Terminal",
            )
        }

    @Test
    fun `a long re-armed wait republishes periodically instead of going stale for its whole span`() =
        runTest {
            var runCallCount = 0
            val driveOnce =
                mockk<MigrationDriveOnce> {
                    coEvery { run(any(), any(), any(), any()) } coAnswers {
                        runCallCount++
                        // 250s is comfortably more than 4x the 60s staleness-refresh interval, and
                        // ends the loop right after — Terminal itself never triggers a periodic
                        // refresh (there's no wait to refresh during).
                        if (runCallCount < 2) DriveOnceResult.ReArmed(250.seconds) else DriveOnceResult.Terminal
                    }
                }
            val sdk = mockk<OrchardMigrationSdk>(relaxed = true)
            val repository = mockk<MigrationTransferStateRepository>(relaxed = true)
            val driver =
                MigrationLiveDriverImpl(
                    migrationDriveOnce = driveOnce,
                    getOrchardMigrationSdk = { sdk },
                    migrationTransferStateRepository = repository,
                    scope = this,
                )

            driver.startIfNotRunning("account-1")
            advanceUntilIdle()

            // One priming publish at loop start (2026-08-0X, before the first driveOnce.run()),
            // one publish for the ReArmed call itself, then one more every 60s across the 250s
            // wait (at 60/120/180/240s — 4 periodic refreshes), then one for the Terminal call:
            // 1 + 1 + 4 + 1 = 7. The old single delay() would have left the repository stale for
            // the whole 250s span instead.
            verify(exactly = 7) { repository.publish("account-1", any()) }
        }
}
