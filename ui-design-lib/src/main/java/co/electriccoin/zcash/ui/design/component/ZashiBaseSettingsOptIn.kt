package co.electriccoin.zcash.ui.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@Suppress("LongMethod", "ComposableParamOrder")
@Composable
fun ZashiBaseSettingsOptIn(
    header: String,
    @DrawableRes image: Int,
    info: String?,
    onDismiss: () -> Unit,
    imageSize: DpSize? = null,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(paddingValues)
        ) {
            Button(
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(40.dp),
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ZashiColors.Btns.Tertiary.btnTertiaryBg
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_settings_opt_int_close),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ZashiColors.Btns.Tertiary.btnTertiaryFg)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
            ) {
                Image(
                    modifier = if (imageSize != null) Modifier.size(imageSize) else Modifier,
                    painter = painterResource(image),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = header,
                    color = ZashiColors.Text.textPrimary,
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold
                )
                content()

                Spacer(modifier = Modifier.weight(1f))

                if (info != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ZashiInfoText(info)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            footer()
        }
    }
}
