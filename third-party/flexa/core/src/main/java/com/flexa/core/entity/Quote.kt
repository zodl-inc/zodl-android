package com.flexa.core.entity


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Quote(
    @SerialName("amount")
    val amount: String? = null,
    @SerialName("asset")
    val asset: String? = null,
    @SerialName("fee")
    val fee: Fee? = null,
    @SerialName("label")
    val label: String? = null,
    @SerialName("unit_of_account")
    val unitOfAccount: String? = null,
    @SerialName("value")
    val value: Value? = null
) {
    @Serializable
    data class Fee(
        @SerialName("amount")
        val amount: String? = null,
        @SerialName("asset")
        val asset: String? = null,
        @SerialName("equivalent")
        val equivalent: String? = null,
        @SerialName("label")
        val label: String? = null,
        @SerialName("price")
        val price: Price? = null,
        @SerialName("zone")
        val zone: String? = null
    ) {
        @Serializable
        data class Price(
            @SerialName("amount")
            val amount: String? = null,
            @SerialName("label")
            val label: String? = null,
            @SerialName("priority")
            val priority: String? = null
        )
    }

    @Serializable
    data class Value(
        @SerialName("amount")
        val amount: String? = null,
        @SerialName("label")
        val label: String? = null,
        @SerialName("rate")
        val rate: Rate? = null
    ) {
        @Serializable
        data class Rate(
            @SerialName("expires_at")
            val expiresAt: Long? = null,
            @SerialName("label")
            val label: String? = null
        )
    }
}