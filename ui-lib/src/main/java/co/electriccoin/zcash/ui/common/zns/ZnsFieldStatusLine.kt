package co.electriccoin.zcash.ui.common.zns

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Ellipsize
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue

/** The line a recipient field shows under itself while a typed name is looked up, or once it resolved. */
sealed interface ZnsFieldStatus {
    data object LookingUp : ZnsFieldStatus

    data class Resolved(
        val address: String
    ) : ZnsFieldStatus
}

/**
 * Shows [status] under a recipient field, in the field's message position. Errors use the field's own error line, so
 * this only draws the in-progress and success states.
 */
@Composable
fun ZnsFieldStatusLine(
    status: ZnsFieldStatus,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier = Modifier.height(6.dp))
    when (status) {
        ZnsFieldStatus.LookingUp -> {
            Text(
                text = stringResource(R.string.zns_lookingUp),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                modifier = modifier.testTag(ZnsFieldStatusTag.LOOKING_UP)
            )
        }

        is ZnsFieldStatus.Resolved -> {
            Text(
                text =
                    stringResource(
                        R.string.zns_resolvesTo,
                        StringResource.ByAddress(status.address, Ellipsize.MIDDLE).getValue()
                    ),
                style = ZashiTypography.textSm,
                color = ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                modifier = modifier.testTag(ZnsFieldStatusTag.RESOLVED)
            )
        }
    }
}

object ZnsFieldStatusTag {
    const val LOOKING_UP = "zns_looking_up"
    const val RESOLVED = "zns_resolved"
}
