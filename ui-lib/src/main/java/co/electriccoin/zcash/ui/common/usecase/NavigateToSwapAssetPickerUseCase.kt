package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.screen.swap.picker.SwapAssetPickerArgs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription

class NavigateToSwapAssetPickerUseCase(
    private val navigationRouter: NavigationRouter
) {
    private val pipeline = MutableSharedFlow<SelectSwapAssetPipelineResult>()

    suspend operator fun invoke(onlyChainTicker: String?, nearOnly: Boolean = false): SwapAsset? {
        val args = SwapAssetPickerArgs(onlyChainTicker = onlyChainTicker, nearOnly = nearOnly)
        // Subscribe before forwarding so a result emitted as soon as the screen appears can never be
        // dropped (a bare SharedFlow emit with no collector is lost and would hang the caller).
        val result =
            pipeline
                .onSubscription { navigationRouter.forward(args) }
                .first { it.args.requestId == args.requestId }
        return when (result) {
            is SelectSwapAssetPipelineResult.Cancelled -> null
            is SelectSwapAssetPipelineResult.Selected -> result.asset
        }
    }

    suspend fun onSelectionCancelled(args: SwapAssetPickerArgs) {
        pipeline.emit(SelectSwapAssetPipelineResult.Cancelled(args))
        navigationRouter.back()
    }

    suspend fun onSelected(asset: SwapAsset, args: SwapAssetPickerArgs) {
        pipeline.emit(SelectSwapAssetPipelineResult.Selected(asset = asset, args = args))
        navigationRouter.back()
    }
}

private sealed interface SelectSwapAssetPipelineResult {
    val args: SwapAssetPickerArgs

    data class Cancelled(
        override val args: SwapAssetPickerArgs
    ) : SelectSwapAssetPipelineResult

    data class Selected(
        val asset: SwapAsset,
        override val args: SwapAssetPickerArgs
    ) : SelectSwapAssetPipelineResult
}
