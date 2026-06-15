package co.electriccoin.zcash.ui.screen.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiBulletText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiCheckbox
import co.electriccoin.zcash.ui.design.component.ZashiCheckboxDefaults
import co.electriccoin.zcash.ui.design.component.ZashiDisclaimer
import co.electriccoin.zcash.ui.design.component.ZashiDisclaimerState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun KeepOpenView(state: KeepOpenState) {
    BlankBgScaffold { paddingValues ->
        Content(
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(paddingValues)
                    .verticalScroll(rememberScrollState())
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun Content(
    state: KeepOpenState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        VerticalSpacer(64.dp)

        Image(
            painter = painterResource(R.drawable.img_success_dialog),
            contentDescription = null,
        )

        VerticalSpacer(24.dp)

        Text(
            text = state.title.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )

        VerticalSpacer(8.dp)

        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium
        )

        VerticalSpacer(16.dp)

        Text(
            text = state.description.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )

        VerticalSpacer(ZashiDimensions.Spacing.spacingLg)

        ZashiBulletText(
            listOf(
                state.bullet1.getValue(),
                state.bullet2.getValue()
            ),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )

        VerticalSpacer(ZashiDimensions.Spacing.spacingLg)

        ZashiDisclaimer(state = state.disclaimer)

        Spacer(1f)

        ZashiCheckbox(
            modifier = Modifier.align(Alignment.Start),
            isChecked = state.isChecked,
            onClick = { state.onCheckedChange(!state.isChecked) },
            text = state.checkboxLabel,
            textStyles =
                ZashiCheckboxDefaults.textStyles(
                    title =
                        ZashiTypography.textMd.copy(
                            fontWeight = FontWeight.Medium,
                            color = ZashiColors.Text.textPrimary,
                        )
                )
        )

        VerticalSpacer(14.dp)

        ZashiButton(
            state = state.button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewScreens
@Composable
private fun KeepOpenViewPreview() =
    ZcashTheme {
        KeepOpenView(
            KeepOpenState(
                description = stringRes(R.string.keep_open_restore_description),
                subtitle = stringRes(R.string.keep_open_restore_subtitle),
                disclaimer = ZashiDisclaimerState.warning(stringRes(R.string.keep_open_keystone_warning)),
                checkboxLabel = stringRes(R.string.keep_open_restore_checkbox),
                isChecked = true,
                onCheckedChange = { },
                button =
                    ButtonState(
                        text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_got_it),
                        onClick = { },
                    ),
            )
        )
    }
