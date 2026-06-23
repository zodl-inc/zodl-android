package co.electriccoin.zcash.ui.screen.home.backup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiInfoRow
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun WalletBackupDetailView(
    state: WalletBackupDetailState,
) {
    Scaffold(
        topBar = { AppBar(state = state) }
    ) { paddingValues ->
        Content(
            modifier = Modifier.scaffoldPadding(paddingValues),
            state = state,
        )
    }
}

@Composable
private fun AppBar(
    state: WalletBackupDetailState,
    modifier: Modifier = Modifier,
) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.wallet_backup_title),
        modifier = modifier,
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = state.onBack)
        },
        regularActions = {
            ZashiIconButton(
                state =
                    IconButtonState(
                        onClick = state.onInfoClick,
                        icon = R.drawable.ic_help
                    )
            )
            Spacer(Modifier.width(20.dp))
        }
    )
}

@Composable
private fun Content(
    state: WalletBackupDetailState,
    modifier: Modifier = Modifier,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(modifier),
    ) {
        Text(
            text = stringResource(R.string.recoveryPhraseDisplay_warningTitle),
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.header6
        )
        Spacer(10.dp)
        Text(
            text = stringResource(R.string.recoveryPhraseDisplay_warningInfo),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textSm
        )
        Spacer(24.dp)
        ZashiInfoRow(
            icon = R.drawable.ic_wallet_backup_1,
            title = stringResource(R.string.recoveryPhraseDisplay_warningControl_title),
            subtitle = stringResource(R.string.recoveryPhraseDisplay_warningControl_info)
        )
        Spacer(20.dp)
        ZashiInfoRow(
            icon = R.drawable.ic_wallet_backup_2,
            title = stringResource(R.string.recoveryPhraseDisplay_warningKeep_title),
            subtitle = stringResource(R.string.recoveryPhraseDisplay_warningKeep_info)
        )
        Spacer(20.dp)
        ZashiInfoRow(
            icon = R.drawable.ic_wallet_backup_3,
            title = stringResource(R.string.recoveryPhraseDisplay_warningStore_title),
            subtitle = stringResource(R.string.recoveryPhraseDisplay_warningStore_info)
        )
        Spacer(20.dp)
        ZashiInfoRow(
            icon = R.drawable.ic_wallet_backup_4,
            title = stringResource(R.string.recoveryPhraseDisplay_birthdayTitle),
            subtitle = stringResource(R.string.recoveryPhraseDisplay_warningHeight_info)
        )
        Spacer(20.dp)
        Spacer(1f)
        Row {
            Spacer(20.dp)
            Image(
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Utility.WarningYellow.utilityOrange700)
            )
            Spacer(12.dp)
            Text(
                text = stringResource(R.string.recoveryPhraseDisplay_proceedWarning),
                color = ZashiColors.Utility.WarningYellow.utilityOrange700,
                style = ZashiTypography.textXs,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(24.dp)
        ZashiButton(
            state =
                ButtonState(
                    text = stringRes(stringResource(R.string.general_next)),
                    onClick = state.onNextClick
                ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
@PreviewScreens
private fun Preview() =
    ZcashTheme {
        WalletBackupDetailView(
            state =
                WalletBackupDetailState(
                    onBack = {},
                    onNextClick = {},
                    onInfoClick = {}
                )
        )
    }
