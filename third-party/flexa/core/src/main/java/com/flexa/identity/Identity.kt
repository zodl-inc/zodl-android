package com.flexa.identity

import android.annotation.SuppressLint
import android.app.Application
import com.flexa.core.Flexa
import com.flexa.core.data.storage.SecuredPreferences
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.shared.SerializerProvider
import com.flexa.identity.domain.IdentityInteractor
import com.flexa.identity.shared.ConnectResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object Identity {
    private val securedPreferences by lazy {
        SecuredPreferences(
            Flexa.context as Application,
            SerializerProvider(), FlexaConstants.FILE
        )
    }

    @SuppressLint("StaticFieldLeak")
    val identityInteractor = IdentityInteractor(
        Flexa.restInteractor, Flexa.dbInteractor,
        securedPreferences
    )
    var onResult: ((ConnectResult) -> Unit)? = null

    fun sendResult(listener: ((ConnectResult) -> Unit)?) {
        Flexa.scope.launch {
            val accounts = identityInteractor.getAppAccounts()
            val token = securedPreferences.getString(FlexaConstants.TOKEN)
            val result = when {
                accounts != null && token != null -> {
                    ConnectResult.Connected(token)
                }
                else -> ConnectResult.NotConnected("")
            }
            withContext(Dispatchers.Main) {
                kotlin.runCatching { listener?.invoke(result) }
            }
        }
    }
}
