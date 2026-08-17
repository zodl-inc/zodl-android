package co.electriccoin.zcash.ui.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

private val CONTENT_TOP_SPACING = 28.dp

private val CONTENT_BOTTOM_SPACING = 20.dp

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
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState)
            ) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.systemBars))
                Button(
                    contentPadding = PaddingValues(0.dp),
                    modifier =
                        Modifier
                            .padding(
                                start = ZashiDimensions.Spacing.spacing3xl,
                                top = ZashiDimensions.Spacing.spacingLg
                            ).size(40.dp),
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
            }
        },
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedFooter(hazeState)
                        .padding(horizontal = ZashiDimensions.Spacing.spacing3xl)
            ) {
                footer()
                Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacing3xl))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(
                            paddingValues = paddingValues,
                            top = paddingValues.calculateTopPadding() + CONTENT_TOP_SPACING,
                            bottom = paddingValues.calculateBottomPadding() + CONTENT_BOTTOM_SPACING
                        )
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
        }
    }
}
