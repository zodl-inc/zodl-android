package co.electriccoin.zcash.ui.screen.voting.delegationsigning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoteDelegationSigningVM(
    private val args: VoteDelegationSigningArgs,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val isGenerating = MutableStateFlow(false)
    private val proofProgress = MutableStateFlow<Float?>(null)

    val state: StateFlow<LceState<VoteDelegationSigningState>> =
        combine(isGenerating, proofProgress) { generating, progress ->
            LceState(
                content =
                    VoteDelegationSigningState(
                        title = stringRes("Delegation signing"),
                        body =
                            stringRes(
                                "This step generates a zero-knowledge proof that you held ZEC at the " +
                                    "snapshot height — without revealing your balance or address.\n\n" +
                                    "Proof generation takes approximately 1–3 minutes on real hardware."
                            ),
                        statusLabel =
                            when {
                                progress != null && progress < 1f -> {
                                    stringRes("Generating proof… ${(progress * 100).toInt()}%")
                                }

                                progress == 1f -> {
                                    stringRes("Proof complete — submitting delegation…")
                                }

                                else -> {
                                    stringRes("Ready to generate zero-knowledge proof")
                                }
                            },
                        proofProgress = progress,
                        generateButton =
                            ButtonState(
                                text =
                                    if (generating) {
                                        stringRes("Generating…")
                                    } else {
                                        stringRes("Generate delegation proof")
                                    },
                                style = ButtonStyle.PRIMARY,
                                isEnabled = !generating,
                                onClick = ::onGenerate
                            ),
                        onBack = { if (!generating) navigationRouter.back() },
                    ),
                isLoading = false
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), LceState(content = null, isLoading = true))

    private fun onGenerate() {
        if (isGenerating.value) return
        viewModelScope.launch {
            isGenerating.value = true
            // TODO: Call TypesafeVotingBackend.buildAndProveDelegation() when .so is compiled
            // Real impl: navigate to TallyingArgs(args.roundIdHex) after delegation proof completes
            // Simulate ZKP1 proof generation with progress updates
            val steps = 20
            repeat(steps) { i ->
                proofProgress.value = (i + 1).toFloat() / steps
                delay(150L) // ~3 seconds total for fake proof
            }
            delay(400) // Brief pause at 100%
            navigationRouter.forward(VoteTallyingArgs(roundIdHex = args.roundIdHex))
        }
    }
}
