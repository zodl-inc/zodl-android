package co.electriccoin.zcash.ui.screen.migration.notification

data class MigrationNotificationState(
    val onAllow: () -> Unit,
    val onSkip: () -> Unit,
    val onAutoSkip: () -> Unit,
    val onBack: () -> Unit,
)
