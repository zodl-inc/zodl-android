package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.repository.TransactionSwapMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class GetReloadableSwapQuoteUseCase(
    private val getSwapStatus: GetSwapStatusUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(swapMetadata: TransactionSwapMetadata) =
        channelFlow {
            val requestSwipeReloadPipeline = MutableSharedFlow<Unit>()

            val reloadHandle =
                object : ReloadHandle {
                    override fun requestReload() {
                        launch {
                            requestSwipeReloadPipeline.emit(Unit)
                        }
                    }
                }

            val swapFlow =
                requestSwipeReloadPipeline
                    .onStart { emit(Unit) }
                    .flatMapLatest { getSwapStatus.observe(swapMetadata) }

            swapFlow
                .map { status ->
                    SwapData(
                        data = status,
                        handle = reloadHandle
                    )
                }.collect {
                    send(it)
                }

            awaitClose {
                // do nothing
            }
        }.distinctUntilChanged()
}

data class SwapData(
    val data: SwapQuoteStatusData,
    val handle: ReloadHandle
) {
    val status: SwapQuoteStatus? = data.status
    val isLoading: Boolean = data.isLoading
    val error: Exception? = data.error
}
