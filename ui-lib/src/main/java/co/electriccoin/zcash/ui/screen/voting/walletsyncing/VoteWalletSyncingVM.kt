package co.electriccoin.zcash.ui.screen.voting.walletsyncing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoteWalletSyncingVM(
    private val args: VoteWalletSyncingArgs,
    private val votingConfigRepository: VotingConfigRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    init {
        viewModelScope.launch {
            votingConfigRepository.get()
        }
    }

    val state: StateFlow<LceState<VoteWalletSyncingState>> =
        combine(
            votingConfigRepository.currentConfig,
            synchronizerProvider.synchronizer,
        ) { config, synchronizer ->
            val snapshotHeight = config
                ?.takeIf { snapshot -> snapshot.session.voteRoundId.toHex() == args.roundId }
                ?.session
                ?.snapshotHeight

            if (snapshotHeight == null || synchronizer == null) {
                LceState(content = null, isLoading = true)
            } else {
                val scannedHeight = synchronizer.fullyScannedHeight.value?.value ?: 0L
                val progress = (scannedHeight.coerceAtMost(snapshotHeight).toFloat() / snapshotHeight.toFloat())
                    .coerceIn(0f, 1f)
                val isSynced = scannedHeight >= snapshotHeight

                LceState(
                    content = VoteWalletSyncingState(
                        title = stringRes(R.string.vote_wallet_syncing_title),
                        body = stringRes(
                            R.string.vote_wallet_syncing_message,
                            snapshotHeight,
                            scannedHeight
                        ),
                        progressLabel =
                            if (isSynced) {
                                stringRes(R.string.vote_wallet_syncing_progress_complete)
                            } else {
                                stringRes(
                                    R.string.vote_wallet_syncing_progress_percent,
                                    (progress * 100).toInt()
                                )
                            },
                        progress = progress,
                        isSynced = isSynced,
                        continueButton = ButtonState(
                            text = stringRes(R.string.vote_continue),
                            style = ButtonStyle.PRIMARY,
                            isEnabled = isSynced,
                            onClick = ::onContinue
                        ),
                        onBack = ::onBack,
                    ),
                    isLoading = false,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = LceState(content = null, isLoading = true)
        )

    private fun onContinue() = navigationRouter.replace(VoteProposalListArgs(roundId = args.roundId))

    private fun onBack() = navigationRouter.backTo(VoteCoinholderPollingArgs::class)
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
