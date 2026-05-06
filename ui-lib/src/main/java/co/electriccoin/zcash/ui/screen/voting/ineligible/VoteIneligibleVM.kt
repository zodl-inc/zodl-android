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
            VoteIneligibilityReason.NO_NOTES ->
                stringRes(
                    "Your wallet has no shielded notes from before ${snapshotBlockLabel()}. " +
                        "Only funds that existed at the snapshot block are eligible for this voting round."
                )

            VoteIneligibilityReason.BALANCE_TOO_LOW -> {
                val eligibleWeightZec = "%.4f".format(args.eligibleWeightZatoshi / ZATOSHI_PER_ZEC)
                stringRes(
                    "Your shielded voting weight at ${snapshotBlockLabel()} was $eligibleWeightZec ZEC, " +
                        "so this wallet is not eligible for this voting round."
                )
            }
        }

    private fun snapshotBlockLabel(): String =
        args.snapshotHeight
            .takeIf { it > 0L }
            ?.let { "snapshot block #${NumberFormat.getNumberInstance(Locale.US).format(it)}" }
            ?: "the snapshot block"

    private fun onClose() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}
