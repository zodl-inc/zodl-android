package co.electriccoin.zcash.ui.screen.receive.info

import androidx.activity.compose.BackHandler
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
import co.electriccoin.zcash.ui.design.component.ZashiBulletText
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldedAddressInfoScreen() {
    val navigationRouter = koinInject<NavigationRouter>()
    BackHandler { navigationRouter.back() }
    InfoBottomSheetView(
        onBack = navigationRouter::back,
        primaryButton =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                onClick = navigationRouter::back,
            ),
    ) {
        Image(painterResource(R.drawable.ic_receive_info_shielded), contentDescription = null)
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.receive_help_shielded_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(12.dp)
        ZashiBulletText(
            stringResource(R.string.receive_info_shielded_bullet_1, CURRENCY_TICKER, CURRENCY_TICKER, CURRENCY_TICKER),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(8.dp)
        ZashiBulletText(
            stringResource(R.string.receive_help_shielded_desc2),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(8.dp)
        ZashiBulletText(
            stringResource(R.string.receive_help_shielded_desc3),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(8.dp)
        ZashiBulletText(
            stringResource(R.string.receive_help_shielded_desc4),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
    }
}

@Serializable
data object ShieldedAddressInfoArgs
