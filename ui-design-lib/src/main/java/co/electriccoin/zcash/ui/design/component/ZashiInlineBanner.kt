package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue

/**
 * Non-blocking inline banner: a warning icon, a title and a subtitle (Figma 9043:2986). It is not timed and
 * has no actions; the owning screen decides when to show and clear it.
 */
@Composable
fun ZashiInlineBanner(
    state: ZashiInlineBannerState,
    modifier: Modifier = Modifier,
) {
    ZashiCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row {
            Image(
                painter = painterResource(R.drawable.ic_alert_circle_outline),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary)
            )
            Spacer(12.dp)
            Column {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textTertiary
                )
                Text(
                    text = state.subtitle.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary
                )
            }
        }
    }
}

@Immutable
data class ZashiInlineBannerState(
    val title: StringResource,
    val subtitle: StringResource,
)

@PreviewScreens
@Composable
private fun ZashiInlineBannerPreview() =
    ZcashTheme {
        BlankSurface {
            ZashiInlineBanner(
                state = maxFailedBannerState(),
                modifier = Modifier.padding(ZcashTheme.dimens.spacingDefault)
            )
        }
    }
