package co.electriccoin.zcash.ui.screen.signkeystonetransaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.component.SettingsListItemLeadingIcon
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.QrCodeDefaults
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeDefaults
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiQr
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.listitem.BaseListItem
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItemDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SignKeystoneTransactionView(state: SignKeystoneTransactionState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = state.barTitle.getValue(),
            )
        }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(it)
        ) {
            ZashiAccountInfoListItem(
                state = state.accountInfo,
                badgeText = state.badgeText
            )
            Spacer(Modifier.height(32.dp))
            QrContent(state)
            Spacer(Modifier.height(32.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                text = state.title.getValue(),
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                text = state.subtitle.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary
            )
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.weight(1f))
            BottomSection(state)
        }
    }
}

@Composable
private fun ZashiAccountInfoListItem(
    state: ZashiAccountInfoListItemState,
    badgeText: co.electriccoin.zcash.ui.design.util.StringResource,
    modifier: Modifier = Modifier,
) {
    val color = ZashiListItemDefaults.secondaryColors()

    BaseListItem(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        leading = {
            SettingsListItemLeadingIcon(
                modifier = it,
                drawableRes = state.icon,
                contentDescription = state.title.getValue()
            )
        },
        content = {
            ZashiListItemDefaults.ContentItem(
                modifier = it,
                text = state.title.getValue(),
                subtitle = state.subtitle.getValue().let(::AnnotatedString),
                titleIcons = persistentListOf(),
                isEnabled = true
            )
        },
        trailing = {
            ZashiBadge(
                text = badgeText.getValue(),
                colors = ZashiBadgeDefaults.hyperBlueColors()
            )
        },
        border = BorderStroke(1.dp, color.borderColor),
        onClick = null
    )
}

@Composable
private fun ColumnScope.QrContent(ksState: SignKeystoneTransactionState) {
    ksState.qrData?.let {
        ZashiQr(
            state = ksState.toQrState(),
            modifier = Modifier.align(CenterHorizontally),
            colors =
                QrCodeDefaults.colors(
                    background = Color.White,
                    foreground = Color.Black
                )
        )
    }
    LaunchedEffect(ksState.qrData) {
        if (ksState.qrData != null) {
            delay(100.milliseconds)
            ksState.generateNextQrCode()
        }
    }
}

@Composable
private fun BottomSection(
    state: SignKeystoneTransactionState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
    ) {
        if (state.secondaryButton != null) {
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = state.secondaryButton,
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors()
            )
        }
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.negativeButton,
            defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors()
        )
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.positiveButton
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SignKeystoneTransactionView(
            state =
                SignKeystoneTransactionState(
                    barTitle = stringRes("Sign Transaction"),
                    title = stringRes("Scan with your Keystone wallet"),
                    subtitle = stringRes("After you have signed with Keystone, tap on the Get Signature button below."),
                    accountInfo =
                        ZashiAccountInfoListItemState(
                            icon = R.drawable.ic_item_keystone,
                            title = stringRes("title"),
                            subtitle = stringRes("subtitle"),
                        ),
                    badgeText = stringRes("Hardware"),
                    generateNextQrCode = {},
                    qrData = "tralala",
                    secondaryButton = null,
                    positiveButton = ButtonState(stringRes("Get Signature")),
                    negativeButton = ButtonState(stringRes("Reject")),
                    onBack = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun DebugPreview() =
    ZcashTheme {
        SignKeystoneTransactionView(
            state =
                SignKeystoneTransactionState(
                    barTitle = stringRes("Sign Transaction"),
                    title = stringRes("Scan with your Keystone wallet"),
                    subtitle = stringRes("After you have signed with Keystone, tap on the Get Signature button below."),
                    accountInfo =
                        ZashiAccountInfoListItemState(
                            icon = R.drawable.ic_item_keystone,
                            title = stringRes("title"),
                            subtitle = stringRes("subtitle"),
                        ),
                    badgeText = stringRes("Hardware"),
                    generateNextQrCode = {},
                    qrData = "tralala",
                    secondaryButton = ButtonState(stringRes("Share PCZT")),
                    positiveButton = ButtonState(stringRes("Get Signature")),
                    negativeButton = ButtonState(stringRes("Reject")),
                    onBack = {},
                )
        )
    }
