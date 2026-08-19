package co.electriccoin.zcash.ui.screen.theme.settings

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.theme.AppearanceMode

internal data class ThemeSettingsState(
    val options: List<AppearanceModeOptionState>,
    val oledCheckbox: OledCheckboxState,
    val saveButton: ButtonState,
    val onBack: () -> Unit,
)

internal data class AppearanceModeOptionState(
    val mode: AppearanceMode,
    val isChecked: Boolean,
    val onClick: () -> Unit,
)

/**
 * [isEnabled] is false whenever the selected mode is [AppearanceMode.LIGHT] - pure black never applies to a
 * theme that never renders dark, so the checkbox has nothing to modify in that case.
 */
internal data class OledCheckboxState(
    val isChecked: Boolean,
    val isEnabled: Boolean,
    val onClick: () -> Unit,
)
