package com.flexa.core.entity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionFee(
    @SerialName("amount")
    val amount: String? = null,
    @SerialName("asset")
    val asset: String,
    @SerialName("expires_at")
    val expiresAt: Long? = null,
    @SerialName("label")
    val label: String? = null,
    @SerialName("price")
    val price: TransactionFeePrice? = null,
    @SerialName("transaction_asset")
    val transactionAsset: String,
    @SerialName("zone")
    val zone: String? = null
)

@Serializable
data class TransactionFeePrice(
    @SerialName("amount")
    val amount: String? = null,
    @SerialName("label")
    val label: String? = null,
    @SerialName("priority")
    val priority: String? = null
)
