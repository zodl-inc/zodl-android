package co.electriccoin.zcash.ui.screen.voting.walletsyncing

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

@Immutable
data class VoteWalletSyncingState(
    val title: StringResource,
    val body: StringResource,
    val progressLabel: StringResource,
    val progress: Float,
    val isSynced: Boolean,
    val continueButton: ButtonState,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            VoteWalletSyncingState(
                title = stringRes("Syncing wallet"),
                body = stringRes("Scanning block 2,500,000 of 2,500,000"),
                progressLabel = stringRes("50%"),
                progress = 0.5f,
                isSynced = false,
                continueButton = ButtonState.preview,
                onBack = {},
            )
    }
}
