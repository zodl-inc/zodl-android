package co.electriccoin.zcash.ui.screen.restore.tor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreTorView(state: RestoreTorState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        content = { state, contentPadding ->
            Content(
                modifier = Modifier.weight(1f, false),
                state = state,
                contentPadding = contentPadding
            )
        }
    )
}

@Composable
private fun Content(
    state: RestoreTorState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                )
    ) {
        Image(
            painter = painterResource(R.drawable.ic_restore_tor_info),
            contentDescription = null
        )
        Spacer(12.dp)
        Text(
            stringResource(R.string.restore_tor_title),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(8.dp)
        Text(
            stringResource(R.string.restore_tor_subtitle),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(24.dp)
        Switch(state.checkbox)
        Spacer(32.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors(),
            state = state.secondary,
        )
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.primary
        )
    }
}

@Suppress("MagicNumber")
@Composable
private fun Switch(state: CheckboxState) {
    val borderColor by animateColorAsState(
        if (state.isChecked) {
            ZashiColors.Utility.Gray.utilityGray900
        } else {
            ZashiColors.Surfaces.strokeSecondary
        }
    )

    Surface(
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        onClick = { state.onClick() }
    ) {
        Row(
            modifier = Modifier.padding(20.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                state.subtitle?.let {
                    Spacer(2.dp)
                    Text(
                        text = it.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary
                    )
                }
            }
            Spacer(22.dp)
            val switchColor by animateColorAsState(
                if (state.isChecked) {
                    ZashiColors.Utility.SuccessGreen.utilitySuccess600
                } else {
                    ZashiColors.Utility.Gray.utilityGray200
                }
            )
            val offset by animateDpAsState(if (state.isChecked) 21.dp else 0.dp)
            Surface(
                modifier =
                    Modifier
                        .width(64.dp)
                        .height(28.dp),
                color = switchColor,
                shape = CircleShape,
            ) {
                Box(
                    modifier = Modifier.padding(2.dp)
                ) {
                    Box(
                        modifier =
                            Modifier
                                .offset(x = offset)
                                .width(39.dp)
                                .height(24.dp)
                                .clip(CircleShape)
                                .background(ZashiColors.Surfaces.bgPrimary)
                    )
                }
            }
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        var isChecked by remember { mutableStateOf(true) }

        RestoreTorView(
            state =
                RestoreTorState(
                    checkbox =
                        CheckboxState(
                            title = stringRes(stringResource(R.string.restore_tor_checkbox_title)),
                            subtitle = stringRes(stringResource(R.string.restore_tor_checkbox_subtitle)),
                            isChecked = isChecked,
                            onClick = { isChecked = !isChecked }
                        ),
                    secondary = ButtonState(stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel)),
                    primary = ButtonState(stringRes(R.string.restore_bd_restore_btn)),
                    onBack = { }
                )
        )
    }
