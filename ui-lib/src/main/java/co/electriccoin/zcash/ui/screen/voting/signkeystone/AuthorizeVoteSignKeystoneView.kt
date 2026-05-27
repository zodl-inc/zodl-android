package co.electriccoin.zcash.ui.screen.voting.signkeystone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.QrCodeDefaults
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeDefaults
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiLinearProgressIndicator
import co.electriccoin.zcash.ui.design.component.ZashiQr
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.listitem.BaseListItem
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItemDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.ZashiAccountInfoListItemState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AuthorizeVoteSignKeystoneView(state: AuthorizeVoteSignKeystoneState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = stringResource(R.string.authorize_vote_bar_title),
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = state.onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(padding)
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
            ) {
                Spacer(Modifier.height(12.dp))
                AccountInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.accountInfo,
                    badgeText = state.badgeText
                )
                QrCodeCard(state = state)
                ProgressMemoSection(state = state)
                Spacer(Modifier.height(12.dp))
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                        .padding(bottom = ZashiDimensions.Spacing.spacingMd)
            ) {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.scanButton
                )
            }
        }
    }
}

@Composable
private fun AccountInfoCard(
    state: ZashiAccountInfoListItemState,
    badgeText: co.electriccoin.zcash.ui.design.util.StringResource,
    modifier: Modifier = Modifier,
) {
    val colors = ZashiListItemDefaults.secondaryColors()
    BaseListItem(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        leading = {
            Image(
                modifier = it.size(40.dp),
                painter = painterResource(state.icon),
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
                text = badgeText,
                colors = ZashiBadgeDefaults.hyperBlueColors()
            )
        },
        border = BorderStroke(1.dp, colors.borderColor),
        onClick = null
    )
}

@Composable
private fun QrCodeCard(state: AuthorizeVoteSignKeystoneState) {
    state.qrData?.let { _ ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ZashiColors.Surfaces.bgPrimary)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            ZashiQr(
                state = state.toQrState(),
                modifier = Modifier.padding(vertical = 20.dp),
                colors = QrCodeDefaults.colors(background = Color.White, foreground = Color.Black)
            )
        }
    }
    LaunchedEffect(state.qrData) {
        if (state.qrData != null) {
            delay(100.milliseconds)
            state.generateNextQrCode()
        }
    }
}

@Composable
private fun ProgressMemoSection(state: AuthorizeVoteSignKeystoneState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ZashiColors.Surfaces.bgSecondary)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.authorize_vote_signature_n_of_m,
                            state.currentBundleNumber,
                            state.totalBundles
                        ),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textPrimary
                )
                ZashiBadge(
                    text =
                        stringRes(
                            R.string.authorize_vote_signed_badge,
                            state.signedBundleCount,
                            state.totalBundles
                        ),
                    colors = ZashiBadgeDefaults.hyperBlueColors(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            ZashiLinearProgressIndicator(
                progress = state.bundleProgress
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = state.signedZec.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary
                )
                Text(
                    text = "·",
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary
                )
                Text(
                    text = state.pendingZec.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary
                )
            }
            state.signingNotice?.let { notice ->
                Text(
                    text = notice.getValue(),
                    style = ZashiTypography.textXs,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textPrimary
                )
            }
        }

        if (state.useSignedBundlesOnly != null) {
            HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
            UseSignedBundlesOnlyRow(state = state.useSignedBundlesOnly)
        }

        HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
        MemoSection(memoText = state.memoText)
    }
}

@Composable
private fun UseSignedBundlesOnlyRow(state: UseSignedBundlesOnlyState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = state.onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.authorize_vote_use_signed_bundles_only),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary
            )
            Text(
                text =
                    stringResource(
                        R.string.authorize_vote_use_signed_bundles_only_subtitle,
                        state.remainingZec.getValue()
                    ),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary
            )
        }
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ZashiColors.Text.textTertiary
        )
    }
}

@Composable
private fun MemoSection(memoText: co.electriccoin.zcash.ui.design.util.StringResource) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.authorize_vote_memo_label),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
        Text(
            text = memoText.getValue(),
            style = ZashiTypography.textXs,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textPrimary
        )
    }
}

@PreviewScreens
@Composable
private fun AuthorizeVoteSignKeystoneStep1Preview() =
    ZcashTheme {
        AuthorizeVoteSignKeystoneView(
            state =
                AuthorizeVoteSignKeystoneState(
                    onBack = {},
                    accountInfo =
                        ZashiAccountInfoListItemState(
                            icon = co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone,
                            title = stringRes("Keystone"),
                            subtitle = stringRes("u18EgiqpBzgfeFqB6cde...")
                        ),
                    badgeText = stringRes("Hardware"),
                    qrData = "sample-qr-data",
                    generateNextQrCode = {},
                    currentBundleNumber = 1,
                    totalBundles = 2,
                    signedBundleCount = 0,
                    signedZec = stringRes("0.000 ZEC signed"),
                    pendingZec = stringRes("1.625 ZEC awaiting"),
                    memoText =
                        stringRes(
                            "I am authorizing this hotkey managed by my wallet to vote " +
                                "on NU7 Sentiment Poll with 1.50000000 ZEC."
                        ),
                    signingNotice = null,
                    useSignedBundlesOnly = null,
                    scanButton = ButtonState(text = stringRes("Scan signature"), onClick = {})
                )
        )
    }

@PreviewScreens
@Composable
private fun AuthorizeVoteSignKeystoneStep2Preview() =
    ZcashTheme {
        AuthorizeVoteSignKeystoneView(
            state =
                AuthorizeVoteSignKeystoneState(
                    onBack = {},
                    accountInfo =
                        ZashiAccountInfoListItemState(
                            icon = co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone,
                            title = stringRes("Keystone"),
                            subtitle = stringRes("u18EgiqpBzgfeFqB6cde...")
                        ),
                    badgeText = stringRes("Hardware"),
                    qrData = "sample-qr-data",
                    generateNextQrCode = {},
                    currentBundleNumber = 2,
                    totalBundles = 2,
                    signedBundleCount = 1,
                    signedZec = stringRes("1.500 ZEC signed"),
                    pendingZec = stringRes("0.125 ZEC pending"),
                    memoText =
                        stringRes(
                            "I am authorizing this hotkey managed by my wallet to vote on " +
                                "NU7 Sentiment Poll with 0.12500000 ZEC."
                        ),
                    signingNotice =
                        stringRes(
                            "That signature is for a different bundle. Sign bundle 2 of 2 on Keystone, " +
                                "then scan the new QR."
                        ),
                    useSignedBundlesOnly =
                        UseSignedBundlesOnlyState(
                            remainingZec = stringRes("0.125 ZEC"),
                            onClick = {}
                        ),
                    scanButton = ButtonState(text = stringRes("Scan signature"), onClick = {})
                )
        )
    }
