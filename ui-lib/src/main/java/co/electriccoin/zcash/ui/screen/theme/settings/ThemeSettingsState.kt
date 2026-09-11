package co.electriccoin.zcash.ui.screen.theme.settings

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.theme.AppearanceMode

internal data class ThemeSettingsState(
    val options: List<AppearanceModeOptionState>,
    val saveButton: ButtonState,
    val onBack: () -> Unit,
)

internal data class AppearanceModeOptionState(
    val mode: AppearanceMode,
    val isChecked: Boolean,
    val onClick: () -> Unit,
)
