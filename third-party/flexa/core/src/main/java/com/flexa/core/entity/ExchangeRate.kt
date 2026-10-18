package com.flexa.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ExchangeRatesResponse(
    val date: String? = null,
    val data: List<ExchangeRate>,
)

@Serializable
data class ExchangeRate(
    @SerialName("asset")
    val asset: String,
    @SerialName("expires_at")
    val expiresAt: Long? = null,
    @SerialName("label")
    val label: String? = null,
    @SerialName("precision")
    val precision: Int? = null,
    @SerialName("price")
    val price: String? = null,
    @SerialName("unit_of_account")
    val unitOfAccount: String? = null
)
