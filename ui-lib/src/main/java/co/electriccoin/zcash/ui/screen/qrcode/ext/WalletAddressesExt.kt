package co.electriccoin.zcash.ui.screen.qrcode.ext

import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType

internal fun WalletAccount.fromReceiveAddressType(receiveAddressType: ReceiveAddressType) =
    when (receiveAddressType) {
        ReceiveAddressType.Unified -> this.unifiedAddress
        ReceiveAddressType.Sapling -> this.saplingAddress
        ReceiveAddressType.Transparent -> this.transparentAddress
    }
