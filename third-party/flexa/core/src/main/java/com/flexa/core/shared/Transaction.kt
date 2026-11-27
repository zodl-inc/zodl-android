package com.flexa.core.shared

data class Transaction(
    val commerceSessionId: String,
    val amount: String,
    val brandLogo: String,
    val brandName: String,
    val brandColor: String,
    val assetAccountHash: String,
    val assetId: String,
    val destinationAddress: String,
    val feeAmount: String,
    val feeAssetId: String,
    val feePrice: String,
    val feePriorityPrice: String,
    val size: String,
)
