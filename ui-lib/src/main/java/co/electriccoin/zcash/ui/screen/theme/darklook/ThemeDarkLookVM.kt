package co.electriccoin.zcash.ui.screen.theme.darklook

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.getOrSystem
import co.electriccoin.zcash.ui.common.usecase.SetAppearanceModeUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ThemeDarkLookVM(
    private val args: ThemeDarkLookArgs,
    private val navigationRouter: NavigationRouter,
    private val appearanceModeStorageProvider: AppearanceModeStorageProvider,
    private val isOledEnabledStorageProvider: IsOledEnabledStorageProvider,
    private val setAppearanceMode: SetAppearanceModeUseCase,
) : ViewModel() {
    private var storedMode = AppearanceMode.SYSTEM

    private var storedOledEnabled = false

    private val selectedOledEnabled = MutableStateFlow<Boolean?>(null)

    val state =
        selectedOledEnabled
            .map { oledEnabled -> oledEnabled?.let(::createState) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    init {
        viewModelScope.launch {
            storedMode = appearanceModeStorageProvider.getOrSystem()
            storedOledEnabled = isOledEnabledStorageProvider.get() == true
            selectedOledEnabled.update { storedOledEnabled }
        }
    }

    private fun createState(oledEnabled: Boolean) =
        ThemeDarkLookState(
            options =
                listOf(false, true).map { isOled ->
                    DarkLookOptionState(
                        isOledEnabled = isOled,
                        isChecked = isOled == oledEnabled,
                        onClick = { onOptionClick(isOled) }
                    )
                },
            saveButton =
                ButtonState(
                    stringRes(R.string.currencyConversion_saveBtn),
                    onClick = ::onSaveClick,
                    isEnabled = isSaveEnabled(oledEnabled),
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ),
            onBack = ::onBack
        )

    /**
     * Save commits two values - [ThemeDarkLookArgs.mode] and the dark look - and it is the only path that
     * persists the mode for [AppearanceMode.SYSTEM] and [AppearanceMode.DARK]. So it stays enabled whenever
     * either one differs from what is stored; gating on the look alone would make switching to a mode while
     * keeping the current look unreachable.
     */
    private fun isSaveEnabled(oledEnabled: Boolean) = oledEnabled != storedOledEnabled || args.mode != storedMode

    private fun onOptionClick(isOledEnabled: Boolean) = selectedOledEnabled.update { isOledEnabled }

    private fun onBack() = navigationRouter.back()

    private fun onSaveClick() =
        viewModelScope.launch {
            setAppearanceMode(args.mode, isOledEnabled = selectedOledEnabled.value == true)
        }
}
