package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MessageAvailabilityDataSource
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Shielding outcome routing (MOB-1145): shielding has no pending screen, so every non-success
 * result routes to the error screen - including Partial (which must not be silently ignored) and a
 * resubmittable GrpcFailure - while a full success shows no error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShieldFundsUseCaseTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun partialResultRoutesToError() =
        runTest(dispatcher) {
            val result = SubmitResult.Partial(txIds = listOf("a", "b"), statuses = listOf("success", "notAttempted"))
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

            useCase(submitResult = result, navigateToError = navigateToError)(false)
            advanceUntilIdle()

            verify(exactly = 1) { navigateToError(ErrorArgs.ShieldingError(result), any()) }
        }

    @Test
    fun grpcFailureRoutesToError() =
        runTest(dispatcher) {
            val result = SubmitResult.GrpcFailure(txIds = listOf("a"))
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

            useCase(submitResult = result, navigateToError = navigateToError)(false)
            advanceUntilIdle()

            verify(exactly = 1) { navigateToError(ErrorArgs.ShieldingError(result), any()) }
        }

    @Test
    fun successDoesNotRouteToError() =
        runTest(dispatcher) {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

            useCase(submitResult = SubmitResult.Success(txIds = listOf("a")), navigateToError = navigateToError)(false)
            advanceUntilIdle()

            verify(exactly = 0) { navigateToError(any(), any()) }
        }

    private fun useCase(
        submitResult: SubmitResult,
        navigateToError: NavigateToErrorUseCase
    ) = ShieldFundsUseCase(
        keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true),
        zashiProposalRepository =
            mockk<ZashiProposalRepository>(relaxed = true) { coEvery { submit() } returns submitResult },
        navigationRouter = mockk<NavigationRouter>(relaxed = true),
        accountDataSource =
            mockk<AccountDataSource> { coEvery { getSelectedAccount() } returns mockk<ZashiAccount>() },
        navigateToError = navigateToError,
        messageAvailabilityDataSource = mockk<MessageAvailabilityDataSource>(relaxed = true)
    )
}
