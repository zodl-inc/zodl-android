package co.electriccoin.zcash.ui.screen.theme.darklook

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.SetAppearanceModeUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
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
    private val isOledEnabledStorageProvider: IsOledEnabledStorageProvider,
    private val setAppearanceMode: SetAppearanceModeUseCase,
) : ViewModel() {
    private var originalOledEnabled = false

    private val selectedOledEnabled = MutableStateFlow(false)

    val state =
        selectedOledEnabled
            .map(::createState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    init {
        viewModelScope.launch {
            originalOledEnabled = isOledEnabledStorageProvider.get() == true
            selectedOledEnabled.update { originalOledEnabled }
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
                    isEnabled = oledEnabled != originalOledEnabled,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ),
            onBack = ::onBack
        )

    private fun onOptionClick(isOledEnabled: Boolean) = selectedOledEnabled.update { isOledEnabled }

    private fun onBack() = navigationRouter.back()

    private fun onSaveClick() =
        viewModelScope.launch {
            setAppearanceMode(args.mode, isOledEnabled = selectedOledEnabled.value)
        }
}
