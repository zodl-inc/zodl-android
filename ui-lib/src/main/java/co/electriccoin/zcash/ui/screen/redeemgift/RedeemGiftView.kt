package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
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
        Spacer(Modifier.height(24.dp))
        state.image.Compose(modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing2xl))
        Text(
            text = state.heading.getValue(),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingLg))
        Text(
            modifier = Modifier.testTag(RedeemGiftTag.AMOUNT),
            text = state.amount.getValue(),
            style = ZashiTypography.header3,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
        if (state.fiatAmount != null) {
            Spacer(Modifier.height(ZashiDimensions.Spacing.spacingXs))
            Text(
                text = state.fiatAmount.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textTertiary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingMd))
        Text(
            modifier = Modifier.testTag(RedeemGiftTag.FEE_HINT),
            text = state.feeHint.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Center
        )
        if (state.message != null) {
            Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
            SenderMessage(label = state.messageLabel.getValue(), message = state.message.getValue())
        }
        if (state.destination != null) {
            Spacer(Modifier.height(ZashiDimensions.Spacing.spacing2xl))
            Text(
                text = state.destination.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing2xl))
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
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = ZashiColors.Surfaces.bgSecondary,
                    shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl)
                ).padding(ZashiDimensions.Spacing.spacingXl)
    ) {
        Text(
            text = label,
            style = ZashiTypography.textXs,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingXs))
        Text(
            modifier = Modifier.testTag(RedeemGiftTag.MESSAGE),
            text = message,
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textPrimary
        )
    }
}

object RedeemGiftTag {
    const val AMOUNT = "redeem_gift_amount"
    const val FEE_HINT = "redeem_gift_fee_hint"
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
