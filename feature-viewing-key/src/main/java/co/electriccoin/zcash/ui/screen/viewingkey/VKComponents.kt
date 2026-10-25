package co.electriccoin.zcash.ui.screen.viewingkey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue

/**
 * The body of the flow's alert sheets: [icon] in a circle, a centred title and message, then
 * [buttons].
 */
@Composable
internal fun ColumnScope.VKSheetContent(
    icon: ImageResource.ByDrawable,
    title: StringResource,
    message: StringResource,
    contentPadding: PaddingValues,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .weight(1f, false)
                .padding(horizontal = 24.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .background(ZashiColors.Surfaces.bgPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            icon.Compose(modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title.getValue(),
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        buttons()
    }
}
