package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.model.WalletRestoringState.NONE
import co.electriccoin.zcash.ui.common.model.WalletRestoringState.RESTORING
import co.electriccoin.zcash.ui.common.model.WalletRestoringState.RESYNCING
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.WalletRestoringStateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

interface WalletSnapshotRepository {
    fun init()
}

class WalletSnapshotRepositoryImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val walletRestoringStateProvider: WalletRestoringStateProvider
) : WalletSnapshotRepository {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun init() {
        scope.launch {
            synchronizerProvider.synchronizer
                .flatMapLatest {
                    if (it == null) {
                        emptyFlow()
                    } else {
                        combine(it.status, walletRestoringStateProvider.observe()) { status, restoringState ->
                            status to restoringState
                        }
                    }
                }.collect { (status, restoringState) ->
                    // Once the wallet is fully synced and still in restoring/resyncing state, persist the new state
                    if (status == Synchronizer.Status.SYNCED && restoringState in listOf(RESTORING, RESYNCING, NONE)) {
                        walletRestoringStateProvider.store(WalletRestoringState.SYNCING)
                    }
                }
        }
    }
}
