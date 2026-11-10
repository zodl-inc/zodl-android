package co.electriccoin.zcash.ui.screen.zns.settings

data class ZnsSettingsState(
    val isEnabled: Boolean,
    val onSaveClick: (enable: Boolean) -> Unit,
    val onDismiss: () -> Unit,
)
