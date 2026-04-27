package co.electriccoin.zcash.ui.screen.voting.walletsyncing

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class VoteWalletSyncingState(
    val title: StringResource,
    val body: StringResource,
    val progressLabel: StringResource,
    val progress: Float,
    val isSynced: Boolean,
    val continueButton: ButtonState,
    val onBack: () -> Unit,
)
