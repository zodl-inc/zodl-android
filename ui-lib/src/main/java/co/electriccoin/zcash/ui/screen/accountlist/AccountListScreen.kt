package co.electriccoin.zcash.ui.screen.accountlist

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun AccountListScreen() {
    val viewModel = koinViewModel<AccountListVM>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { state.onBack() }
    AccountListView(state)
}

@Serializable
object AccountListArgs
