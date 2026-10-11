package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import co.electriccoin.zcash.ui.common.provider.KeepScreenOnSyncSessionProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Ends the [KeepScreenOnSyncSessionProvider] session once the sync it was started for has finished.
 *
 * A session only ends on [Synchronizer.Status.SYNCED] after [Synchronizer.Status.SYNCING] has been seen
 * on the same synchronizer while it was active. A synchronizer that reports itself synced before it has
 * started syncing, as a freshly reset one may, therefore does not end it. Collects forever and is
 * launched once from the application class.
 */
class ObserveKeepScreenOnSyncSessionUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val keepScreenOnSyncSessionProvider: KeepScreenOnSyncSessionProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke() {
        var syncingSeenOn: Synchronizer? = null
        synchronizerProvider.synchronizer
            .flatMapLatest { synchronizer ->
                synchronizer?.status?.map { status -> synchronizer to status } ?: emptyFlow()
            }.combine(keepScreenOnSyncSessionProvider.observe()) { (synchronizer, status), isSessionActive ->
                Triple(synchronizer, status, isSessionActive)
            }.collect { (synchronizer, status, isSessionActive) ->
                when {
                    isSessionActive != true -> {
                        syncingSeenOn = null
                    }

                    status == Synchronizer.Status.SYNCING -> {
                        syncingSeenOn = synchronizer
                    }

                    status == Synchronizer.Status.SYNCED && syncingSeenOn === synchronizer -> {
                        syncingSeenOn = null
                        keepScreenOnSyncSessionProvider.clear()
                    }
                }
            }
    }
}
