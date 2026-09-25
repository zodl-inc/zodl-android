package co.electriccoin.zcash.ui.screen.home.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.screen.transactionhistory.EMPTY_GRADIENT_THRESHOLD

@Composable
fun CommonEmptyScreen(
    modifier: Modifier = Modifier,
    shimmerColor: Color = ZashiColors.Surfaces.bgSecondary
) {
    Box(
        modifier = modifier
    ) {
        CommonShimmerLoadingScreen(
            modifier = Modifier.padding(top = 22.dp),
            shimmerItemsCount = 3,
            color = shimmerColor,
            disableShimmer = true,
            showDivider = false,
            contentPaddingValues = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                EMPTY_GRADIENT_THRESHOLD to ZashiColors.Surfaces.bgPrimary,
                                1f to ZashiColors.Surfaces.bgPrimary,
                            )
                    ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(118.dp))
            Image(
                painter = painterResource(R.drawable.ic_transaction_widget_empty),
                contentDescription = null,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(co.electriccoin.zcash.ui.design.R.string.common_empty_screen_title),
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.textLg,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(co.electriccoin.zcash.ui.design.R.string.common_empty_screen_subtitle),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm,
            )
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            CommonEmptyScreen(modifier = Modifier.fillMaxWidth())
        }
    }
