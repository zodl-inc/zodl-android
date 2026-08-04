package co.electriccoin.zcash.ui.screen.ironwood

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun IronwoodAnnouncementScreen() {
    val vm = koinViewModel<IronwoodAnnouncementVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    // Consume Android back: this is a one-time screen the user must dismiss via the primary button.
    BackHandler { }
    IronwoodAnnouncementView(state = state)
}
