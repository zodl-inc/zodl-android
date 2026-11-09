package com.flexa.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class TokensResponse {
    @Serializable
    data class Success(
        @SerialName("id")
        val id: String,
        @SerialName("status")
        val status: String,
    ) : TokensResponse()
    data class Error(
        @SerialName("code")
        val code: String,
        @SerialName("message")
        val message: String,
    ) : TokensResponse()
}

@Serializable
data class TokenPatch(

    @SerialName("id")
    val id: String,

    @SerialName("status")
    val status: String,

    @SerialName("value")
    val value: String,

    @SerialName("expires_at")
    val expiration: Long,
)
