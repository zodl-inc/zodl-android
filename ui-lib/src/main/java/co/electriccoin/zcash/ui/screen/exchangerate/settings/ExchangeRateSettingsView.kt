package co.electriccoin.zcash.ui.screen.exchangerate.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiBaseSettingsOptIn
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
internal fun ExchangeRateSettingsView(state: ExchangeRateSettingsState) {
    ZashiBaseSettingsOptIn(
        header = stringResource(id = R.string.currencyConversion_title),
        image = R.drawable.exchange_rate,
        onDismiss = state.onBack,
        info = state.info?.getValue(),
        content = {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.currencyConversion_settingsDesc),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Option(
                modifier = Modifier.fillMaxWidth(),
                isChecked = state.isOptedIn.isChecked,
                title = stringResource(R.string.torSetup_ccSheet_enable),
                subtitle = stringResource(R.string.currencyConversion_learnMoreOptionEnableDesc),
                onClick = state.isOptedIn.onClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            Option(
                modifier = Modifier.fillMaxWidth(),
                isChecked = state.isOptedOut.isChecked,
                title = stringResource(R.string.currencyConversion_learnMoreOptionDisable),
                subtitle = stringResource(R.string.currencyConversion_learnMoreOptionDisableDesc),
                onClick = state.isOptedOut.onClick
            )
            state.currencyField?.let { field ->
                Spacer(modifier = Modifier.height(24.dp))
                CurrencyField(
                    modifier = Modifier.fillMaxWidth(),
                    state = field
                )
            }
        },
        footer = {
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = state.saveButton,
                defaultPrimaryColors = ZashiButtonDefaults.primaryColors(),
            )
        },
    )
}

@Composable
fun Option(
    isChecked: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val clickAction =
        remember(isChecked, onClick) {
            if (isChecked) {
                onClick
            } else {
                {
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                    onClick()
                }
            }
        }

    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier =
            modifier
                .clip(shape)
                .background(
                    if (isChecked) ZashiColors.Surfaces.bgPrimary else ZashiColors.Surfaces.bgSecondary
                ).then(
                    if (isChecked) {
                        Modifier.border(1.dp, ZashiColors.Surfaces.strokeSecondary, shape)
                    } else {
                        Modifier
                    }
                ).clickable(
                    onClick = clickAction,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter =
                painterResource(
                    if (isChecked) {
                        R.drawable.ic_checkbox_checked
                    } else {
                        R.drawable.ic_checkbox_unchecked
                    }
                ),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@Composable
internal fun CurrencyField(
    state: CurrencyFieldState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.currencyConversion_selectCurrencyTitle),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ZashiColors.Surfaces.bgSecondary)
                    .clickable(
                        onClick = state.onClick,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.code.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                modifier = Modifier.weight(1f),
                text = state.name.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textTertiary,
            )
            Image(
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right),
                contentDescription = null
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun SettingsExchangeRateOptInPreview() =
    ZcashTheme {
        BlankSurface {
            ExchangeRateSettingsView(
                state =
                    ExchangeRateSettingsState(
                        isOptedIn =
                            SimpleCheckboxState(
                                isChecked = true,
                                onClick = {}
                            ),
                        isOptedOut =
                            SimpleCheckboxState(
                                isChecked = false,
                                onClick = {}
                            ),
                        currencyField =
                            CurrencyFieldState(
                                code = stringRes("USD"),
                                name = stringRes("US Dollar"),
                                onClick = {}
                            ),
                        saveButton =
                            ButtonState(
                                text = stringRes(R.string.currencyConversion_saveBtn),
                                onClick = {}
                            ),
                        onBack = {},
                        info = stringRes(R.string.currencyConversion_torOnInfo)
                    )
            )
        }
    }
