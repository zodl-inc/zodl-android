package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.ledger.LedgerAccountImporter
import co.electriccoin.zcash.ui.common.model.LedgerPairingMissingException
import co.electriccoin.zcash.ui.common.model.ViewOnlyPendingImportMissingException
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyAccountImporter
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment

/**
 * The one place the shared enrollment screens dispatch on vendor: everything else about them —
 * the birthday question, the estimation, the manual height, the Lce wiring — is identical.
 */
class CreateHWWalletAccountUseCase(
    private val parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
    private val createKeystoneAccount: CreateKeystoneAccountUseCase,
    private val ledgerAccountImporter: LedgerAccountImporter,
    private val viewOnlyAccountImporter: ViewOnlyAccountImporter,
    private val navigationRouter: NavigationRouter,
) {
    /**
     * Whether [enrollment] still carries what the import needs. A Ledger pairing and a pending
     * view-only key live in memory only and are gone after process death; a Keystone UR travels in
     * the route but can be unusable. Either way the screens have nothing to offer, and send the user back to the root
     * instead of a form that cannot be submitted.
     */
    fun isReady(enrollment: HWWalletEnrollment): Boolean =
        when (enrollment) {
            is HWWalletEnrollment.Keystone -> {
                try {
                    parseKeystoneUrToZashiAccounts(enrollment.ur).accounts.isNotEmpty()
                } catch (_: InvalidKeystoneSignInQRException) {
                    false
                }
            }

            is HWWalletEnrollment.Ledger -> {
                ledgerAccountImporter.hasPendingPairing()
            }

            is HWWalletEnrollment.ViewOnly -> {
                viewOnlyAccountImporter.hasPendingImport()
            }
        }

    /**
     * Imports the hardware-wallet account being enrolled at [birthday]. A view-only import is not
     * imported here: its birthday is stored with the pending key and the flow goes on to name the
     * account, which imports it. A Ledger pairing or a pending view-only key that went missing since
     * the screen opened cannot come back by trying again, so the user is returned to the wallet root
     * instead of an error that offers a retry.
     */
    suspend operator fun invoke(
        enrollment: HWWalletEnrollment,
        birthday: BlockHeight?
    ) {
        when (enrollment) {
            is HWWalletEnrollment.Keystone -> {
                val accounts = parseKeystoneUrToZashiAccounts(enrollment.ur)
                val account = accounts.accounts.firstOrNull() ?: throw InitializeException.NoAccountLoaded
                createKeystoneAccount(accounts, account, birthday)
            }

            is HWWalletEnrollment.Ledger -> {
                try {
                    ledgerAccountImporter.importAccount(birthday)
                } catch (_: LedgerPairingMissingException) {
                    navigationRouter.backToRoot()
                }
            }

            is HWWalletEnrollment.ViewOnly -> {
                try {
                    viewOnlyAccountImporter.continueWithBirthday(birthday)
                } catch (_: ViewOnlyPendingImportMissingException) {
                    navigationRouter.backToRoot()
                }
            }
        }
    }
}
