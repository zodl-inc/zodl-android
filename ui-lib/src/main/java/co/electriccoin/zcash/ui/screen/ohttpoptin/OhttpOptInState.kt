package co.electriccoin.zcash.ui.screen.ohttpoptin

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.design.util.StringResource

data class OhttpOptInState(
    val title: StringResource,
    val body: StringResource,
    val items: List<OhttpOptInItemState>,
    val notNowText: StringResource,
    val turnOnText: StringResource,
    val onTurnOn: () -> Unit,
    val onNotNow: () -> Unit,
    val onBack: () -> Unit,
)

data class OhttpOptInItemState(
    @param:DrawableRes val icon: Int,
    val title: StringResource,
    val subtitle: StringResource,
)
