package co.electriccoin.zcash.ui.screen.voting.walletsyncing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.usecase.GetActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.delegationsigning.VoteDelegationSigningArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoteWalletSyncingVM(
    private val getActiveVotingSession: GetActiveVotingSessionUseCase,
    private val synchronizerProvider: SynchronizerProvider,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val snapshotHeightFlow = MutableStateFlow<Long?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val session = runCatching { getActiveVotingSession() }.getOrNull()
            snapshotHeightFlow.value = session?.snapshotHeight
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LceState<VoteWalletSyncingState>> =
        combine(
            snapshotHeightFlow,
            synchronizerProvider.synchronizer.flatMapLatest { sync ->
                sync?.fullyScannedHeight ?: flowOf(null)
            },
            synchronizerProvider.synchronizer.flatMapLatest { sync ->
                sync?.networkHeight ?: flowOf(null)
            }
        ) { snapshotHeight, fullyScanned, networkHeight ->
            if (snapshotHeight == null) {
                LceState(content = null, isLoading = true)
            } else {
                val scanned = fullyScanned?.value ?: 0L
                val network = networkHeight?.value ?: snapshotHeight
                val progress =
                    if (snapshotHeight <= 0L) {
                        1f
                    } else {
                        (scanned.coerceAtMost(snapshotHeight).toFloat() / snapshotHeight.toFloat()).coerceIn(0f, 1f)
                    }
                val isSynced = scanned >= snapshotHeight

                LceState(
                    content =
                        VoteWalletSyncingState(
                            title = stringRes(R.string.vote_wallet_syncing_title),
                            body =
                                stringRes(
                                    "Your wallet needs to scan up to block $snapshotHeight (the snapshot height) " +
                                        "before you can vote. Currently at block $scanned."
                                ),
                            progressLabel =
                                if (isSynced) {
                                    stringRes(R.string.vote_wallet_syncing_status_complete)
                                } else {
                                    stringRes(
                                        R.string.vote_wallet_syncing_status_progress,
                                        (progress * 100).toInt(),
                                        scanned,
                                        snapshotHeight
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
                    isLoading = false
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), LceState(content = null, isLoading = true))

    private fun onContinue() = navigationRouter.forward(VoteDelegationSigningArgs)

    private fun onBack() = navigationRouter.back()
}
