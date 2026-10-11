package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepository
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectArgs

/**
 * Ends the signing session the way Cancel does, then opens the Ledger connect flow to pair the
 * selected account again, with that account as the flow's target: a Ledger that exports another
 * viewing key is then reported as the wrong Ledger rather than added. The account's ZIP 32 index
 * goes with it when its binding still holds one, so the scan screen can offer it.
 *
 * The account is read before the session ends, so nothing suspends between leaving the sign sheet
 * and opening the flow.
 */
class NavigateToLedgerRepairUseCase(
    private val accountDataSource: AccountDataSource,
    private val ledgerRepairTargetRepository: LedgerRepairTargetRepository,
    private val cancelLedgerSigning: CancelLedgerSigningUseCase,
    private val navigationRouter: NavigationRouter,
) {
    suspend operator fun invoke() {
        val account = accountDataSource.getSelectedAccount()
        cancelLedgerSigning()
        ledgerRepairTargetRepository.set(
            accountUuid = account.sdkAccount.accountUuid,
            zip32AccountIndex = (account as? LedgerAccount)?.zip32AccountIndex,
        )
        navigationRouter.forward(LedgerConnectArgs)
    }
}
