package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.PreparationStep
import cash.z.ecc.android.sdk.TransferProposal
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.work.MigrationScheduler
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class FinalizeMigrationScheduleUseCaseTest {
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

    private fun useCase(scheduler: MigrationScheduler = mockk(relaxed = true)) =
        FinalizeMigrationScheduleUseCase(
            migrationScheduler = scheduler,
            getOrchardMigrationSdk = mockk<GetOrchardMigrationSdkUseCase>(relaxed = true),
            getSelectedWalletAccount =
                mockk<GetSelectedWalletAccountUseCase> {
                    coEvery { this@mockk() } returns mockk<ZodlAccount>(relaxed = true)
                },
            synchronizerProvider = mockk(relaxed = true),
            migrationLiveDriver = mockk(relaxed = true),
        )

    @Test
    fun invokeArmsTheMigrationWorkerChain() =
        runTest {
            // The single worker chain is armed at commit; its first run asks the engine
            // (nextStep/syncWakeupSchedule) and computes every subsequent wake itself. Nothing is
            // persisted app-side anymore — the engine's committed state is the only record.
            val scheduler = mockk<MigrationScheduler>(relaxed = true)

            useCase(scheduler)(schedule(), MigrationMode.AUTOMATIC)

            verify(exactly = 1) { scheduler.schedule(any(), any()) }
        }

    @Test
    fun `delay targets earliest step including preparations`() {
        val sched =
            MigrationSchedule(
                transfers =
                    listOf(
                        TransferProposal(
                            id = 4,
                            amountZatoshi = 1,
                            anchorHeight = 4219036,
                            nextExecutableAfterHeight = 4219108,
                            expiryHeight = 9_999_999
                        )
                    ),
                preparations =
                    listOf(
                        PreparationStep(
                            id = 1,
                            layer = 0,
                            index = 1,
                            broadcastHeight = 4219043,
                            dependsOn = emptyList()
                        )
                    ),
                estimatedDurationHours = 1,
                proposalHandle = 1,
            )
        val d =
            useCase().delayUntilFirstStep(
                sched,
                secondsPerBlock = 28,
                nu7ActivationHeight = null,
                tipHeight = 4219036
            )
        assertEquals((7 * 28).seconds, d) // 4219043-4219036 = 7 blocks
    }

    // ── NU7 characterization pins (MOB-2287): pre-NU7 behaviour at the 75 s/block rate. ──

    @Test
    fun `characterization pin of pre-NU7 75s - delay is block delta times 75 for a transfer-only schedule`() {
        // NU7: revisit - spans activation once helper is height-aware
        // schedule(): anchor 100, nextExecutableAfter 200 -> 100 blocks * 75 s.
        val d =
            useCase().delayUntilFirstStep(
                schedule(),
                secondsPerBlock = 75L,
                nu7ActivationHeight = null,
                tipHeight = 100L
            )
        assertEquals((100 * 75).seconds, d)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - preparation before the first transfer wins`() {
        // NU7: revisit - spans activation once helper is height-aware
        val sched =
            schedule().copy(
                preparations =
                    listOf(
                        PreparationStep(id = 1, layer = 0, index = 0, broadcastHeight = 110L, dependsOn = emptyList())
                    )
            )
        val d =
            useCase().delayUntilFirstStep(
                sched,
                secondsPerBlock = 75L,
                nu7ActivationHeight = null,
                tipHeight = 100L
            )
        assertEquals((10 * 75).seconds, d)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - earliest step already due yields zero delay`() {
        // NU7: revisit - spans activation once helper is height-aware
        val d =
            useCase().delayUntilFirstStep(
                schedule(),
                secondsPerBlock = 75L,
                nu7ActivationHeight = null,
                tipHeight = 250L
            )
        assertEquals(0.seconds, d)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - empty schedule yields zero delay`() {
        val empty = MigrationSchedule(transfers = emptyList(), estimatedDurationHours = 0, proposalHandle = 0L)
        assertEquals(
            0.seconds,
            useCase().delayUntilFirstStep(
                empty,
                secondsPerBlock = 75L,
                nu7ActivationHeight = null,
                tipHeight = 0L
            )
        )
    }

    @Test
    fun `NU7 - delay splits at the activation height`() {
        // schedule(): anchor 100, first step at 200. Activation at 140: 39 blocks at 75 s, 61 at 25 s.
        val d =
            useCase().delayUntilFirstStep(
                schedule(),
                secondsPerBlock = 75L,
                tipHeight = 100L,
                nu7ActivationHeight = 140L,
            )
        assertEquals((39 * 75 + 61 * 25).seconds, d)
    }

    @Test
    fun `NU7 - delay after activation uses the post-NU7 rate`() {
        val d =
            useCase().delayUntilFirstStep(
                schedule(),
                secondsPerBlock = 25L,
                tipHeight = 100L,
                nu7ActivationHeight = 50L,
            )
        assertEquals((100 * 25).seconds, d)
    }
}
