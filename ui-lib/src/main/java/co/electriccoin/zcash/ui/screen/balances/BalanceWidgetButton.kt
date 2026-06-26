package co.electriccoin.zcash.ui.screen.balances

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.LottieProgress
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.toButtonColors
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orHiddenString
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
internal fun BalanceWidgetButton(
    state: BalanceButtonState,
    modifier: Modifier = Modifier,
) {
    val colors =
        ZashiButtonDefaults.secondaryColors(
            containerColor = ZashiColors.Surfaces.bgPrimary,
            borderColor = ZashiColors.Utility.Gray.utilityGray100
        )
    val borderColor = colors.borderColor

    Button(
        onClick = state.onClick,
        modifier = modifier,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusLg),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = colors.toButtonColors(),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 1.dp,
                pressedElevation = 0.dp
            ),
        border = borderColor.takeIf { it != Color.Unspecified }?.let { BorderStroke(1.dp, it) },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(state.icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary)
                )
                Spacer(4.dp)
                Text(
                    text = state.text.getValue(),
                    color = ZashiColors.Btns.Tertiary.btnTertiaryFg,
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.amount != null) {
                    Spacer(6.dp)
                    Image(
                        modifier = Modifier.padding(top = 1.dp),
                        painter = painterResource(R.drawable.ic_balance_zec_small),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary)
                    )
                    Spacer(3.dp)
                    Text(
                        text =
                            stringRes(state.amount, HIDDEN) orHiddenString
                                stringRes(co.electriccoin.zcash.ui.design.R.string.general_hideBalancesMost),
                        color = ZashiColors.Btns.Tertiary.btnTertiaryFg,
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Spacer(6.dp)
                    LottieProgress(
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    )
}

data class BalanceButtonState(
    @field:DrawableRes val icon: Int,
    val text: StringResource,
    val amount: Zatoshi?,
    val onClick: () -> Unit
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            BalanceWidgetButton(
                state =
                    BalanceButtonState(
                        icon = R.drawable.ic_help,
                        text = stringRes("text"),
                        amount = Zatoshi(1000),
                        onClick = {}
                    )
            )
        }
    }
