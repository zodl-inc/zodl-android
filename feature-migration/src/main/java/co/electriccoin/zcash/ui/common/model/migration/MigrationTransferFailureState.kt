package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.TransferResult
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.R as DesignR

data class MigrationTransferFailureState(
    val message: StringResource,
    // Null when the failure isn't safely resubmittable (e.g. a non-retryable SubmitResult) — the
    // shared bottom sheet omits the Retry button entirely in that case rather than silently
    // wiring it to mean "go back".
    val onRetry: (() -> Unit)?,
    val onDismiss: () -> Unit,
)

fun migrationFailureMessage(result: TransferResult): StringResource =
    when (result) {
        is TransferResult.NetworkError -> stringRes(DesignR.string.migrationFailureMessage_networkError)
        TransferResult.InvalidNote -> stringRes(DesignR.string.migrationFailureMessage_invalidNote)
        TransferResult.Expired -> stringRes(DesignR.string.migrationFailureMessage_expired)
        is TransferResult.Success -> error("migrationFailureMessage called with a Success result")
    }
