@file:OptIn(ExperimentalSerializationApi::class)

package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.serialization.NearSwapStatusSerializer
import co.electriccoin.zcash.ui.common.serialization.NullableBigDecimalSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.math.BigDecimal

@JsonIgnoreUnknownKeys
@Serializable
data class SwapStatusResponseDto(
    @SerialName("quoteResponse")
    val quoteResponse: QuoteResponseDto,
    @SerialName("status")
    @Serializable(with = NearSwapStatusSerializer::class)
    val status: SwapStatus? = null,
    @SerialName("updatedAt")
    val updatedAt: String,
    @SerialName("swapDetails")
    val swapDetails: SwapDetails? = null
)

// @JsonIgnoreUnknownKeys
// @Serializable
// data class TransactionChainHash(
//     @SerialName("hash")
//     val hash: String,
//     @SerialName("explorerUrl")
//     val explorerUrl: String
// )

@JsonIgnoreUnknownKeys
@Serializable
data class SwapDetails(
    // @SerialName("intentHashes")
    // val intentHashes: List<String>,
    // @SerialName("nearTxHashes")
    // val nearTxHashes: List<String>,
    @SerialName("amountIn")
    @Serializable(NullableBigDecimalSerializer::class)
    val amountIn: BigDecimal? = null,
    @SerialName("amountInFormatted")
    @Serializable(NullableBigDecimalSerializer::class)
    val amountInFormatted: BigDecimal? = null,
    @SerialName("amountInUsd")
    @Serializable(NullableBigDecimalSerializer::class)
    val amountInUsd: BigDecimal? = null,
    @SerialName("amountOut")
    @Serializable(NullableBigDecimalSerializer::class)
    val amountOut: BigDecimal? = null,
    @SerialName("amountOutFormatted")
    @Serializable(NullableBigDecimalSerializer::class)
    val amountOutFormatted: BigDecimal? = null,
    @SerialName("amountOutUsd")
    @Serializable(NullableBigDecimalSerializer::class)
    val amountOutUsd: BigDecimal? = null,
    @SerialName("slippage")
    val slippage: Int? = null,
    // @SerialName("originChainTxHashes")
    // val originChainTxHashes: List<TransactionChainHash>,
    // @SerialName("destinationChainTxHashes")
    // val destinationChainTxHashes: List<TransactionChainHash>,
    @SerialName("refundedAmount")
    @Serializable(NullableBigDecimalSerializer::class)
    val refundedAmount: BigDecimal? = null,
    @SerialName("refundedAmountFormatted")
    @Serializable(NullableBigDecimalSerializer::class)
    val refundedAmountFormatted: BigDecimal? = null,
    @SerialName("refundedAmountUsd")
    val refundedAmountUsd: String? = null,
    @SerialName("depositedAmount")
    @Serializable(NullableBigDecimalSerializer::class)
    val depositedAmount: BigDecimal? = null,
    @SerialName("depositedAmountFormatted")
    @Serializable(NullableBigDecimalSerializer::class)
    val depositedAmountFormatted: BigDecimal? = null,
    @SerialName("depositedAmountUsd")
    @Serializable(NullableBigDecimalSerializer::class)
    val depositedAmountUsd: BigDecimal? = null
)

enum class SwapStatus(
    val apiValue: String
) {
    KNOWN_DEPOSIT_TX("KNOWN_DEPOSIT_TX"),
    PENDING_DEPOSIT("PENDING_DEPOSIT"),
    INCOMPLETE_DEPOSIT("INCOMPLETE_DEPOSIT"),
    PROCESSING("PROCESSING"),
    SUCCESS("SUCCESS"),
    REFUNDED("REFUNDED"),
    FAILED("FAILED")
}
