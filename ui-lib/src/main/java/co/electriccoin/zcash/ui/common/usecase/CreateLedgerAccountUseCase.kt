package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenFlow

class CreateLedgerAccountUseCase(
    private val accountDataSource: AccountDataSource,
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val navigationRouter: NavigationRouter,
) {
    /**
     * @throws InitializeException.NoAccountLoaded if the pending pairing is gone — the flow was
     *         resumed after process death, and the device has to be paired again.
     */
    @Throws(InitializeException.ImportAccountException::class)
    suspend operator fun invoke(birthday: BlockHeight? = null) {
        val pairing = ledgerPairingRepository.get() ?: throw InitializeException.NoAccountLoaded
        val createdAccount = accountDataSource.importLedgerAccount(pairing, birthday)
        accountDataSource.selectAccount(createdAccount)
        ledgerPairingRepository.clear()
        synchronizerProvider.resetSynchronizer()
        if (birthday != null) {
            navigationRouter.forward(KeepOpenArgs(KeepOpenFlow.LEDGER))
        } else {
            navigationRouter.forward(LedgerConnectedArgs)
        }
    }
}
