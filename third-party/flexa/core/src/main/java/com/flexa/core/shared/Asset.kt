package com.flexa.core.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AssetsResponse(
    val startingAfter: String? = null,
    val data: List<Asset>,
)

@Serializable
class Asset(

    @SerialName("icon_url")
    val iconUrl: String? = null,

    @SerialName("symbol")
    val symbol: String? = null,

    @SerialName("color")
    val color: String? = null,

    @SerialName("display_name")
    val displayName: String? = null,

    @SerialName("livemode")
    val livemode: Boolean? = null,

    @SerialName("id")
    val id: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Asset

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
