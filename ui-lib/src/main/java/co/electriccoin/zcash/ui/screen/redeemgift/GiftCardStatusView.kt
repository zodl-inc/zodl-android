package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import co.electriccoin.zcash.ui.common.compose.DisableScreenTimeout
import co.electriccoin.zcash.ui.design.component.GradientBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.screen.redeemgift.GiftCardStatusState.Background.EMPTY
import co.electriccoin.zcash.ui.screen.redeemgift.GiftCardStatusState.Background.PENDING
import co.electriccoin.zcash.ui.screen.redeemgift.GiftCardStatusState.Background.SUCCESS
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressImage
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressSubtitle
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressTitle

/** The room the designs keep free below the centered content for the buttons, at least. */
private val CONTENT_BOTTOM_RESERVE = 96.dp

@Composable
fun GiftCardStatusView(state: GiftCardStatusState) {
    if (state.isWaiting) DisableScreenTimeout()
    GradientBgScaffold(
        startColor =
            when (state.background) {
                null -> ZashiColors.Surfaces.bgPrimary
                PENDING -> ZashiColors.Utility.HyperBlue.utilityBlueDark100
                EMPTY -> ZashiColors.Utility.ErrorRed.utilityError100
                SUCCESS -> ZashiColors.Utility.SuccessGreen.utilitySuccess100
            },
        endColor = ZashiColors.Surfaces.bgPrimary,
        bottomBar = { Buttons(state) },
    ) { padding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = max(CONTENT_BOTTOM_RESERVE, padding.calculateBottomPadding())
                    )
        ) {
            Content(
                state = state,
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                        .heightIn(min = maxHeight)
                        .padding(horizontal = ZashiDimensions.Spacing.spacing3xl)
            )
        }
    }
}

@Composable
private fun Content(
    state: GiftCardStatusState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TransactionProgressImage(state.image)
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingXl))
        TransactionProgressTitle(state.title, style = ZashiTypography.header6)
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingMd))
        TransactionProgressSubtitle(state.subtitle)
    }
}

@Composable
private fun Buttons(state: GiftCardStatusState) {
    if (state.primaryButton == null && state.secondaryButton == null) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = ZashiDimensions.Spacing.spacing3xl),
            verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingLg)
        ) {
            if (state.secondaryButton != null) {
                ZashiButton(
                    state = state.secondaryButton,
                    defaultSecondaryColors =
                        ZashiButtonDefaults.secondaryColors(
                            containerColor =
                                ZashiColors.Btns.Secondary.btnSecondaryBgHover orDark ZashiColors.Surfaces.bgTertiary
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(GiftCardStatusTag.SECONDARY_BUTTON)
                )
            }
            if (state.primaryButton != null) {
                ZashiButton(
                    state = state.primaryButton,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(GiftCardStatusTag.PRIMARY_BUTTON)
                )
            }
        }
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}

object GiftCardStatusTag {
    const val PRIMARY_BUTTON = "gift_card_status_primary_button"
    const val SECONDARY_BUTTON = "gift_card_status_secondary_button"
}
