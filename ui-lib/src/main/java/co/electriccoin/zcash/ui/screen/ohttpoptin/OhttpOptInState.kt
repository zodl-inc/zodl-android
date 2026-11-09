package co.electriccoin.zcash.ui.screen.ohttpoptin

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

data class OhttpOptInState(
    val title: StringResource,
    val body: StringResource,
    val items: List<OhttpOptInItemState>,
    val notNowText: StringResource,
    val turnOnText: StringResource,
    val onTurnOn: () -> Unit,
    val onNotNow: () -> Unit,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            OhttpOptInState(
                title = stringRes(R.string.ohttpOptIn_title),
                body = stringRes(R.string.ohttpOptIn_body),
                items =
                    listOf(
                        OhttpOptInItemState(
                            icon = ImageResource.ByDrawable(R.drawable.ic_ohttp_lock),
                            title = stringRes(R.string.ohttpInfo_item1Title),
                            subtitle = stringRes(R.string.ohttpInfo_item1Body),
                        ),
                    ),
                notNowText = stringRes(R.string.ohttpOptIn_notNow),
                turnOnText = stringRes(R.string.ohttpOptIn_turnOn),
                onTurnOn = {},
                onNotNow = {},
                onBack = {},
            )
    }
}

data class OhttpOptInItemState(
    val icon: ImageResource.ByDrawable,
    val title: StringResource,
    val subtitle: StringResource,
)
