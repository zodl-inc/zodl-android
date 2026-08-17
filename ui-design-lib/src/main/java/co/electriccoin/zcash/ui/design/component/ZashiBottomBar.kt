package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ZashiBottomBar(
    isElevated: Boolean,
    modifier: Modifier = Modifier,
    color: Color = ZashiColors.Surfaces.bgPrimary,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shadowElevation = if (isElevated) 4.dp else 0.dp,
        color = color,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            content()
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }
}

@PreviewScreens
@Composable
private fun BottomBarPreview() =
    ZcashTheme {
        ZashiBottomBar(
            isElevated = true
        ) {
            ZashiButton(
                state = ButtonState(text = stringRes("Save Button")),
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
            )
        }
    }
