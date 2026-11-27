package co.electriccoin.zcash.ui.screen.migration.howitworks

data class MigrationHowItWorksState(
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
)
