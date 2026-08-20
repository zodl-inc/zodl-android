package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.PreparationStep
import cash.z.ecc.android.sdk.TransferProposal
import co.electriccoin.zcash.ui.common.model.ZashiAccount
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
                    coEvery { this@mockk() } returns mockk<ZashiAccount>(relaxed = true)
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
        val d = useCase().delayUntilFirstStep(sched, secondsPerBlock = 28, tipHeight = 4219036)
        assertEquals((7 * 28).seconds, d) // 4219043-4219036 = 7 blocks
    }
}
