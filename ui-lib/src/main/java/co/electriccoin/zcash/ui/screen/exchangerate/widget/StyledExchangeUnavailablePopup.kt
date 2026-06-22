package co.electriccoin.zcash.ui.screen.exchangerate.widget

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipScope
import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.compose.ZashiTooltip
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TooltipScope.StyledExchangeUnavailablePopup(
    onDismissRequest: () -> Unit,
) {
    ZashiTooltip(
        title = stringRes(R.string.tooltip_exchangeRate_title),
        message = stringRes(R.string.tooltip_exchangeRate_desc),
        onDismissRequest = onDismissRequest
    )
}
