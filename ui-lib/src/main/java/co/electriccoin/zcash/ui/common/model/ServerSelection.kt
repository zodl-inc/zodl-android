package co.electriccoin.zcash.ui.common.model

import co.electriccoin.lightwallet.client.model.LightWalletEndpoint

sealed interface ServerSelection {
    data object Automatic : ServerSelection

    data class Manual(
        val endpoint: LightWalletEndpoint
    ) : ServerSelection
}
