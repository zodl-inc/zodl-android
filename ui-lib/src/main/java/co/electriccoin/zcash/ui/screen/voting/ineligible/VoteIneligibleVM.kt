package co.electriccoin.zcash.ui.screen.voting.ineligible

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.usecase.IneligibilityReason
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale

class VoteIneligibleVM(
    private val args: VoteIneligibleArgs,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val reason =
        runCatching {
            IneligibilityReason.valueOf(args.reason)
        }.getOrDefault(IneligibilityReason.NO_NOTES)

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

    private fun buildBodyMessage(): StringResource {
        val snapshotFormatted = NumberFormat.getNumberInstance(Locale.US).format(args.snapshotHeight)
        return when (reason) {
            IneligibilityReason.NO_NOTES -> {
                stringRes(
                    "Your wallet has no shielded notes from before the snapshot block. " +
                        "Only funds that existed at block #$snapshotFormatted are eligible for this voting round."
                )
            }

            IneligibilityReason.BALANCE_TOO_LOW -> {
                val balanceZEC = "%.3f".format(args.balanceZatoshi / 100_000_000.0)
                stringRes(
                    "Your shielded balance at the snapshot block was $balanceZEC ZEC, " +
                        "which is below the 0.125 ZEC minimum required to vote."
                )
            }
        }
    }

    private fun onClose() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}
