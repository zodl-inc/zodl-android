package co.electriccoin.zcash.ui.screen.migration.battery

data class MigrationBatteryState(
    val onAllow: () -> Unit,
    val onSkip: () -> Unit,
    val onAutoSkip: () -> Unit,
    val onBack: () -> Unit,
)
