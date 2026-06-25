package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.screen.swap.slippage.SwapSlippageArgs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import java.math.BigDecimal

class NavigateToSlippageUseCase(
    private val navigationRouter: NavigationRouter
) {
    private val pipeline = MutableSharedFlow<SelectSlippagePipelineResult>()

    suspend operator fun invoke(
        currentSlippage: BigDecimal,
        fiatAmount: BigDecimal?,
        mode: SwapMode
    ): BigDecimal? {
        val args =
            SwapSlippageArgs(
                currentSlippage = currentSlippage.toPlainString(),
                fiatAmount = fiatAmount?.toPlainString(),
                mode = mode
            )
        // Subscribe before forwarding so a result emitted as soon as the screen appears can never be
        // dropped (a bare SharedFlow emit with no collector is lost and would hang the caller).
        val result =
            pipeline
                .onSubscription { navigationRouter.forward(args) }
                .first { it.args.requestId == args.requestId }
        return when (result) {
            is SelectSlippagePipelineResult.Cancelled -> null
            is SelectSlippagePipelineResult.Selected -> result.slippage
        }
    }

    suspend fun onSelectionCancelled(args: SwapSlippageArgs) {
        pipeline.emit(SelectSlippagePipelineResult.Cancelled(args))
        navigationRouter.back()
    }

    suspend fun onSelected(slippage: BigDecimal, args: SwapSlippageArgs) {
        pipeline.emit(SelectSlippagePipelineResult.Selected(slippage = slippage, args = args))
        navigationRouter.back()
    }
}

private sealed interface SelectSlippagePipelineResult {
    val args: SwapSlippageArgs

    data class Cancelled(
        override val args: SwapSlippageArgs
    ) : SelectSlippagePipelineResult

    data class Selected(
        val slippage: BigDecimal,
        override val args: SwapSlippageArgs
    ) : SelectSlippagePipelineResult
}
