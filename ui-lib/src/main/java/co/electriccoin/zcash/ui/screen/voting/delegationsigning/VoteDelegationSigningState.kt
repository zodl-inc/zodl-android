package co.electriccoin.zcash.ui.screen.voting.delegationsigning

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class VoteDelegationSigningState(
    val title: StringResource,
    val body: StringResource,
    val statusLabel: StringResource,
    /** Progress 0..1 during proof generation, null when idle. */
    val proofProgress: Float?,
    val generateButton: ButtonState,
    val onBack: () -> Unit,
)
