package co.electriccoin.zcash.ui.screen.ohttpoptin

data class OhttpOptInState(
    val onTurnOn: () -> Unit,
    val onNotNow: () -> Unit,
    val onBack: () -> Unit,
)
