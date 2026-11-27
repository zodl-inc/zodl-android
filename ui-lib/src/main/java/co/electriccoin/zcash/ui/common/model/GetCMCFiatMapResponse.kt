@file:OptIn(ExperimentalSerializationApi::class)

package co.electriccoin.zcash.ui.common.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@JsonIgnoreUnknownKeys
@Serializable
data class GetCMCFiatMapResponse(
    @SerialName("data")
    val data: List<CMCFiatCurrencyDto>
)

@JsonIgnoreUnknownKeys
@Serializable
data class CMCFiatCurrencyDto(
    @SerialName("id")
    val id: Int,
    @SerialName("symbol")
    val symbol: String,
)
