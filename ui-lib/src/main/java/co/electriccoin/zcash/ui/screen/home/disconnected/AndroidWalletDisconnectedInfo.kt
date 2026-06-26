package co.electriccoin.zcash.ui.screen.home.disconnected

import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidWalletDisconnectedInfo() {
    val navigationRouter = koinInject<NavigationRouter>()
    InfoBottomSheetView(
        onBack = navigationRouter::back,
        primaryButton =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                onClick = navigationRouter::back,
            ),
    ) {
        Image(painterResource(R.drawable.ic_info_wallet_disconnected), contentDescription = null)
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.smartBanner_content_disconnected_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.smartBanner_help_diconnected_info),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd,
        )
    }
}

@Serializable
object WalletDisconnectedInfo
