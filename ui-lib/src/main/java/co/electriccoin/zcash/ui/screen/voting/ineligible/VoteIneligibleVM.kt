package co.electriccoin.zcash.ui.screen.voting.ineligible

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.ZATOSHI_PER_ZEC
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale

class VoteIneligibleVM(
    private val args: VoteIneligibleArgs,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LceState<VoteIneligibleState>> =
        MutableStateFlow(
            LceState(
                content =
                    VoteIneligibleState(
                        title = stringRes(R.string.vote_ineligible_title),
                        body = buildBodyMessage(),
                        closeButton =
                            ButtonState(
                                text = stringRes(R.string.vote_close),
                                style = ButtonStyle.PRIMARY,
                                onClick = ::onClose
                            ),
                        onBack = ::onBack,
                    ),
                isLoading = false
            )
        )

    private fun buildBodyMessage(): StringResource =
        when (args.reason) {
            VoteIneligibilityReason.NO_NOTES -> {
                val snapshotHeight = snapshotHeightLabel()
                if (snapshotHeight == null) {
                    stringRes(R.string.vote_ineligible_no_notes)
                } else {
                    stringRes(R.string.vote_ineligible_no_notes_at_snapshot, snapshotHeight)
                }
            }

            VoteIneligibilityReason.BALANCE_TOO_LOW -> {
                val eligibleWeightZec = "%.4f".format(args.eligibleWeightZatoshi / ZATOSHI_PER_ZEC)
                val snapshotHeight = snapshotHeightLabel()
                if (snapshotHeight == null) {
                    stringRes(R.string.vote_ineligible_balance_too_low, eligibleWeightZec)
                } else {
                    stringRes(R.string.vote_ineligible_balance_too_low_at_snapshot, snapshotHeight, eligibleWeightZec)
                }
            }
        }

    private fun snapshotHeightLabel(): String? =
        args.snapshotHeight
            .takeIf { it > 0L }
            ?.let { NumberFormat.getNumberInstance(Locale.US).format(it) }

    private fun onClose() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}
