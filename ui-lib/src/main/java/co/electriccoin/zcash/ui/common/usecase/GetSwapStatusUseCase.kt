package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class GetSwapStatusUseCase(
    private val metadataRepository: MetadataRepository,
    private val swapRepository: SwapRepository,
) {
    suspend operator fun invoke(depositAddress: String) = observe(depositAddress).first { !it.isLoading }

    @Suppress("TooGenericExceptionCaught", "LoopWithTooManyJumpStatements")
    fun observe(depositAddress: String): Flow<SwapQuoteStatusData> =
        channelFlow {
            val data = MutableStateFlow(SwapQuoteStatusData())

            launch {
                data.collect { send(it) }
            }

            launch {
                val expectedMetadata = metadataRepository.getSwapMetadata(depositAddress)

                if (expectedMetadata == null) {
                    data.update {
                        it.copy(
                            isLoading = false,
                            error = IllegalStateException("No metadata for deposit addr")
                        )
                    }
                    return@launch
                }

                while (true) {
                    try {
                        val result = swapRepository.checkSwapStatus(expectedMetadata)
                        metadataRepository.updateSwap(
                            depositAddress = depositAddress,
                            amountOutFormatted = result.amountOutFormatted,
                            status = result.status,
                            mode = result.mode,
                            origin = result.originAsset,
                            destination = result.destinationAsset
                        )
                        data.update {
                            it.copy(
                                status = result,
                                isLoading = false,
                                error = null
                            )
                        }
                        if (result.status.isTerminal) {
                            break
                        }
                    } catch (e: Exception) {
                        data.update { it.copy(isLoading = false, error = e) }
                        break
                    }
                    delay(30.seconds)
                }
            }

            awaitClose()
        }
}

data class SwapQuoteStatusData(
    val status: SwapQuoteStatus? = null,
    val isLoading: Boolean = true,
    val error: Exception? = null,
) {
    val originAsset = status?.originAsset
    val destinationAsset = status?.destinationAsset
}
