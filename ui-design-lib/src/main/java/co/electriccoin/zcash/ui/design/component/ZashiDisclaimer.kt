package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.design.util.withStyle

@Composable
fun ZashiDisclaimer(
    state: ZashiDisclaimerState,
    modifier: Modifier = Modifier,
) {
    ZashiCard(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = ZashiColors.Utility.WarningYellow.utilityOrange50,
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = state.value.getValue(),
            style = ZashiTypography.textXs,
            color = ZashiColors.Utility.WarningYellow.utilityOrange900,
        )
    }
}

data class ZashiDisclaimerState(
    val value: StyledStringResource
) {
    companion object {
        fun warning(text: StyledStringResource) =
            ZashiDisclaimerState(
                styledStringResource(
                    R.string.general_warning,
                    StyledStringStyle(color = StringResourceColor.WARNING, fontWeight = FontWeight.Bold),
                ) + text
            )

        fun warning(text: StringResource) =
            ZashiDisclaimerState(
                styledStringResource(
                    R.string.general_warning,
                    StyledStringStyle(color = StringResourceColor.WARNING, fontWeight = FontWeight.Bold),
                ) +
                    text.withStyle(
                        StyledStringStyle(
                            color = StringResourceColor.WARNING,
                            fontWeight = FontWeight.Normal
                        )
                    )
            )
    }
}

@PreviewScreens
@Composable
private fun ZashiDisclaimerPreview() =
    ZcashTheme {
        ZashiDisclaimer(ZashiDisclaimerState.warning(stringRes("Test")))
    }
