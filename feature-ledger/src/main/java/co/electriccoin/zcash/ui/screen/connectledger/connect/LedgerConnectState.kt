package co.electriccoin.zcash.ui.screen.connectledger.connect

data class LedgerConnectState(
    val onBack: () -> Unit,
    val onContinueClick: () -> Unit,
) {
    companion object {
        val preview =
            LedgerConnectState(
                onBack = {},
                onContinueClick = {},
            )
    }
}
