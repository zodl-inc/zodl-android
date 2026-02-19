package co.electriccoin.zcash.ui.screen.hotfix.enhancement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.FixEnhancementUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.hotfix.ephemeral.EphemeralHotfixState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EnhancementHotfixVM(
    private val navigationRouter: NavigationRouter,
    private val fixEnhancement: FixEnhancementUseCase
) : ViewModel() {
    private val text = MutableStateFlow<String?>(null)

    private val isError = MutableStateFlow(false)

    val state: StateFlow<EphemeralHotfixState?> =
        combine(
            text,
            isError
        ) { text, isError ->
            EphemeralHotfixState(
                address =
                    TextFieldState(
                        value = stringRes(text.orEmpty()),
                        onValueChange = { new -> this.text.update { new } },
                        error = stringRes("").takeIf { isError }
                    ),
                button =
                    ButtonState(
                        stringRes("Fetch Data"),
                        onClick = ::onFetchDataClick,
                        isEnabled = !text.isNullOrBlank()
                    ),
                info = null,
                onBack = ::onBack,
                title = stringRes("Refresh Transaction Data"),
                message =
                    stringRes(
                        "If you confirm, Zodl will fetch transaction data for a transaction ID you provide"
                    ),
                subtitle = stringRes("Transaction ID"),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun onBack() = navigationRouter.back()

    private fun onFetchDataClick() = viewModelScope.launch { fixEnhancement(text.value.orEmpty()) }
}
