package co.electriccoin.zcash.ui.screen.home.shieldfunds

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.typicalFee
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiCard
import co.electriccoin.zcash.ui.design.component.ZashiCheckbox
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.asPrivacySensitive
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldFundsInfoView(
    state: ShieldFundsInfoState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    state ?: return
    InfoBottomSheetView(onBack = state.onBack, sheetState = sheetState) {
        Image(painterResource(R.drawable.ic_info_shield), contentDescription = null)
        Spacer(12.dp)
        Text(
            stringResource(R.string.smartBanner_help_shield_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(12.dp)
        Text(
            stringResource(R.string.smartBanner_help_shield_info1, CURRENCY_TICKER),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd,
        )
        Spacer(24.dp)
        Text(
            stringRes(
                R.string.smartBanner_help_shield_info2,
                stringRes(Zatoshi.typicalFee, HIDDEN),
                CURRENCY_TICKER,
            ).getValue(),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd,
        )
        Spacer(32.dp)
        ZashiCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            borderColor = ZashiColors.Surfaces.strokeSecondary,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_info_transparent_subheader),
                    color = ZashiColors.Text.textPrimary,
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(4.dp)
                Image(painterResource(R.drawable.ic_transparent_small), contentDescription = null)
            }
            Spacer(4.dp)
            Text(
                text = state.subtitle.getValue(),
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.textXl,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(24.dp)
        ZashiCheckbox(state = state.checkbox)
        Spacer(24.dp)
        ZashiButton(
            state = state.secondaryButton,
            modifier = Modifier.fillMaxWidth(),
            defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
        )
        Spacer(4.dp)
        ZashiButton(state = state.primaryButton, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        ShieldFundsInfoView(
            state =
                ShieldFundsInfoState(
                    onBack = {},
                    primaryButton = ButtonState(text = stringRes("Remind me later"), onClick = {}),
                    secondaryButton = ButtonState(text = stringRes("Not now"), onClick = {}),
                    subtitle =
                        stringRes(
                            R.string.home_message_transparent_balance_subtitle,
                            stringRes("0.00").asPrivacySensitive(),
                            CURRENCY_TICKER,
                        ),
                    checkbox =
                        CheckboxState(
                            title = stringRes(R.string.smartBanner_help_shield_doNotShowAgain),
                            onClick = {},
                            isChecked = false,
                        ),
                ),
        )
    }
