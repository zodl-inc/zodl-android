package com.flexa.spend

import android.app.Activity
import android.content.Intent
import com.flexa.core.Flexa
import com.flexa.core.shared.PaymentAuthorization
import com.flexa.core.shared.Transaction
import com.flexa.spend.domain.CommerceSessionWorker

class SpendConfig private constructor() {

    fun open(activity: Activity, deepLink: String? = null) {
        val intent = Intent(activity, SpendActivity::class.java)
        intent.putExtra(SpendActivity.KEY_DEEP_LINK, deepLink)
        activity.startActivity(intent)
    }

    class Builder {

        fun onTransactionRequest(callback: (Result<Transaction>) -> Unit) = apply {
            Spend.onTransactionRequest = callback
        }

        fun onPaymentAuthorization(callback: (PaymentAuthorization) -> Unit) = apply {
            Spend.onPaymentAuthorization = callback
        }

        fun transactionSent(commerceSessionId: String, txSignature: String) {
            Spend.transactionSent?.invoke(commerceSessionId, txSignature)
        }

        fun transactionFailed(commerceSessionId: String) {
            Flexa.context?.let { ctx -> CommerceSessionWorker.execute(ctx, commerceSessionId) }
            Spend.transactionFailed?.invoke(commerceSessionId)
        }

        fun build() = SpendConfig()

        fun open(activity: Activity, deepLink: String? = null) =
            SpendConfig().open(activity, deepLink)
    }
}

class SpendConstants {
    companion object {
        const val LAST_SESSION_ID = "last.session.id"
        const val LAST_EVENT_ID = "last.event.id"
        const val ASSET_IDS = "asset.ids"
        const val COMMERCE_SESSION_KEY = "commerce.session.key"
        const val PINNED_BRANDS = "pinned.brands"
        const val ACCOUNT = "account"
    }
}

fun Flexa.buildSpend() = SpendConfig.Builder()
