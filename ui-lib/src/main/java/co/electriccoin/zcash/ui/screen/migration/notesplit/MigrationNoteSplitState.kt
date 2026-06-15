package co.electriccoin.zcash.ui.screen.migration.notesplit

import co.electriccoin.zcash.ui.design.util.StringResource

enum class NoteSplitPhase { EXPLAINER, IN_PROGRESS, COMPLETE }

data class MigrationNoteSplitState(
    val phase: NoteSplitPhase,
    val isKeystone: Boolean,
    val splitAmount: StringResource,
    val fee: StringResource,
    val transactionId: StringResource?,
    val onCopyTransactionId: () -> Unit,
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
)
