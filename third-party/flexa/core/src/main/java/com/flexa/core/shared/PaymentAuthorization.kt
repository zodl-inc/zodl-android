package com.flexa.core.shared

sealed class PaymentAuthorization() {
    data class Success(
        val commerceSessionId: String,
        val brandName: String,
        val brandLogoUrl: String?
    ) : PaymentAuthorization()
    data class Failed(
        val commerceSessionId: String,
        val brandName: String,
        val brandLogoUrl: String?
    ) : PaymentAuthorization()
}