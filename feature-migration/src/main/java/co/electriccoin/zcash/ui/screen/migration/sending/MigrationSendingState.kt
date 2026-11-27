package co.electriccoin.zcash.ui.screen.migration.sending

import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferFailureState

data class MigrationSendingState(
    val failureSheet: MigrationTransferFailureState? = null,
    val onBack: () -> Unit = {},
)
