package co.electriccoin.zcash.ui.screen.connectledger.turnon

data class LedgerTurnOnState(
    val onBack: () -> Unit,
    val onContinueClick: () -> Unit,
) {
    companion object {
        val preview =
            LedgerTurnOnState(
                onBack = {},
                onContinueClick = {},
            )
    }
}
