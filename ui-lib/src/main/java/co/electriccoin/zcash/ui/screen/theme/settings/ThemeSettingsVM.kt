package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.IsOledThemeEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.SetOledThemeUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.exchangerate.settings.SimpleCheckboxState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ThemeSettingsVM(
    private val navigationRouter: NavigationRouter,
    private val isOledThemeEnabledStorageProvider: IsOledThemeEnabledStorageProvider,
    private val setOledTheme: SetOledThemeUseCase,
) : ViewModel() {
    private var isOledOriginal = false

    private val isOledSelected = MutableStateFlow(false)

    val state =
        isOledSelected
            .map(::createState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    init {
        viewModelScope.launch {
            isOledOriginal = isOledThemeEnabledStorageProvider.get() == true
            isOledSelected.update { isOledOriginal }
        }
    }

    private fun createState(isOled: Boolean) =
        ThemeSettingsState(
            isClassicThemeSelected = SimpleCheckboxState(!isOled, ::onClassicThemeClick),
            isOledThemeSelected = SimpleCheckboxState(isOled, ::onOledThemeClick),
            saveButton =
                ButtonState(
                    stringRes(R.string.currencyConversion_saveBtn),
                    onClick = ::onSaveClick,
                    isEnabled = isOled != isOledOriginal,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ),
            onBack = ::onBack
        )

    private fun onBack() = navigationRouter.back()

    private fun onClassicThemeClick() = isOledSelected.update { false }

    private fun onOledThemeClick() = isOledSelected.update { true }

    private fun onSaveClick() = viewModelScope.launch { setOledTheme(isOledSelected.value) }
}
