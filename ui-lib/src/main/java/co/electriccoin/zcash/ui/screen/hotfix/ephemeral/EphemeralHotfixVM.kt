package co.electriccoin.zcash.ui.screen.hotfix.ephemeral

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.FixEphemeralAddressUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class EphemeralHotfixVM(
    args: EphemeralHotfixArgs,
    isTorEnabledStorageProvider: IsTorEnabledStorageProvider,
    private val navigationRouter: NavigationRouter,
    private val fixEphemeralAddressUseCase: FixEphemeralAddressUseCase,
) : ViewModel() {
    private val text = MutableStateFlow(args.address)

    private val isError = MutableStateFlow(false)

    val state: StateFlow<EphemeralHotfixState?> =
        combine(
            text,
            fixEphemeralAddressUseCase.observeIsLoading(),
            isError,
            isTorEnabledStorageProvider.observe()
        ) { text, isLoading, isError, isTorEnabled ->
            EphemeralHotfixState(
                address =
                    TextFieldState(
                        value = stringRes(text.orEmpty()),
                        onValueChange = { new -> this.text.update { new } },
                        isEnabled = !isLoading && isTorEnabled == true,
                        error = stringRes("").takeIf { isError }
                    ),
                button =
                    ButtonState(
                        stringRes("Recover Funds"),
                        onClick = ::onRecoverFundsClick,
                        isEnabled = !text.isNullOrBlank() && !isLoading && isTorEnabled == true
                    ),
                info =
                    stringRes(
                        "This operation requires Tor Protection. " +
                            "Please enable it in the Advanced Settings."
                    ).takeIf { isTorEnabled != true },
                onBack = ::onBack,
                title = stringRes("Discover Funds"),
                message =
                    stringRes(
                        "If you confirm, Zodl will scan the transparent address you provide and " +
                            "discover its funds. This may take a few minutes up to a few hours."
                    ),
                subtitle = stringRes("Transparent Address"),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun onBack() = navigationRouter.back()

    private fun onRecoverFundsClick() = fixEphemeralAddressUseCase(text.value.orEmpty())
}
