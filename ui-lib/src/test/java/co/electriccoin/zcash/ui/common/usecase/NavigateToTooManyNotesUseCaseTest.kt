package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.toomanynotes.TooManyNotesArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * [NavigateToTooManyNotesUseCase] answers only the caller that opened the destination (requestId filter), with OK or
 * with a dismissal, and a destination that goes away unanswered resolves to a dismissal instead of hanging.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToTooManyNotesUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }
    private val useCase = NavigateToTooManyNotesUseCase(router)

    @Test
    fun okResolvesToOkAndLeavesTheDestination() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as TooManyNotesArgs

            useCase.onOk(args)

            assertEquals(TooManyNotesOutcome.OK, result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun dismissalResolvesToDismissed() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }

            useCase.onDismissed(forwarded.single() as TooManyNotesArgs)

            assertEquals(TooManyNotesOutcome.DISMISSED, result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun anAbandonedDestinationResolvesToDismissedWithoutNavigating() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }

            useCase.onAbandoned(forwarded.single() as TooManyNotesArgs)

            assertEquals(TooManyNotesOutcome.DISMISSED, result.await())
            verify(exactly = 0) { router.back() }
        }

    @Test
    fun anAnswerForAnotherRequestIsIgnored() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as TooManyNotesArgs

            useCase.onOk(TooManyNotesArgs())
            assertFalse(result.isCompleted)

            useCase.onDismissed(args)
            assertEquals(TooManyNotesOutcome.DISMISSED, result.await())
        }
}
