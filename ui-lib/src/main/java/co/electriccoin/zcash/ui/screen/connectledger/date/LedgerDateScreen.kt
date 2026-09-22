package co.electriccoin.zcash.ui.screen.connectledger.date

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.BirthdayPickerView
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun LedgerFirstTransactionScreen() {
    val vm = koinViewModel<LedgerDateVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        BirthdayPickerView(it)
    }
}

@Serializable
data object LedgerDateArgs
