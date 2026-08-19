package co.electriccoin.zcash.ui.screen.theme.settings

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ThemeSettingsVM(
    private val navigationRouter: NavigationRouter,
    private val appearanceModeStorageProvider: AppearanceModeStorageProvider,
    private val isOledEnabledStorageProvider: IsOledEnabledStorageProvider,
    private val setAppearanceMode: SetAppearanceModeUseCase,
) : ViewModel() {
    private var originalMode = AppearanceMode.SYSTEM
    private var originalOledEnabled = false

    private val selectedMode = MutableStateFlow(AppearanceMode.SYSTEM)
    private val selectedOledEnabled = MutableStateFlow(false)

    val state =
        combine(selectedMode, selectedOledEnabled, ::createState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    init {
        viewModelScope.launch {
            originalMode = appearanceModeStorageProvider.getOrSystem()
            originalOledEnabled = isOledEnabledStorageProvider.get() == true
            selectedMode.update { originalMode }
            selectedOledEnabled.update { originalOledEnabled }
        }
    }

    private fun createState(
        selectedMode: AppearanceMode,
        oledEnabled: Boolean
    ) = ThemeSettingsState(
        options =
            AppearanceMode.entries.map { mode ->
                AppearanceModeOptionState(
                    mode = mode,
                    isChecked = mode == selectedMode,
                    onClick = { onModeClick(mode) }
                )
            },
        oledCheckbox =
            OledCheckboxState(
                isChecked = oledEnabled,
                isEnabled = selectedMode != AppearanceMode.LIGHT,
                onClick = ::onOledClick
            ),
        saveButton =
            ButtonState(
                stringRes(R.string.currencyConversion_saveBtn),
                onClick = ::onSaveClick,
                isEnabled = selectedMode != originalMode || oledEnabled != originalOledEnabled,
                hapticFeedbackType = HapticFeedbackType.Confirm
            ),
        onBack = ::onBack
    )

    private fun onBack() = navigationRouter.back()

    private fun onModeClick(mode: AppearanceMode) = selectedMode.update { mode }

    private fun onOledClick() = selectedOledEnabled.update { !it }

    private fun onSaveClick() =
        viewModelScope.launch {
            setAppearanceMode(selectedMode.value, selectedOledEnabled.value)
        }
}
