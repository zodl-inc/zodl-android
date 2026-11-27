package co.electriccoin.zcash.ui.screen.migration.keystonesign

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.screen.migration.component.MigrationFailureBottomSheet
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class MigrationKeystoneSignArgs(
    val mode: MigrationMode,
)

@Composable
fun MigrationKeystoneSignScreen(args: MigrationKeystoneSignArgs) {
    val vm = koinViewModel<MigrationKeystoneSignVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    val failureSheet by vm.failureSheet.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onBack?.invoke() }
    state?.let { SignKeystoneTransactionView(it) }
    MigrationFailureBottomSheet(failureSheet)
}
