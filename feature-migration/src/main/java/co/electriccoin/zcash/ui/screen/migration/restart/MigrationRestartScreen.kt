package co.electriccoin.zcash.ui.screen.migration.restart

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import org.koin.androidx.compose.koinViewModel

@Composable
fun MigrationRestartScreen() {
    val vm = koinViewModel<MigrationRestartVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { s ->
        BackHandler { s.onBack() }
        MigrationRestartView(s)
    }
}
