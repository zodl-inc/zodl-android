package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.screen.connectkeystone.connected.KeystoneConnectedArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenFlow
import com.keystone.module.ZcashAccount
import com.keystone.module.ZcashAccounts

class CreateKeystoneAccountUseCase(
    private val accountDataSource: AccountDataSource,
    private val synchronizerProvider: SynchronizerProvider,
    private val navigationRouter: NavigationRouter,
) {
    @Throws(InitializeException.ImportAccountException::class)
    suspend operator fun invoke(
        accounts: ZcashAccounts,
        account: ZcashAccount,
        birthday: BlockHeight? = null,
    ) {
        val createdAccount =
            accountDataSource.importKeystoneAccount(
                ufvk = account.ufvk,
                seedFingerprint = accounts.seedFingerprint,
                index = account.index.toLong(),
                birthday = birthday,
            )
        accountDataSource.selectAccount(createdAccount)
        synchronizerProvider.resetSynchronizer()
        if (birthday != null) {
            navigationRouter.forward(KeepOpenArgs(KeepOpenFlow.KEYSTONE))
        } else {
            navigationRouter.forward(KeystoneConnectedArgs)
        }
    }
}
