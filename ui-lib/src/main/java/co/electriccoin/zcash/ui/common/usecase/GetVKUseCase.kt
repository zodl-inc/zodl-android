package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.common.model.viewingKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetVKUseCase(
    private val observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase
) {
    fun observe(type: VKType): Flow<String?> =
        observeSelectedWalletAccount.require().map { account -> account.sdkAccount.viewingKey(type) }

    fun observeAvailability(): Flow<Map<VKType, Boolean>> =
        observeSelectedWalletAccount.require().map { account ->
            VKType.entries.associateWith { type -> account.sdkAccount.viewingKey(type) != null }
        }
}
