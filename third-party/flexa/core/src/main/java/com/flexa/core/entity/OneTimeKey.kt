package com.flexa.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OneTimeKeyResponse(
    val startingAfter: String? = null,
    val date: String,
    val data: List<OneTimeKey>,
)

@Serializable
data class OneTimeKey(
    @SerialName("id")
    val id: String,
    @SerialName("asset")
    val asset: String? = null,
    @SerialName("expires_at")
    val expiresAt: Long? = null,
    @SerialName("length")
    val length: Int? = null,
    @SerialName("livemode")
    val livemode: Boolean? = null,
    @SerialName("prefix")
    val prefix: String? = null,
    @SerialName("secret")
    val secret: String? = null
)


