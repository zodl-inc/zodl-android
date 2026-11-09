package com.flexa.spend

import android.annotation.SuppressLint
import android.app.Application
import com.flexa.core.Flexa
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.shared.PaymentAuthorization
import com.flexa.core.shared.SelectedAsset
import com.flexa.core.shared.SerializerProvider
import com.flexa.core.shared.Transaction
import com.flexa.core.shared.observeConnectionAsFlow
import com.flexa.spend.data.SecuredPreferences
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.domain.SpendInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

internal object Spend {
    val tokenState = MutableStateFlow<TokenState>(TokenState.Fine)
    val selectedAsset: StateFlow<SelectedAsset?> = Flexa.selectedAsset
    var onTransactionRequest: ((Result<Transaction>) -> Unit)? = null
    var onPaymentAuthorization: ((PaymentAuthorization) -> Unit)? = null
    internal var transactionSent: ((
        @ParameterName("commerceSessionId") String,
        @ParameterName("txSignature") String
    ) -> Unit)? = null
    internal var transactionFailed: ((
        @ParameterName("commerceSessionId") String
    ) -> Unit)? = null
    internal val json = Json { ignoreUnknownKeys = true }

    private val securedPreferences by lazy {
        SecuredPreferences(
            Flexa.context as Application,
            SerializerProvider(), FlexaConstants.FILE
        )
    }
    private var USE_FAKE_INTERACTOR = false

    @SuppressLint("StaticFieldLeak")
    val interactor = if (USE_FAKE_INTERACTOR) FakeInteractor()
    else SpendInteractor(
        Flexa.restInteractor, Flexa.dbInteractor,
        securedPreferences, Flexa.context?.observeConnectionAsFlow()
    )

    fun selectedAsset(value: SelectedAsset) {
        Flexa.selectedAsset(value.accountId, value.asset.assetId)
    }
}

sealed class TokenState {
    data object Fine: TokenState()
    data object Error: TokenState()
}
