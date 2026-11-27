package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.toomanynotes.TooManyNotesArgs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription

/** How the user left the too-many-notes destination. */
enum class TooManyNotesOutcome {
    /** OK: the caller clears its amount and focuses the amount field. */
    OK,

    /** Back, scrim or drag: the caller leaves its form as it is. */
    DISMISSED
}

/**
 * Shows the too-many-notes sheet as a destination and returns how the user left it to the caller, which owns the
 * form underneath (keyed pipeline, like [NavigateToSlippageUseCase]). Only the caller that opened the destination
 * receives the outcome, so a form deeper in the back stack is never touched.
 */
class NavigateToTooManyNotesUseCase(
    private val navigationRouter: NavigationRouter
) {
    private val pipeline = MutableSharedFlow<TooManyNotesPipelineResult>(extraBufferCapacity = 1)

    suspend operator fun invoke(): TooManyNotesOutcome {
        val args = TooManyNotesArgs()
        // Subscribe before forwarding so an outcome emitted as soon as the screen appears can never be dropped.
        return pipeline
            .onSubscription { navigationRouter.forward(args) }
            .first { it.args.requestId == args.requestId }
            .outcome
    }

    fun onOk(args: TooManyNotesArgs) = answer(TooManyNotesOutcome.OK, args)

    fun onDismissed(args: TooManyNotesArgs) = answer(TooManyNotesOutcome.DISMISSED, args)

    /** The destination went away without an answer (process-level back, scope cleared): the caller must not hang. */
    fun onAbandoned(args: TooManyNotesArgs) {
        pipeline.tryEmit(TooManyNotesPipelineResult(TooManyNotesOutcome.DISMISSED, args))
    }

    private fun answer(outcome: TooManyNotesOutcome, args: TooManyNotesArgs) {
        pipeline.tryEmit(TooManyNotesPipelineResult(outcome, args))
        navigationRouter.back()
    }
}

private data class TooManyNotesPipelineResult(
    val outcome: TooManyNotesOutcome,
    val args: TooManyNotesArgs
)
