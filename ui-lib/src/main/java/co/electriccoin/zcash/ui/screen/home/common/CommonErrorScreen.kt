package co.electriccoin.zcash.ui.screen.home.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.transactionhistory.EMPTY_GRADIENT_THRESHOLD

@Composable
fun CommonErrorScreen(
    state: CommonErrorScreenState,
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
            Spacer(118.dp)
            Image(
                painter = painterResource(R.drawable.img_common_error),
                contentDescription = null,
            )
            Spacer(20.dp)
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
                text = state.title.getValue(),
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.textLg,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(8.dp)
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
                text = state.subtitle.getValue(),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm,
            )
            Spacer(24.dp)
            ZashiButton(
                state = state.buttonState,
                defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors()
            )
        }
    }
}

interface CommonErrorScreenState {
    val title: StringResource
    val subtitle: StringResource
    val buttonState: ButtonState
}

data class CommonErrorScreenStateImpl(
    override val title: StringResource,
    override val subtitle: StringResource,
    override val buttonState: ButtonState
) : CommonErrorScreenState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            CommonErrorScreen(
                modifier = Modifier.fillMaxWidth(),
                state =
                    CommonErrorScreenStateImpl(
                        title = stringRes("Title"),
                        subtitle = stringRes("Subtitle"),
                        buttonState = ButtonState(stringRes("Button"))
                    )
            )
        }
    }
