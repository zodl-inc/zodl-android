package co.electriccoin.zcash.ui.screen.connecthw

import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerPairingMissingException
import co.electriccoin.zcash.ui.common.usecase.CreateHWWalletAccountUseCase

/**
 * Imports the account being enrolled at [birthday]. A Ledger pairing that went missing since the
 * screen opened cannot come back by trying again, so the user is returned to the wallet root
 * instead of an error that offers a retry.
 */
internal suspend fun CreateHWWalletAccountUseCase.importOrReturnToRoot(
    enrollment: HWWalletEnrollment,
    birthday: BlockHeight?,
    navigationRouter: NavigationRouter,
) {
    try {
        invoke(enrollment, birthday)
    } catch (_: LedgerPairingMissingException) {
        navigationRouter.backToRoot()
    }
}
