package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.styledStringResource

@Composable
fun ZashiMessage(state: ZashiMessageState) {
    ZashiCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = ZashiColors.Utility.WarningYellow.utilityOrange50,
                contentColor = ZashiColors.Utility.WarningYellow.utilityOrange800,
            ),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row {
            Image(
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Utility.WarningYellow.utilityOrange500)
            )
            Spacer(12.dp)
            Column {
                Spacer(2.dp)
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Utility.WarningYellow.utilityOrange700
                )
                Spacer(8.dp)
                Text(
                    text = state.text.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Utility.WarningYellow.utilityOrange800
                )
            }
        }
    }
}

data class ZashiMessageState(
    val title: StringResource,
    val text: StyledStringResource,
    val type: Type = Type.WARNING
) {
    enum class Type {
        WARNING,
        INFO
    }

    companion object {
        val preview =
            ZashiMessageState(
                stringRes("Title"),
                styledStringResource(stringRes("Text")),
            )
    }
}

@PreviewScreens
@Composable
private fun ZashiMessagePreview() =
    ZcashTheme {
        ZashiMessage(ZashiMessageState.preview)
    }
