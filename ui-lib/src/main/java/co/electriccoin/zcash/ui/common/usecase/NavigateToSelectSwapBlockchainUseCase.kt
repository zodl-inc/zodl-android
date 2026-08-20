package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.screen.swap.picker.SwapBlockchainPickerArgs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription

class NavigateToSelectSwapBlockchainUseCase(
    private val navigationRouter: NavigationRouter
) {
    private val pipeline = MutableSharedFlow<SelectSwapBlockchainPipelineResult>()

    suspend operator fun invoke(): SwapBlockchain? {
        val args = SwapBlockchainPickerArgs()
        // Subscribe before forwarding so a result emitted as soon as the screen appears can never be
        // dropped (a bare SharedFlow emit with no collector is lost and would hang the caller).
        val result =
            pipeline
                .onSubscription { navigationRouter.forward(args) }
                .first { it.args.requestId == args.requestId }
        return when (result) {
            is SelectSwapBlockchainPipelineResult.Cancelled -> null
            is SelectSwapBlockchainPipelineResult.Scanned -> result.blockchain
        }
    }

    suspend fun onSelectionCancelled(args: SwapBlockchainPickerArgs) {
        pipeline.emit(SelectSwapBlockchainPipelineResult.Cancelled(args))
        navigationRouter.back()
    }

    suspend fun onSelected(blockchain: SwapBlockchain, args: SwapBlockchainPickerArgs) {
        pipeline.emit(
            SelectSwapBlockchainPipelineResult.Scanned(
                blockchain = blockchain,
                args = args
            )
        )
        navigationRouter.back()
    }
}

private sealed interface SelectSwapBlockchainPipelineResult {
    val args: SwapBlockchainPickerArgs

    data class Cancelled(
        override val args: SwapBlockchainPickerArgs
    ) : SelectSwapBlockchainPipelineResult

    data class Scanned(
        val blockchain: SwapBlockchain,
        override val args: SwapBlockchainPickerArgs
    ) : SelectSwapBlockchainPipelineResult
}
