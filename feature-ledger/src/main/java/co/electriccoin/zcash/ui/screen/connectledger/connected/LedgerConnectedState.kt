package co.electriccoin.zcash.ui.screen.connectledger.connected

data class LedgerConnectedState(
    val onBack: () -> Unit,
) {
    companion object {
        val preview = LedgerConnectedState(onBack = {})
    }
}
