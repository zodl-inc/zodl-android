package com.flexa.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Account(
    @SerialName("pinned_brands")
    val pinnedBrands: List<String?>? = null,
    @SerialName("preferred_unit_of_account")
    val preferredUnitOfAccount: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("notifications")
    val notifications: List<Notification>? = null,
    @SerialName("limits")
    val limits: List<Limit>? = null,
    @SerialName("created")
    val created: Long? = null,
    @SerialName("balance")
    val balance: Balance? = null
) {
    @Serializable
    data class Balance(
        @SerialName("amount")
        val amount: String? = null,
        @SerialName("asset")
        val asset: String? = null,
        @SerialName("label")
        val label: String? = null
    )
}

@Serializable
data class Notification(
    @SerialName("action")
    val action: Action? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("icon_url")
    val iconUrl: String? = null,
    @SerialName("id")
    val id: String? = null,
    @SerialName("object")
    val objectX: String? = null,
    @SerialName("title")
    val title: String? = null,
) {
    @Serializable
    data class Action(
        @SerialName("label")
        val label: String? = null,
        @SerialName("url")
        val url: String? = null
    )
}

@Serializable
data class Limit(
    @SerialName("amount")
    val amount: String? = null,
    @SerialName("asset")
    val asset: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("label")
    val label: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("remaining")
    val remaining: String? = null,
    @SerialName("resets_at")
    val resetsAt: Long? = null
)
