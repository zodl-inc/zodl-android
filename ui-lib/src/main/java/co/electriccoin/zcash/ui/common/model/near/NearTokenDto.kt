@file:OptIn(ExperimentalSerializationApi::class)

package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.serialization.BigDecimalSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.math.BigDecimal

@JsonIgnoreUnknownKeys
@Serializable
data class NearTokenDto(
    @SerialName("assetId")
    val assetId: String,
    @SerialName("decimals")
    val decimals: Int,
    @SerialName("blockchain")
    val blockchain: String,
    @SerialName("symbol")
    val symbol: String,
    @SerialName("price")
    @Serializable(BigDecimalSerializer::class)
    val price: BigDecimal? = null,
    // @SerialName("priceUpdatedAt")
    // val priceUpdatedAt: String,
    // @SerialName("contractAddress")
    // val contractAddress: String? = null,
)
