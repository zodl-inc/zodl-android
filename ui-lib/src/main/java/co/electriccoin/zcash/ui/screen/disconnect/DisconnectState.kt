package co.electriccoin.zcash.ui.screen.disconnect

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class DisconnectState(
    val header: StringResource,
    val title: StringResource,
    val subtitle: StringResource,
    val warningTitle: StringResource,
    val warningItems: List<StringResource>,
    val connectedTitle: StringResource,
    val connectedStatus: StringResource,
    val infoText: StringResource,
    val disconnectButton: ButtonState,
    val confirmationDialog: ZashiConfirmationState?,
    val onBack: () -> Unit,
)
