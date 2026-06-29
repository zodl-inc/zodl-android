@file:OptIn(ExperimentalSerializationApi::class)

package co.electriccoin.zcash.ui.common.model.swapkit

import co.electriccoin.zcash.ui.common.serialization.LenientBigDecimalSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.math.BigDecimal

// ---------------------------------------------------------------------------------------------------------
// GET /tokens?provider=MAYACHAIN
// ---------------------------------------------------------------------------------------------------------

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitTokensResponseDto(
    @SerialName("provider") val provider: String? = null,
    @SerialName("tokens") val tokens: List<SwapkitTokenDto> = emptyList(),
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitTokenDto(
    @SerialName("chain") val chain: String,
    @SerialName("chainId") val chainId: String? = null,
    @SerialName("ticker") val ticker: String? = null,
    @SerialName("identifier") val identifier: String,
    @SerialName("symbol") val symbol: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("decimals") val decimals: Int,
    @SerialName("logoURI") val logoURI: String? = null,
    @SerialName("coingeckoId") val coingeckoId: String? = null,
)

// ---------------------------------------------------------------------------------------------------------
// POST /price  (response is a JSON array)
// ---------------------------------------------------------------------------------------------------------

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitPriceRequestDto(
    @SerialName("tokens") val tokens: List<SwapkitPriceTokenRefDto>,
    @SerialName("metadata") val metadata: Boolean,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitPriceTokenRefDto(
    @SerialName("identifier") val identifier: String,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitPriceDto(
    @SerialName("identifier") val identifier: String,
    @SerialName("provider") val provider: String? = null,
    @SerialName("price_usd")
    @Serializable(LenientBigDecimalSerializer::class)
    val priceUsd: BigDecimal? = null,
    @SerialName("timestamp") val timestamp: Long? = null,
)

// ---------------------------------------------------------------------------------------------------------
// POST /v3/quote
// ---------------------------------------------------------------------------------------------------------

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitQuoteRequestDto(
    @SerialName("sellAsset") val sellAsset: String,
    @SerialName("buyAsset") val buyAsset: String,
    @SerialName("sellAmount") val sellAmount: String,
    @SerialName("slippage") val slippage: Double? = null,
    @SerialName("providers") val providers: List<String>? = null,
    // Bind the route to real addresses at quote time: `/v3/swap` rejects a route built without them with
    // `500 invalidRoute` ("{sourceAddress}"). Optional — omit for pure price discovery.
    @SerialName("sourceAddress") val sourceAddress: String? = null,
    @SerialName("destinationAddress") val destinationAddress: String? = null,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitQuoteResponseDto(
    @SerialName("quoteId") val quoteId: String? = null,
    @SerialName("routes") val routes: List<SwapkitRouteDto> = emptyList(),
    @SerialName("error") val error: String? = null,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitRouteDto(
    @SerialName("routeId") val routeId: String,
    @SerialName("providers") val providers: List<String> = emptyList(),
    @SerialName("sellAsset") val sellAsset: String,
    @SerialName("buyAsset") val buyAsset: String,
    @SerialName("sellAmount")
    @Serializable(LenientBigDecimalSerializer::class)
    val sellAmount: BigDecimal,
    @SerialName("expectedBuyAmount")
    @Serializable(LenientBigDecimalSerializer::class)
    val expectedBuyAmount: BigDecimal,
    @SerialName("expectedBuyAmountMaxSlippage")
    @Serializable(LenientBigDecimalSerializer::class)
    val expectedBuyAmountMaxSlippage: BigDecimal? = null,
    @SerialName("fees") val fees: List<SwapkitFeeDto> = emptyList(),
    @SerialName("expiration") val expiration: String? = null,
    @SerialName("totalSlippageBps") val totalSlippageBps: Double? = null,
    @SerialName("meta") val meta: SwapkitMetaDto? = null,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitFeeDto(
    @SerialName("type") val type: String? = null,
    @SerialName("amount")
    @Serializable(LenientBigDecimalSerializer::class)
    val amount: BigDecimal? = null,
    @SerialName("asset") val asset: String? = null,
    @SerialName("chain") val chain: String? = null,
    @SerialName("protocol") val protocol: String? = null,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitMetaDto(
    @SerialName("assets") val assets: List<SwapkitMetaAssetDto> = emptyList(),
    @SerialName("affiliateFee") val affiliateFee: Double? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitMetaAssetDto(
    @SerialName("asset") val asset: String,
    @SerialName("price")
    @Serializable(LenientBigDecimalSerializer::class)
    val price: BigDecimal? = null,
    @SerialName("image") val image: String? = null,
)

// ---------------------------------------------------------------------------------------------------------
// POST /v3/swap
// ---------------------------------------------------------------------------------------------------------

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitSwapRequestDto(
    @SerialName("routeId") val routeId: String,
    @SerialName("sourceAddress") val sourceAddress: String,
    @SerialName("destinationAddress") val destinationAddress: String,
    // Zodl skips PSBT/balance work it can't do for shielded Zcash; we use only targetAddress + memo.
    @SerialName("disableBuildTx") val disableBuildTx: Boolean = true,
    @SerialName("disableBalanceCheck") val disableBalanceCheck: Boolean = true,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitSwapResponseDto(
    @SerialName("swapId") val swapId: String? = null,
    @SerialName("targetAddress") val targetAddress: String,
    @SerialName("inboundAddress") val inboundAddress: String? = null,
    @SerialName("memo") val memo: String? = null,
    @SerialName("expectedBuyAmount")
    @Serializable(LenientBigDecimalSerializer::class)
    val expectedBuyAmount: BigDecimal? = null,
    @SerialName("expectedBuyAmountMaxSlippage")
    @Serializable(LenientBigDecimalSerializer::class)
    val expectedBuyAmountMaxSlippage: BigDecimal? = null,
    @SerialName("meta") val meta: SwapkitMetaDto? = null,
)

// ---------------------------------------------------------------------------------------------------------
// POST /track
// ---------------------------------------------------------------------------------------------------------

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitTrackRequestDto(
    @SerialName("hash") val hash: String? = null,
    @SerialName("chainId") val chainId: String? = null,
    @SerialName("depositAddress") val depositAddress: String? = null,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitTrackResponseDto(
    @SerialName("chainId") val chainId: String? = null,
    @SerialName("hash") val hash: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("trackingStatus") val trackingStatus: String? = null,
    @SerialName("fromAsset") val fromAsset: String? = null,
    @SerialName("fromAmount")
    @Serializable(LenientBigDecimalSerializer::class)
    val fromAmount: BigDecimal? = null,
    @SerialName("fromAddress") val fromAddress: String? = null,
    @SerialName("toAsset") val toAsset: String? = null,
    @SerialName("toAmount")
    @Serializable(LenientBigDecimalSerializer::class)
    val toAmount: BigDecimal? = null,
    @SerialName("toAddress") val toAddress: String? = null,
    @SerialName("finalisedAt") val finalisedAt: Long? = null,
)

@JsonIgnoreUnknownKeys
@Serializable
data class SwapkitErrorDto(
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
)
