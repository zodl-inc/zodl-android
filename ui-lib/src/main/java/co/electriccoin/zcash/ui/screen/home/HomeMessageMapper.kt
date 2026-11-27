package co.electriccoin.zcash.ui.screen.home

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.asPrivacySensitive
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.shieldfunds.ShieldFundsMessageState
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER

class HomeMessageMapper {
    fun createState(
        data: HomeMessageData.ShieldFunds,
        isShieldFundsInfoEnabled: Boolean,
        onClick: () -> Unit,
        onButtonClick: () -> Unit,
    ) = ShieldFundsMessageState(
        subtitle =
            stringRes(
                R.string.home_message_transparent_balance_subtitle,
                stringRes(data.zatoshi, HIDDEN).asPrivacySensitive(),
                CURRENCY_TICKER
            ),
        onClick = onClick.takeIf { isShieldFundsInfoEnabled },
        onButtonClick = onButtonClick,
    )
}
