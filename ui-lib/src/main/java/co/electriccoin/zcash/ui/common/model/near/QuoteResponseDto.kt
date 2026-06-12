@file:OptIn(ExperimentalSerializationApi::class)

package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.serialization.BigDecimalSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.math.BigDecimal
import kotlin.time.Instant

@JsonIgnoreUnknownKeys
@Serializable
data class QuoteResponseDto(
    @SerialName("timestamp")
    val timestamp: Instant,
    @SerialName("quoteRequest")
    val quoteRequest: QuoteRequest,
    @SerialName("quote")
    val quote: QuoteDetails
)

@JsonIgnoreUnknownKeys
@Serializable
data class QuoteDetails(
    @SerialName("depositAddress")
    val depositAddress: String,
    @SerialName("amountIn")
    @Serializable(with = BigDecimalSerializer::class)
    val amountIn: BigDecimal,
    @SerialName("amountInFormatted")
    @Serializable(with = BigDecimalSerializer::class)
    val amountInFormatted: BigDecimal,
    @SerialName("amountInUsd")
    @Serializable(with = BigDecimalSerializer::class)
    val amountInUsd: BigDecimal,
    @SerialName("minAmountIn")
    @Serializable(with = BigDecimalSerializer::class)
    val minAmountIn: BigDecimal,
    @SerialName("amountOut")
    @Serializable(with = BigDecimalSerializer::class)
    val amountOut: BigDecimal,
    @SerialName("amountOutFormatted")
    @Serializable(with = BigDecimalSerializer::class)
    val amountOutFormatted: BigDecimal,
    @SerialName("amountOutUsd")
    @Serializable(with = BigDecimalSerializer::class)
    val amountOutUsd: BigDecimal,
    @SerialName("minAmountOut")
    @Serializable(with = BigDecimalSerializer::class)
    val minAmountOut: BigDecimal,
    @SerialName("deadline")
    val deadline: Instant
)
