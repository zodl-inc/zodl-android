@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressView

@Composable
fun RedeemGiftView(state: RedeemGiftState) {
    when (state) {
        is RedeemGiftState.Status -> TransactionProgressView(state.progress)
        is RedeemGiftState.CardStatus -> GiftCardStatusView(state.status)
        is RedeemGiftState.Ready -> ReadyView(state)
    }
}

@Composable
private fun ReadyView(state: RedeemGiftState.Ready) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                title = state.title.getValue(),
                navigationAction = {
                    ZashiTopAppBarBackNavigation(onBack = state.onBack)
                },
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            ReadyContent(
                state = state,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(paddingValues)
            )
        }
    }
}

@Composable
private fun ReadyContent(
    state: RedeemGiftState.Ready,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
        state.image.Compose(modifier = Modifier.size(108.dp))
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingLg + ZashiDimensions.Spacing.spacingSm))
        Text(
            text = state.heading.getValue(),
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingMd))
        ZashiAutoSizeText(
            modifier = Modifier.testTag(RedeemGiftTag.AMOUNT),
            text = state.amount,
            style = ZashiTypography.header1,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            contentAlignment = Alignment.Center,
            maxLines = 1
        )
        if (state.message != null) {
            Spacer(Modifier.height(ZashiDimensions.Spacing.spacing5xl))
            SenderMessage(label = state.messageLabel.getValue(), message = state.message.getValue())
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
        Disclaimer(text = state.disclaimer.getValue())
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
        ZashiButton(
            state = state.redeemButton,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(RedeemGiftTag.REDEEM_BUTTON)
        )
    }
}

@Composable
private fun SenderMessage(
    label: String,
    message: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingMd))
        Text(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = ZashiColors.Surfaces.bgSecondary,
                        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl)
                    ).padding(ZashiDimensions.Spacing.spacingLg)
                    .testTag(RedeemGiftTag.MESSAGE),
            text = message,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun Disclaimer(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZashiDimensions.Spacing.spacingXl)
                .testTag(RedeemGiftTag.DISCLAIMER),
    ) {
        Image(
            modifier = Modifier.size(16.dp),
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary)
        )
        Spacer(Modifier.width(ZashiDimensions.Spacing.spacingMd))
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Start
        )
    }
}

object RedeemGiftTag {
    const val AMOUNT = "redeem_gift_amount"
    const val DISCLAIMER = "redeem_gift_disclaimer"
    const val MESSAGE = "redeem_gift_message"
    const val REDEEM_BUTTON = "redeem_gift_redeem_button"
}

@PreviewScreens
@Composable
private fun ReadyPreview() =
    ZcashTheme {
        RedeemGiftView(RedeemGiftState.preview)
    }

@PreviewScreens
@Composable
private fun ReadyNoMessagePreview() =
    ZcashTheme {
        RedeemGiftView(RedeemGiftState.previewNoMessage)
    }

@PreviewScreens
@Composable
private fun CheckingPreview() =
    ZcashTheme {
        RedeemGiftView(RedeemGiftState.previewChecking)
    }

@PreviewScreens
@Composable
private fun PendingPreview() =
    ZcashTheme {
        RedeemGiftView(RedeemGiftState.previewPending)
    }

@PreviewScreens
@Composable
private fun EmptyPreview() =
    ZcashTheme {
        RedeemGiftView(RedeemGiftState.previewEmpty)
    }

@PreviewScreens
@Composable
private fun EmptyDustPreview() =
    ZcashTheme {
        RedeemGiftView(RedeemGiftState.previewEmptyDust)
    }

@PreviewScreens
@Composable
private fun RedeemingPreview() =
    ZcashTheme {
        RedeemGiftView(RedeemGiftState.previewRedeeming)
    }

@PreviewScreens
@Composable
private fun SuccessPreview() =
    ZcashTheme {
        RedeemGiftView(RedeemGiftState.previewSuccess)
    }
