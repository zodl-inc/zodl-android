package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepository
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectArgs

/**
 * Ends the signing session the way Cancel does, then opens the Ledger connect flow to pair the
 * selected account again, with that account as the flow's target: a Ledger that exports another
 * viewing key is then reported as the wrong Ledger rather than added.
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
        val accountUuid = accountDataSource.getSelectedAccount().sdkAccount.accountUuid
        cancelLedgerSigning()
        ledgerRepairTargetRepository.set(accountUuid)
        navigationRouter.forward(LedgerConnectArgs)
    }
}
