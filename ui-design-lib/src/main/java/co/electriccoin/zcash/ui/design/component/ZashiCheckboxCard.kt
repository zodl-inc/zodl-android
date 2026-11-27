package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ZashiCheckboxCard(
    state: CheckboxState,
    modifier: Modifier = Modifier,
    colors: ZashiCheckboxCardColors = ZashiCheckboxCardDefaults.colors(),
    contentPadding: PaddingValues = ZashiCheckboxCardDefaults.contentPadding,
    textStyles: CheckboxTextStyles = ZashiCheckboxCardDefaults.textStyles(),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        color = colors.container,
        border = BorderStroke(1.dp, colors.border).takeIf { colors.border.isSpecified },
    ) {
        ZashiCheckbox(
            state = state,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ZashiCheckboxCardDefaults.minHeight),
            spacing = ZashiCheckboxCardDefaults.spacing,
            contentPadding = contentPadding,
            textStyles = textStyles
        )
    }
}

@Immutable
data class ZashiCheckboxCardColors(
    val container: Color,
    val border: Color,
)

object ZashiCheckboxCardDefaults {
    val spacing = 16.dp

    /** The smallest tap target a row keeps even when its padding is reduced. */
    val minHeight = 32.dp

    val contentPadding = PaddingValues(16.dp)

    @Composable
    fun colors(
        container: Color = ZashiColors.Surfaces.bgPrimary,
        border: Color = ZashiColors.Surfaces.strokeSecondary,
    ) = ZashiCheckboxCardColors(container = container, border = border)

    @Composable
    fun textStyles(
        title: TextStyle =
            ZashiTypography.textSm.copy(
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary
            ),
        subtitle: TextStyle =
            ZashiTypography.textSm.copy(
                color = ZashiColors.Text.textTertiary
            ),
    ) = CheckboxTextStyles(title = title, subtitle = subtitle)
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZashiCheckboxCard(
                state =
                    CheckboxState(
                        title = stringRes("title"),
                        subtitle = stringRes("subtitle"),
                        isChecked = false,
                        onClick = {}
                    )
            )
        }
    }
