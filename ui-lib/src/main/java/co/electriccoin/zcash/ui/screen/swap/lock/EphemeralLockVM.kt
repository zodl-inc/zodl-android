package co.electriccoin.zcash.ui.screen.swap.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitIncreaseEphemeralGapLimitUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteInfoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class EphemeralLockVM(
    observeProposal: ObserveProposalUseCase,
    private val navigationRouter: NavigationRouter,
    private val submitIncreaseEphemeralGapLimit: SubmitIncreaseEphemeralGapLimitUseCase,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
) : ViewModel() {
    val state: StateFlow<EphemeralLockState?> =
        observeProposal
            .filterSend()
            .map {
                EphemeralLockState(
                    items =
                        listOf(
                            SwapQuoteInfoItem(
                                description = stringRes(R.string.send_to),
                                title = stringResByAddress(it.destination.address),
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes(R.string.send_amount),
                                title = stringRes(it.amount).withStyle(),
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes(R.string.send_feeSummary),
                                title = stringRes(it.proposal.totalFeeRequired()).withStyle(),
                            ),
                        ),
                    amount =
                        SwapQuoteInfoItem(
                            description = stringRes(R.string.send_amountSummary),
                            title = stringRes(it.amount + it.proposal.totalFeeRequired()).withStyle(),
                        ),
                    secondaryButton =
                        ButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
                            onClick = ::onBack
                        ),
                    primaryButton =
                        ButtonState(
                            text = stringRes("Confirm Transaction"),
                            onClick = ::onSubmitClick
                        ),
                    onBack = ::onBack
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private var onSubmitClickJob: Job? = null

    private fun onBack() {
        zashiProposalRepository.clear()
        keystoneProposalRepository.clear()
        navigationRouter.backToRoot()
    }

    private fun onSubmitClick() {
        if (onSubmitClickJob?.isActive == true) return
        onSubmitClickJob = viewModelScope.launch { submitIncreaseEphemeralGapLimit() }
    }
}
