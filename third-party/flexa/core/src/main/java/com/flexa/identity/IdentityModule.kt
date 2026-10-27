package com.flexa.identity

import android.app.Activity
import android.content.Intent
import com.flexa.core.Flexa
import com.flexa.identity.shared.ConnectResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IdentityConfig private constructor() {

    fun open(activity: Activity, deepLink: String? = null) {
        val intent = Intent(activity, IdentityActivity::class.java)
        intent.putExtra(IdentityActivity.KEY_DEEP_LINK, deepLink)
        activity.startActivity(intent)
    }

    fun collect(callback: (ConnectResult) -> Unit) = Identity.sendResult(callback)

    fun disconnect(listener: (() -> Unit)? = null) {
        Flexa.scope.launch(Dispatchers.Unconfined) {
            Identity.identityInteractor.clearLoginData()
            listener?.invoke()
        }
    }

    class Builder {

        fun onResult(callback: (ConnectResult) -> Unit) = apply {
            Identity.onResult = callback
        }

        fun build() = IdentityConfig()

        fun open(activity: Activity, deepLink: String? = null) =
            IdentityConfig().open(activity, deepLink)
    }
}

fun Flexa.buildIdentity() = IdentityConfig.Builder()
