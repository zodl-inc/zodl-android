package co.electriccoin.zcash.ui.screen.home.backup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBulletText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiCheckbox
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletBackupInfoView(
    state: WalletBackupInfoState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    state ?: return
    InfoBottomSheetView(onBack = state.onBack, sheetState = sheetState) {
        Image(painterResource(R.drawable.ic_info_backup), contentDescription = null)
        Spacer(12.dp)
        Text(
            stringResource(R.string.smartBanner_help_backup_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(12.dp)
        Text(
            stringResource(R.string.smartBanner_help_backup_info1),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd,
        )
        Spacer(12.dp)
        Text(
            stringResource(R.string.smartBanner_help_backup_info2),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd,
        )
        Spacer(12.dp)
        ZashiBulletText(
            stringResource(R.string.smartBanner_help_backup_point1),
            stringResource(R.string.smartBanner_help_backup_point2),
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(12.dp)
        Text(
            stringResource(R.string.smartBanner_help_backup_info3),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd,
        )
        Spacer(24.dp)
        Text(
            stringResource(R.string.smartBanner_help_backup_info4),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd,
        )
        Spacer(32.dp)
        state.checkboxState?.let {
            ZashiCheckbox(state = it)
            Spacer(12.dp)
        }
        ZashiButton(
            state = state.secondaryButton,
            modifier =
                androidx.compose.ui.Modifier
                    .fillMaxWidth(),
            defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
        )
        Spacer(4.dp)
        ZashiButton(
            state = state.primaryButton,
            modifier =
                androidx.compose.ui.Modifier
                    .fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        WalletBackupInfoView(
            WalletBackupInfoState(
                onBack = {},
                secondaryButton =
                    ButtonState(
                        text = stringRes(R.string.recoveryPhraseDisplay_button_remindMeLater),
                        onClick = {},
                        isEnabled = false,
                    ),
                primaryButton =
                    ButtonState(
                        text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                        onClick = {},
                    ),
                checkboxState =
                    CheckboxState(
                        isChecked = false,
                        onClick = {},
                        title = stringRes(R.string.smartBanner_help_backup_acknowledge),
                    ),
            )
        )
    }
