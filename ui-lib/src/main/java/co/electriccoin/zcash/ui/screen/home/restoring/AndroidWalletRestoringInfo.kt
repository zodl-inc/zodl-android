package co.electriccoin.zcash.ui.screen.home.restoring

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBulletText
import co.electriccoin.zcash.ui.design.component.ZashiInfoText
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidWalletRestoringInfo() {
    val viewModel = koinViewModel<WalletRestoringInfoViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    InfoBottomSheetView(
        onBack = state.onBack,
        primaryButton =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                onClick = state.onBack,
            ),
    ) {
        Text(
            text = stringResource(R.string.smartBanner_help_restore_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.home_info_restoring_message),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd,
        )
        Spacer(12.dp)
        ZashiBulletText(
            stringResource(R.string.home_info_restoring_bullet_1),
            stringResource(R.string.home_info_restoring_bullet_2),
            color = ZashiColors.Text.textTertiary,
        )
        state.info?.let {
            Spacer(32.dp)
            ZashiInfoText(it.getValue())
        }
    }
}

@Serializable
object WalletRestoringInfo
