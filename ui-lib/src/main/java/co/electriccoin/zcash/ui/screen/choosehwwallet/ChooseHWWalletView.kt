package co.electriccoin.zcash.ui.screen.choosehwwallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.PressMorphDefaults
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.pressMorph
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseHWWalletView(state: ChooseHWWalletState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                navigationAction = { ZashiTopAppBarCloseNavigation(state.onBack) },
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        }
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding, start = CARD_INSET.dp, end = CARD_INSET.dp),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = TEXT_EXTRA_INSET.dp),
                    text = state.title.getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    modifier = Modifier.padding(horizontal = TEXT_EXTRA_INSET.dp),
                    text = state.subtitle.getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textTertiary,
                    lineHeight = BODY_LINE_HEIGHT.sp,
                )
                Spacer(Modifier.height(32.dp))
                state.cards.forEachIndexed { index, card ->
                    if (index != 0) {
                        Spacer(Modifier.height(12.dp))
                    }
                    HWWalletCard(card)
                }
            }
        }
    }
}

@Composable
private fun HWWalletCard(state: HWWalletCardState) {
    val image = state.image
    val interactionSource = remember { MutableInteractionSource() }
    if (image is ImageResource.ByDrawable) {
        image.Compose(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .pressMorph(interactionSource, PressMorphDefaults.PRESSED_SCALE_SUBTLE)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = state.onClick,
                    ).testTag(state.testTag),
            contentDescription = state.contentDescription.getValue(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/**
 * The Figma picker insets its cards 16 dp from the screen edge, 8 dp less than the header text.
 */
private const val CARD_INSET = 16

private const val TEXT_EXTRA_INSET = 8

/**
 * Figma sets the body in Text MD at 16/22, tighter than the 24 sp line of [ZashiTypography.textMd].
 */
private const val BODY_LINE_HEIGHT = 22

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        ChooseHWWalletView(state = ChooseHWWalletState.preview)
    }

@PreviewScreens
@Composable
private fun LedgerOnlyPreview() =
    ZcashTheme {
        ChooseHWWalletView(state = ChooseHWWalletState.previewLedgerOnly)
    }
