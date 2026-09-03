package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.zcash.ui.common.model.WalletSnapshot
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.WalletRestoringStateProvider
import co.electriccoin.zcash.ui.common.provider.retainWhileWalletExists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

interface WalletSnapshotDataSource {
    fun observe(): StateFlow<WalletSnapshot?>
}

class WalletSnapshotDataSourceImpl(
    synchronizerProvider: SynchronizerProvider,
    walletRestoringStateProvider: WalletRestoringStateProvider,
    persistableWalletProvider: PersistableWalletProvider,
) : WalletSnapshotDataSource {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow =
        synchronizerProvider
            .synchronizer
            .flatMapLatest { synchronizer ->
                if (synchronizer == null) {
                    flowOf(null)
                } else {
                    val blocksRemainingFlow =
                        combine(
                            synchronizer.networkHeight,
                            synchronizer.fullyScannedHeight,
                        ) { networkHeight, fullyScannedHeight ->
                            if (networkHeight != null && fullyScannedHeight != null) {
                                (networkHeight.value - fullyScannedHeight.value).coerceAtLeast(0L)
                            } else {
                                -1L
                            }
                        }
                    combine(
                        synchronizer.status,
                        synchronizer.progress,
                        synchronizerProvider.error,
                        synchronizer.areFundsSpendable,
                        walletRestoringStateProvider.observe()
                    ) { status, progress, error, isSpendable, restoringState ->
                        WalletSnapshot(
                            status = status,
                            progress = progress,
                            synchronizerError = error,
                            isSpendable = isSpendable,
                            restoringState = restoringState,
                        )
                    }.combine(blocksRemainingFlow) { snapshot, blocksRemaining ->
                        snapshot.copy(blocksRemaining = blocksRemaining)
                    }
                }
            }.retainWhileWalletExists(persistableWalletProvider)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    override fun observe(): StateFlow<WalletSnapshot?> = flow
}
