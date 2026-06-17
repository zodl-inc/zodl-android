package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.datasource.TokenNotFoundException
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.near.requireMatchingAsset
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class GetSwapStatusUseCase(
    private val swapDataSource: SwapDataSource,
    private val metadataRepository: MetadataRepository,
    private val swapRepository: SwapRepository,
) {
    suspend operator fun invoke(depositAddress: String) = observe(depositAddress).first { !it.isLoading }

    @Suppress("TooGenericExceptionCaught", "LoopWithTooManyJumpStatements")
    fun observe(depositAddress: String): Flow<SwapQuoteStatusData> {
        return channelFlow {
            val data = MutableStateFlow(SwapQuoteStatusData())

            launch {
                data.collect { send(it) }
            }

            launch {
                val alreadyLoadedAssets = swapRepository.assets.value
                val supportedAssets =
                    if (alreadyLoadedAssets.data != null && alreadyLoadedAssets.zecAsset != null) {
                        alreadyLoadedAssets.data + alreadyLoadedAssets.zecAsset
                    } else {
                        swapRepository.requestRefreshAssetsOnce()
                        val newAssets = swapRepository.assets.firstOrNull { !it.isLoading }

                        if (newAssets?.data == null || newAssets.zecAsset == null) {
                            data.update { it.copy(isLoading = false, error = newAssets?.error) }
                            return@launch
                        } else {
                            newAssets.data + newAssets.zecAsset
                        }
                    }

                val expectedMetadata = metadataRepository.getSwapMetadata(depositAddress)

                while (true) {
                    try {
                        val result = swapDataSource.checkSwapStatus(depositAddress, supportedAssets)
                        expectedMetadata?.origin?.let {
                            requireMatchingAsset(
                                name = "origin",
                                expectedTokenTicker = it.tokenTicker,
                                expectedChainTicker = it.chainTicker,
                                actual = result.quote.originAsset
                            )
                        }
                        expectedMetadata?.destination?.let {
                            requireMatchingAsset(
                                name = "destination",
                                expectedTokenTicker = it.tokenTicker,
                                expectedChainTicker = it.chainTicker,
                                actual = result.quote.destinationAsset
                            )
                        }
                        metadataRepository.updateSwap(
                            depositAddress = depositAddress,
                            amountOutFormatted = result.amountOutFormatted,
                            status = result.status,
                            mode = result.mode,
                            origin = result.quote.originAsset,
                            destination = result.quote.destinationAsset
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
                    } catch (e: TokenNotFoundException) {
                        data.update {
                            it.copy(
                                isLoading = false,
                                error = e
                            )
                        }
                        break
                    } catch (e: Exception) {
                        data.update { it.copy(isLoading = false, error = e) }
                        break
                    }
                    delay(30.seconds)
                }
            }

            awaitClose {
                // do nothing
            }
        }
    }
}

data class SwapQuoteStatusData(
    val status: SwapQuoteStatus? = null,
    val isLoading: Boolean = true,
    val error: Exception? = null,
) {
    val originAsset = status?.quote?.originAsset
    val destinationAsset = status?.quote?.destinationAsset
}
