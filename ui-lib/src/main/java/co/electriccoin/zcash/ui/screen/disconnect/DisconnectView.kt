package co.electriccoin.zcash.ui.screen.disconnect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBulletText
import co.electriccoin.zcash.ui.design.component.ZashiButton
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

@Composable
fun DisconnectView(state: DisconnectState) {
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

    co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet(
        state = state.confirmationDialog,
    )
}

@Composable
private fun Content(
    state: DisconnectState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Title and warning text
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(8.dp)
        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(16.dp)

        // Warning items section
        Text(
            text = state.warningTitle.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(8.dp)

        ZashiBulletText(
            state.warningItems.map { it.getValue() },
            color = ZashiColors.Text.textTertiary,
        )

        Spacer(24.dp)
        Spacer(1f)

        // Currently connected card
        ConnectedCard(state = state)

        Spacer(20.dp)

        // Disconnect button
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.disconnectButton
        )
    }
}

@Composable
private fun KeystoneConnectedIcon() {
    Box(
        modifier = Modifier.size(40.dp)
    ) {
        Image(
            painter = painterResource(id = co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        // Connected status badge
        Box(
            modifier =
                Modifier
                    .offset(3.dp, 3.dp)
                    .border(3.dp, ZashiColors.Surfaces.bgPrimary, CircleShape)
                    .background(ZashiColors.Avatars.avatarStatus, CircleShape)
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun ConnectedCard(state: DisconnectState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
    ) {
        Column(modifier = Modifier.padding(ZashiDimensions.Spacing.spacingMd)) {
            // Header with icon and title
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ZashiColors.Surfaces.bgPrimary,
                shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
                border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
                shadowElevation = 1.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    KeystoneConnectedIcon()
                    Spacer(16.dp)
                    Column {
                        Text(
                            text = state.connectedTitle.getValue(),
                            style = ZashiTypography.textMd,
                            color = ZashiColors.Text.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.connectedStatus.getValue(),
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textPrimary
                        )
                    }
                }
            }

            Spacer(ZashiDimensions.Spacing.spacingXl)

            // Info row with icon
            Row(
                verticalAlignment = Alignment.Top,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZashiDimensions.Spacing.spacingXl)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_advanced_settings_info),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary)
                )
                Spacer(12.dp)
                Text(
                    text = state.infoText.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary
                )
            }
            Spacer(ZashiDimensions.Spacing.spacingMd)
        }
    }
}

@Composable
private fun AppBar(state: DisconnectState) {
    ZashiSmallTopAppBar(
        title = state.header.getValue(),
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
    )
}

@PreviewScreens
@Composable
private fun DisconnectPreview() =
    ZcashTheme {
        DisconnectView(
            state =
                DisconnectState(
                    header = stringRes(R.string.disconnect_hardware_wallet_header),
                    title = stringRes(R.string.disconnect_hardware_wallet_title),
                    subtitle = stringRes(R.string.disconnect_hardware_wallet_subtitle),
                    warningTitle = stringRes(R.string.disconnect_hardware_wallet_warning_title),
                    warningItems =
                        listOf(
                            stringRes(R.string.disconnect_hardware_wallet_warning_item_1),
                            stringRes(R.string.disconnect_hardware_wallet_warning_item_2),
                            stringRes(R.string.disconnect_hardware_wallet_warning_item_3),
                        ),
                    connectedTitle = stringRes(R.string.disconnect_hardware_wallet_connected_title),
                    connectedStatus = stringRes(R.string.disconnect_hardware_wallet_connected_status),
                    infoText = stringRes(R.string.disconnect_hardware_wallet_info),
                    disconnectButton =
                        co.electriccoin.zcash.ui.design.component.ButtonState(
                            stringRes(R.string.disconnect_hardware_wallet_button),
                            style = ButtonStyle.DESTRUCTIVE1
                        ) {},
                    confirmationDialog = null,
                    onBack = {}
                )
        )
    }
