package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.WalletCoordinator
import cash.z.ecc.android.sdk.WalletInitMode
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.flow.first

class RecoverFromSeedMismatchUseCase(
    private val persistableWalletProvider: PersistableWalletProvider,
    private val walletCoordinator: WalletCoordinator,
) {
    suspend operator fun invoke() {
        val wallet = persistableWalletProvider.getPersistableWallet() ?: return
        val recoveryWallet = wallet.copy(walletInitMode = WalletInitMode.RestoreWallet)
        // Use lockout-aware erase so any lingering synchronizer is stopped before the DB wipe.
        // Erase clears all prefs — seed is re-persisted immediately after.
        walletCoordinator.deleteSdkDataFlow().first()
        persistableWalletProvider.store(recoveryWallet)
    }
}
