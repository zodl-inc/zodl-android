package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.contact.AddZashiABContactArgs
import co.electriccoin.zcash.ui.screen.scan.ScanArgs
import co.electriccoin.zcash.ui.screen.scan.ScanFlow.ADDRESS_BOOK
import co.electriccoin.zcash.ui.screen.scan.ScanFlow.HOMEPAGE
import co.electriccoin.zcash.ui.screen.scan.ScanFlow.SEND
import co.electriccoin.zcash.ui.screen.send.Send

class OnAddressScannedUseCase(
    private val navigationRouter: NavigationRouter,
    private val prefillSend: PrefillSendUseCase,
    private val rejectIfSelectedAccountCannotSpend: RejectIfSelectedAccountCannotSpendUseCase,
) {
    /**
     * An address scanned from home opens the send form, so an account that cannot spend gets the
     * unsupported-operation error in place of the scanner instead.
     */
    suspend operator fun invoke(
        address: String,
        addressType: AddressType,
        scanArgs: ScanArgs
    ) {
        require(addressType is AddressType.Valid)

        when (scanArgs.flow) {
            SEND -> {
                prefillSend.request(PrefillSendData.FromAddressScan(address = address))
                navigationRouter.back()
            }

            ADDRESS_BOOK -> {
                navigationRouter.replace(AddZashiABContactArgs(address))
            }

            HOMEPAGE -> {
                if (rejectIfSelectedAccountCannotSpend { replace(it) }) return
                navigationRouter.replace(
                    Send(
                        address,
                        when (addressType) {
                            AddressType.Shielded -> cash.z.ecc.sdk.model.AddressType.UNIFIED
                            AddressType.Tex -> cash.z.ecc.sdk.model.AddressType.TEX
                            AddressType.Transparent -> cash.z.ecc.sdk.model.AddressType.TRANSPARENT
                            AddressType.Unified -> cash.z.ecc.sdk.model.AddressType.UNIFIED
                            else -> cash.z.ecc.sdk.model.AddressType.UNIFIED
                        }
                    )
                )
            }
        }
    }
}
