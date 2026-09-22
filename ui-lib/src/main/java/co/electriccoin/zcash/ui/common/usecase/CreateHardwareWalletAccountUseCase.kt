package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.screen.connecthardware.HardwareWalletEnrollment

/**
 * The one place the shared enrollment screens dispatch on vendor: everything else about them —
 * the birthday question, the estimation, the manual height, the Lce wiring — is identical.
 */
class CreateHardwareWalletAccountUseCase(
    private val parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
    private val createKeystoneAccount: CreateKeystoneAccountUseCase,
    private val createLedgerAccount: CreateLedgerAccountUseCase,
    private val ledgerPairingRepository: LedgerPairingRepository,
) {
    /**
     * Whether [enrollment] still carries what the import needs. A Ledger pairing lives in memory
     * only and is gone after process death; a Keystone UR travels in the route but can be
     * unusable. Either way the screens have nothing to offer, and send the user back to the root
     * instead of a form that cannot be submitted.
     */
    fun isReady(enrollment: HardwareWalletEnrollment): Boolean =
        when (enrollment) {
            is HardwareWalletEnrollment.Keystone -> {
                try {
                    parseKeystoneUrToZashiAccounts(enrollment.ur).accounts.isNotEmpty()
                } catch (_: InvalidKeystoneSignInQRException) {
                    false
                }
            }

            is HardwareWalletEnrollment.Ledger -> {
                ledgerPairingRepository.get() != null
            }
        }

    suspend operator fun invoke(
        enrollment: HardwareWalletEnrollment,
        birthday: BlockHeight?
    ) {
        when (enrollment) {
            is HardwareWalletEnrollment.Keystone -> {
                val accounts = parseKeystoneUrToZashiAccounts(enrollment.ur)
                val account = accounts.accounts.firstOrNull() ?: throw InitializeException.NoAccountLoaded
                createKeystoneAccount(accounts, account, birthday)
            }

            is HardwareWalletEnrollment.Ledger -> {
                createLedgerAccount(birthday)
            }
        }
    }
}
