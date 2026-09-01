package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.getOrSystem
import co.electriccoin.zcash.ui.common.usecase.SetAppearanceModeUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.theme.darklook.ThemeDarkLookArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ThemeSettingsVM(
    private val navigationRouter: NavigationRouter,
    private val appearanceModeStorageProvider: AppearanceModeStorageProvider,
    private val setAppearanceMode: SetAppearanceModeUseCase,
) : ViewModel() {
    private var originalMode = AppearanceMode.SYSTEM

    private val selectedMode = MutableStateFlow(AppearanceMode.SYSTEM)

    val state =
        selectedMode
            .map(::createState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    init {
        viewModelScope.launch {
            originalMode = appearanceModeStorageProvider.getOrSystem()
            selectedMode.update { originalMode }
        }
    }

    private fun createState(selectedMode: AppearanceMode) =
        ThemeSettingsState(
            options =
                AppearanceMode.entries.map { mode ->
                    AppearanceModeOptionState(
                        mode = mode,
                        isChecked = mode == selectedMode,
                        onClick = { onModeClick(mode) }
                    )
                },
            saveButton =
                ButtonState(
                    stringRes(R.string.currencyConversion_saveBtn),
                    onClick = ::onSaveClick,
                    isEnabled = selectedMode != originalMode,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ),
            onBack = ::onBack
        )

    private fun onBack() = navigationRouter.back()

    /**
     * [AppearanceMode.LIGHT] never renders dark, so it's selected directly. [AppearanceMode.SYSTEM] and
     * [AppearanceMode.DARK] can both resolve to a dark appearance, so tapping either one opens the dark-look
     * sheet instead of changing the local selection - the sheet itself persists the mode once a look is chosen.
     */
    private fun onModeClick(mode: AppearanceMode) {
        if (mode == AppearanceMode.LIGHT) {
            selectedMode.update { mode }
        } else {
            navigationRouter.forward(ThemeDarkLookArgs(mode))
        }
    }

    private fun onSaveClick() =
        viewModelScope.launch {
            setAppearanceMode(selectedMode.value, isOledEnabled = false)
        }
}
