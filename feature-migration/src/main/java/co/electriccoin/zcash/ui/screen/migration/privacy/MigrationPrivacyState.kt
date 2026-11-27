package co.electriccoin.zcash.ui.screen.migration.privacy

import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationPrivacyState(
    val body: StringResource,
    val checkbox: CheckboxState,
    val onConfirm: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
