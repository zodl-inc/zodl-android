package co.electriccoin.zcash.ui.screen.qrcode.ext

import cash.z.ecc.android.sdk.model.WalletAddress
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType

internal suspend fun WalletAccount.fromReceiveAddressType(receiveAddressType: ReceiveAddressType): WalletAddress? =
    when (receiveAddressType) {
        ReceiveAddressType.Unified -> WalletAddress.Unified.new(unifiedAddress)
        ReceiveAddressType.Sapling -> saplingAddress?.let { WalletAddress.Sapling.new(it) }
        ReceiveAddressType.Transparent -> WalletAddress.Transparent.new(transparentAddress)
    }
