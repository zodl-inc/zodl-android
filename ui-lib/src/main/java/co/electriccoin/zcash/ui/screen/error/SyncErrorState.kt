package co.electriccoin.zcash.ui.screen.error

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.listitem.SimpleListItemState
import co.electriccoin.zcash.ui.design.util.StringResource

data class SyncErrorState(
    val tryAgain: ButtonState,
    val switchServer: ButtonState,
    val disableTor: ButtonState?,
    val support: ButtonState,
    override val onBack: () -> Unit,
    val diagnostics: SyncErrorDiagnosticsState? = null
) : ModalBottomSheetState

/**
 * Extra detail shown only for failures the user can act on knowingly, currently the
 * server-compatibility family. It is null for generic or transient sync errors, where retrying is
 * the right remedy and naming internals would only be noise.
 */
data class SyncErrorDiagnosticsState(
    val explanation: StringResource,
    val facts: List<SimpleListItemState>
)
