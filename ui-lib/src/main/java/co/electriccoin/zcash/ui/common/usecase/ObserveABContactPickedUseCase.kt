package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

class ObserveABContactPickedUseCase(
    private val synchronizerProvider: SynchronizerProvider,
) {
    private val bus = MutableSharedFlow<String>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke() =
        bus.mapLatest {
            val synchronizer = synchronizerProvider.getSynchronizer()
            val type = synchronizer.validateAddress(it)
            RecipientAddressState.new(
                address = it,
                type = type
            )
        }

    fun onContactPicked(contact: EnhancedABContact) =
        scope.launch {
            bus.emit(contact.address)
        }

    fun onWalletAccountPicked(account: WalletAccount) =
        scope.launch {
            bus.emit(account.unifiedAddress)
        }
}
