package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
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
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState
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
                title = stringResource(R.string.redeemGift_title),
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
        Image(
            painter = painterResource(R.drawable.ic_integrations_gift),
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing2xl))
        Text(
            text = stringResource(R.string.redeemGift_ready_title),
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
        if (state.message != null) {
            Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
            SenderMessage(state.message.getValue())
        }
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing2xl))
        Text(
            text = state.destination.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Center
        )
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
private fun SenderMessage(message: String) {
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
            text = stringResource(R.string.redeemGift_ready_messageLabel),
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
    const val MESSAGE = "redeem_gift_message"
    const val REDEEM_BUTTON = "redeem_gift_redeem_button"
}

@PreviewScreens
@Composable
private fun ReadyPreview() =
    ZcashTheme {
        RedeemGiftView(
            RedeemGiftState.Ready(
                amount = stringRes(Zatoshi(10_000_000)),
                fiatAmount = stringRes("$4.12"),
                message = stringRes("Welcome to Zcash Summit"),
                destination = stringRes("The funds will be sent to your Zodl wallet."),
                redeemButton = ButtonState(text = stringRes("Redeem"), style = ButtonStyle.PRIMARY),
                onBack = {}
            )
        )
    }

@PreviewScreens
@Composable
private fun ReadyNoMessagePreview() =
    ZcashTheme {
        RedeemGiftView(
            RedeemGiftState.Ready(
                amount = stringRes(Zatoshi(10_000_000)),
                fiatAmount = null,
                message = null,
                destination = stringRes("The funds will be sent to your Zodl wallet."),
                redeemButton = ButtonState(text = stringRes("Redeem"), style = ButtonStyle.PRIMARY),
                onBack = {}
            )
        )
    }

@PreviewScreens
@Composable
private fun CheckingPreview() =
    ZcashTheme {
        RedeemGiftView(
            RedeemGiftState.Status(
                TransactionProgressState(
                    background = null,
                    image = loadingImageRes(),
                    title = stringRes("Looking for your gift…"),
                    subtitle = stringRes("42% checked").withStyle(),
                    middleButton = null,
                    primaryButton = null,
                    secondaryButton = null,
                    onBack = {}
                )
            )
        )
    }

@PreviewScreens
@Composable
private fun PendingPreview() =
    ZcashTheme {
        RedeemGiftView(
            RedeemGiftState.Status(
                TransactionProgressState(
                    background = TransactionProgressState.Background.PENDING,
                    image = imageRes(R.drawable.ic_face_star),
                    title = stringRes("Your gift is on its way"),
                    subtitle = stringRes("0.1 ZEC needs a few more confirmations.").withStyle(),
                    middleButton = null,
                    primaryButton = ButtonState(text = stringRes("Check again"), style = ButtonStyle.PRIMARY),
                    secondaryButton = ButtonState(text = stringRes("Close"), style = ButtonStyle.SECONDARY),
                    onBack = {},
                    showAppBar = true
                )
            )
        )
    }
