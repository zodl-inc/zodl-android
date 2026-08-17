package co.electriccoin.zcash.ui.screen.theme.settings

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.screen.exchangerate.settings.SimpleCheckboxState

internal data class ThemeSettingsState(
    val isClassicThemeSelected: SimpleCheckboxState,
    val isOledThemeSelected: SimpleCheckboxState,
    val saveButton: ButtonState,
    val onBack: () -> Unit,
)
