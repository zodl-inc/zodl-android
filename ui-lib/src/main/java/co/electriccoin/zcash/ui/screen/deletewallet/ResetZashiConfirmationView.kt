package co.electriccoin.zcash.ui.screen.deletewallet

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetZashiConfirmationView(state: ZashiConfirmationState?) {
    ZashiConfirmationBottomSheet(state = state)
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun ResetZashiConfirmationPreview() =
    ZcashTheme {
        ResetZashiConfirmationView(state = null)
    }
