package co.electriccoin.zcash.ui.screen.connectledger.turnon

data class LedgerTurnOnState(
    val onBackClick: () -> Unit,
    val onContinueClick: () -> Unit,
) {
    companion object {
        val preview =
            LedgerTurnOnState(
                onBackClick = {},
                onContinueClick = {},
            )
    }
}
