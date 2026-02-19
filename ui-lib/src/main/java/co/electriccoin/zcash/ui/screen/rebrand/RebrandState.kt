package co.electriccoin.zcash.ui.screen.rebrand

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState

@Immutable
data class RebrandState(
    val info: ButtonState,
    val next: ButtonState,
)
