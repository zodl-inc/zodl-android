package co.electriccoin.zcash.ui.screen.migration.restart

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationTransfer
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationSnapshotUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.RestartMigrationUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import co.electriccoin.zcash.ui.design.R as DesignR

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationRestartVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nextOpensConfirmationAndConfirmRunsUseCaseAndNavigatesBack() =
        runTest {
            val restart = mockk<RestartMigrationUseCase>(relaxed = true)
            val router = mockk<NavigationRouter>(relaxed = true)
            val vm =
                MigrationRestartVM(
                    restartMigration = restart,
                    getMigrationSnapshot = fakeSnapshot(completed = 7, total = 11),
                    getOrchardBalance = fakeBalance(Zatoshi(307_000_000)),
                    navigationRouter = router,
                    errorStateMapper = fakeErrorMapper(),
                )

            // Load completes -> content present, no dialog.
            val loaded = vm.state.first { !it.isLoading }
            assertNull(loaded.content?.confirmationDialog)

            // Next opens the confirmation sheet.
            loaded.content
                ?.nextButton
                ?.onClick
                ?.invoke()
            val withDialog = vm.state.first { it.content?.confirmationDialog != null }
            val dialog = requireNotNull(withDialog.content?.confirmationDialog)

            // The confirmation copy is built from restartMigration_confirmMessage with the
            // remaining-balance StringResource and completed-count args (nested StringResource
            // format path) — see MigrationRestartVM.onNextClicked.
            val title = assertIs<StringResource.ByResource>(dialog.title)
            assertEquals(DesignR.string.restartMigration_confirmTitle, title.resource)
            val message = assertIs<StringResource.ByResource>(dialog.message)
            assertEquals(DesignR.string.restartMigration_confirmMessage, message.resource)
            assertContentEquals(listOf(stringRes(Zatoshi(307_000_000)), 7), message.args)

            // Confirm restart runs the use case and pops back.
            dialog.primaryAction.onClick()
            advanceUntilIdle()
            coVerify { restart.invoke() }
            verify { router.back() }
        }

    private fun fakeSnapshot(
        completed: Int,
        total: Int,
    ): GetMigrationSnapshotUseCase {
        val transfers =
            (0 until total).map { index ->
                LiveMigrationTransfer(
                    id = index.toLong(),
                    index = index,
                    amountZatoshi = 0L,
                    scheduledHeight = 0L,
                    scheduledAt = Instant.fromEpochSeconds(0),
                    isSent = index < completed,
                    isProved = true,
                    action = null,
                    blocker = null,
                    expiryAt = null,
                    minedHeight = null,
                )
            }
        val snapshot = LiveMigrationSnapshot(transfers = transfers, preparations = emptyList(), tipHeight = 1_000L)
        return mockk { coEvery { this@mockk(null) } returns snapshot }
    }

    private fun fakeBalance(zatoshi: Zatoshi): GetOrchardBalanceUseCase =
        mockk { coEvery { this@mockk() } returns zatoshi }

    private fun fakeErrorMapper(): ErrorMapperUseCase = mockk(relaxed = true)
}
