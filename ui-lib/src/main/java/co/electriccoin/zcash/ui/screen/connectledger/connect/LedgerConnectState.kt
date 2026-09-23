package co.electriccoin.zcash.ui.screen.connectledger.connect

data class LedgerConnectState(
    val onBackClick: () -> Unit,
    val onContinueClick: () -> Unit,
) {
    companion object {
        val preview =
            LedgerConnectState(
                onBackClick = {},
                onContinueClick = {},
            )
    }
}
