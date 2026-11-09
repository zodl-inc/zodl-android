package com.flexa.core.entity


import com.flexa.core.shared.Brand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class CommerceSession(
    @SerialName("api_version")
    val apiVersion: String? = null,
    @SerialName("created")
    val created: Long? = null,
    @SerialName("data")
    val data: Data? = null,
    @SerialName("id")
    val id: String? = null,
    @SerialName("object")
    val objectX: String? = null,
    @SerialName("type")
    val type: String? = null
) {
    @Serializable
    data class Data(
        @SerialName("account")
        val account: String? = null,
        @SerialName("amount")
        val amount: String? = null,
        @SerialName("asset")
        val asset: String? = null,
        @SerialName("brand")
        val brand: Brand? = null,
        @SerialName("created")
        val created: Long? = null,
        @SerialName("debits")
        val debits: List<Debit?>? = null,
        @SerialName("id")
        val id: String,
        @SerialName("intent")
        val intent: String? = null,
        @SerialName("object")
        val objectX: String? = null,
        @SerialName("preferences")
        val preferences: Preferences? = null,
        @SerialName("rate")
        val rate: Rate? = null,
        @SerialName("status")
        val status: String? = null,
        @SerialName("test_mode")
        val testMode: Boolean? = null,
        @SerialName("transactions")
        val transactions: List<Transaction?>? = null,
        @SerialName("updated")
        val updated: Long? = null,
        @SerialName("authorization")
        val authorization: Authorization? = null,
        @SerialName("credits")
        val credits: List<Credit>? = null,
        @Transient
        val isLegacy: Boolean = false
    ) {

        @Serializable
        data class Credit(
            @SerialName("id")
            val id: String? = null,
            @SerialName("amount")
            val amount: String? = null,
            @SerialName("asset")
            val asset: String? = null,
            @SerialName("kind")
            val kind: String? = null,
            @SerialName("label")
            val label: String? = null,
            @SerialName("status")
            val status: String? = null,
            @SerialName("created")
            val created: Long? = null,
            @SerialName("updated")
            val updated: Long? = null,
        )

        @Serializable
        data class Authorization(
            @SerialName("details")
            val details: String? = null,
            @SerialName("instructions")
            val instructions: String? = null,
            @SerialName("number")
            val number: String? = null,
            @SerialName("status")
            val status: String? = null
        )

        @Serializable
        data class Rate(
            @SerialName("expires_at")
            val expiresAt: Long? = null,
            @SerialName("label")
            val label: String? = null
        )

        @Serializable
        data class Debit(
            @SerialName("amount")
            val amount: String? = null,
            @SerialName("asset")
            val asset: String? = null,
            @SerialName("created")
            val created: Long? = null,
            @SerialName("id")
            val id: String? = null,
            @SerialName("intent_id")
            val intentId: String? = null,
            @SerialName("kind")
            val kind: String? = null,
            @SerialName("label")
            val label: String? = null,
            @SerialName("object")
            val objectX: String? = null,
            @SerialName("session_id")
            val sessionId: String? = null,
            @SerialName("test_mode")
            val testMode: Boolean? = null,
            @SerialName("updated")
            val updated: Long? = null
        )

        @Serializable
        data class Preferences(
            @SerialName("app")
            val app: String? = null,
            @SerialName("payment_asset")
            val paymentAsset: String? = null
        )

        @Serializable
        data class Transaction(
            @SerialName("amount")
            val amount: String? = null,
            @SerialName("asset")
            val asset: String? = null,
            @SerialName("created")
            val created: Long? = null,
            @SerialName("destination")
            val destination: Destination? = null,
            @SerialName("expires_at")
            val expiresAt: Long? = null,
            @SerialName("fee")
            val fee: Fee? = null,
            @SerialName("id")
            val id: String? = null,
            @SerialName("label")
            val label: String? = null,
            @SerialName("object")
            val objectX: String? = null,
            @SerialName("session")
            val session: String? = null,
            @SerialName("size")
            val size: String? = null,
            @SerialName("status")
            val status: String? = null,
            @SerialName("test_mode")
            val testMode: Boolean? = null,
            @SerialName("updated")
            val updated: Long? = null
        ) {
            @Serializable
            data class Destination(
                @SerialName("address")
                val address: String? = null,
                @SerialName("label")
                val label: String? = null
            )

            @Serializable
            data class Fee(
                @SerialName("amount")
                val amount: String? = null,
                @SerialName("asset")
                val asset: String? = null,
                @SerialName("equivalent")
                var equivalent: String? = null,
                @SerialName("label")
                var label: String? = null,
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
        }
    }
}

sealed class SseEvent(val eventId: String?) {
    class Session(id: String?, val session: CommerceSession) : SseEvent(id)
}