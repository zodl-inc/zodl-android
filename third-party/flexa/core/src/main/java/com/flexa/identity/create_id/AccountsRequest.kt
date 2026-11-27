package com.flexa.identity.create_id


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountsRequest(
    @SerialName("country")
    val country: String,
    @SerialName("date_of_birth")
    val dateOfBirth: String,
    @SerialName("email")
    val email: String,
    @SerialName("family_name")
    val familyName: String,
    @SerialName("given_name")
    val givenName: String,
)