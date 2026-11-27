package co.electriccoin.zcash.ui.screen.migration.customservertor

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationCustomServerTorState(
    val body: StringResource,
    val riskBody: StringResource,
    val onContinueWithoutTor: () -> Unit,
    val onSwitchServer: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
