package co.electriccoin.zcash.ui.screen.voting.walletsyncing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class VoteWalletSyncingVM(
    private val args: VoteWalletSyncingArgs,
    private val votingApiRepository: VotingApiRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val hasAutoAdvanced = AtomicBoolean(false)

    init {
        observeForAutoAdvance()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LceState<VoteWalletSyncingState>> =
        combine(
            votingApiRepository.snapshot,
            synchronizerProvider.synchronizer.flatMapLatest { synchronizer ->
                synchronizer?.fullyScannedHeight ?: flowOf(null)
            },
        ) { apiSnapshot, fullyScannedHeight ->
            val snapshotHeight =
                apiSnapshot.sessionsByRoundId[args.roundId.lowercase()]
                    ?.snapshotHeight

            if (snapshotHeight == null) {
                LceState(content = null, isLoading = true)
            } else {
                val scannedHeight = fullyScannedHeight?.value ?: 0L
                val progress =
                    (scannedHeight.coerceAtMost(snapshotHeight).toFloat() / snapshotHeight.toFloat())
                        .coerceIn(0f, 1f)
                val isSynced = scannedHeight >= snapshotHeight

                LceState(
                    content =
                        VoteWalletSyncingState(
                            title = stringRes(R.string.vote_wallet_syncing_title),
                            body =
                                stringRes(
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
                            continueButton =
                                ButtonState(
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

    /**
     * Mirrors iOS `startActiveRoundPipeline` (`VotingStore+Session.swift:317-332`): once the wallet's
     * fully-scanned height catches up to the active round's snapshot height, automatically navigate
     * to the proposal list without waiting for a user tap. Guarded by a one-shot flag so a re-entry
     * (e.g. snapshot height fluctuation, recomposition) cannot trigger a second navigation. The
     * Continue button remains as a defensive fallback.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeForAutoAdvance() {
        viewModelScope.launch {
            val snapshotHeight =
                votingApiRepository
                    .snapshot
                    .filter { snapshot ->
                        snapshot.sessionsByRoundId.containsKey(args.roundId.lowercase())
                    }.first()
                    .sessionsByRoundId
                    .getValue(args.roundId.lowercase())
                    .snapshotHeight

            synchronizerProvider
                .synchronizer
                .flatMapLatest { synchronizer ->
                    synchronizer?.fullyScannedHeight ?: flowOf(null)
                }.distinctUntilChanged()
                .filter { height -> (height?.value ?: 0L) >= snapshotHeight }
                .first()

            if (hasAutoAdvanced.compareAndSet(false, true)) {
                navigationRouter.replace(VoteProposalListArgs(roundId = args.roundId))
            }
        }
    }

    private fun onContinue() {
        if (hasAutoAdvanced.compareAndSet(false, true)) {
            navigationRouter.replace(VoteProposalListArgs(roundId = args.roundId))
        }
    }

    private fun onBack() = navigationRouter.backTo(VoteCoinholderPollingArgs::class)
}
