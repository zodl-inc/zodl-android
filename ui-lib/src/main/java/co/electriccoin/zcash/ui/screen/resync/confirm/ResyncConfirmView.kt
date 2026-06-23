package co.electriccoin.zcash.ui.screen.resync.confirm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle

@Composable
fun ResyncConfirmView(state: ResyncConfirmState) {
    BlankBgScaffold(
        topBar = { AppBar(state) },
        bottomBar = {},
        content = { padding ->
            Content(
                state = state,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding)
            )
        }
    )
}

@Composable
private fun Content(
    state: ResyncConfirmState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(8.dp)
        Text(
            text = state.message.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(24.dp)
        Spacer(1f)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ZashiColors.Surfaces.bgSecondary,
            shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = state.changeInfo.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(16.dp)
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.change,
                    defaultPrimaryColors =
                        ZashiButtonDefaults.secondaryColors(
                            borderColor = ZashiColors.Btns.Secondary.btnSecondaryBorder
                        )
                )
            }
        }
        Spacer(20.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.confirm,
            defaultPrimaryColors = ZashiButtonDefaults.primaryColors()
        )
    }
}

@Composable
private fun AppBar(state: ResyncConfirmState) {
    ZashiSmallTopAppBar(
        title = state.title.getValue(),
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                ),
    )
}

@PreviewScreens
@Composable
private fun ResyncConfirmPreview() =
    ZcashTheme {
        ResyncConfirmView(
            state =
                ResyncConfirmState(
                    title = stringRes(R.string.resyncWallet_title),
                    subtitle = stringRes(R.string.resyncWallet_confirmTitle),
                    message = stringRes(R.string.confirm_resync_subtitle),
                    onBack = {},
                    confirm = ButtonState(stringRes("Confirm")) {},
                    change = ButtonState(stringRes("Change")) {},
                    changeInfo =
                        stringRes(
                            "Your wallet will be resynced from May 2024 (2,185,500 blocks). " +
                                "Use the button below if you wish to change it."
                        ).withStyle(),
                )
        )
    }
