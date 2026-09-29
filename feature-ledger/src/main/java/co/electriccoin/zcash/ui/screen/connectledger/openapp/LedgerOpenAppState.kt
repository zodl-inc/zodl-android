package co.electriccoin.zcash.ui.screen.connectledger.openapp

data class LedgerOpenAppState(
    val onBackClick: () -> Unit,
    val onContinueClick: () -> Unit,
) {
    companion object {
        val preview =
            LedgerOpenAppState(
                onBackClick = {},
                onContinueClick = {},
            )
    }
}
