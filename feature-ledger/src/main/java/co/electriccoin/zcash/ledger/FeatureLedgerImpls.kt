package co.electriccoin.zcash.ledger

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.ledger.LedgerAccountImporter
import co.electriccoin.zcash.ui.common.ledger.LedgerNavContributor
import co.electriccoin.zcash.ui.common.ledger.LedgerNavigator
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepository
import co.electriccoin.zcash.ui.common.usecase.CreateLedgerAccountUseCase
import co.electriccoin.zcash.ui.dialogComposable
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectArgs
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectScreen
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedArgs
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedScreen
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeArgs
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeScreen
import co.electriccoin.zcash.ui.screen.connectledger.openapp.LedgerOpenAppArgs
import co.electriccoin.zcash.ui.screen.connectledger.openapp.LedgerOpenAppScreen
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanArgs
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanScreen
import co.electriccoin.zcash.ui.screen.connectledger.turnon.LedgerTurnOnArgs
import co.electriccoin.zcash.ui.screen.connectledger.turnon.LedgerTurnOnScreen
import co.electriccoin.zcash.ui.screen.signledgertransaction.LedgerSignArgs
import co.electriccoin.zcash.ui.screen.signledgertransaction.LedgerSignScreen

class LedgerAccountImporterImpl(
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val createLedgerAccount: CreateLedgerAccountUseCase,
) : LedgerAccountImporter {
    override fun hasPendingPairing(): Boolean = ledgerPairingRepository.get() != null

    override suspend fun importAccount(birthday: BlockHeight?) = createLedgerAccount(birthday)
}

class LedgerNavigatorImpl(
    private val navigationRouter: NavigationRouter,
    private val ledgerRepairTargetRepository: LedgerRepairTargetRepository,
) : LedgerNavigator {
    override fun forwardToSign() = navigationRouter.forward(LedgerSignArgs)

    /**
     * Opens the connect flow to add a Ledger, so no account is left as the target of pairing again.
     */
    override fun forwardToConnect() {
        ledgerRepairTargetRepository.clear()
        navigationRouter.forward(LedgerConnectArgs)
    }

    override fun forwardToConnected() = navigationRouter.forward(LedgerConnectedArgs)
}

class LedgerNavContributorImpl : LedgerNavContributor {
    override fun contribute(navGraphBuilder: NavGraphBuilder) {
        with(navGraphBuilder) {
            dialogComposable<LedgerSignArgs> { LedgerSignScreen() }
            composable<LedgerConnectArgs> { LedgerConnectScreen() }
            composable<LedgerTurnOnArgs> { LedgerTurnOnScreen() }
            composable<LedgerDeviceScanArgs> { LedgerDeviceScanScreen() }
            composable<LedgerOpenAppArgs> { LedgerOpenAppScreen() }
            composable<LedgerHandshakeArgs> { LedgerHandshakeScreen() }
            composable<LedgerConnectedArgs> { LedgerConnectedScreen() }
        }
    }
}
