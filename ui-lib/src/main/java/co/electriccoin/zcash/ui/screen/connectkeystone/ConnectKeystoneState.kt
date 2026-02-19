package co.electriccoin.zcash.ui.screen.connectkeystone

data class ConnectKeystoneState(
    val onViewKeystoneTutorialClicked: () -> Unit,
    val onBackClick: () -> Unit,
    val onContinueClick: () -> Unit
)
