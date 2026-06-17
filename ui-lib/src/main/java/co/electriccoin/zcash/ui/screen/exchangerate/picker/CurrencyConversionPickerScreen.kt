package co.electriccoin.zcash.ui.screen.exchangerate.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

@Composable
fun CurrencyConversionPickerScreen(args: CurrencyConversionPickerArgs) {
    val vm = koinViewModel<CurrencyConversionPickerVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    CurrencyConversionPickerView(state)
}

@Serializable
data class CurrencyConversionPickerArgs(
    val selectedCode: String,
    val requestId: String = UUID.randomUUID().toString()
)
