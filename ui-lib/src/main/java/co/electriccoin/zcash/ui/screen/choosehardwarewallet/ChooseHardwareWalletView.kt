package co.electriccoin.zcash.ui.screen.choosehardwarewallet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseHardwareWalletView(state: ChooseHardwareWalletState) {
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
                        .scaffoldPadding(padding),
            ) {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.subtitle.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(Modifier.height(24.dp))
                state.cards.forEachIndexed { index, card ->
                    if (index != 0) {
                        Spacer(Modifier.height(12.dp))
                    }
                    HardwareWalletCard(card)
                }
            }
        }
    }
}

@Composable
private fun HardwareWalletCard(state: HardwareWalletCardState) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CARD_BACKGROUND)
                .clickable(onClick = state.onClick)
                .testTag(state.testTag),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.matchParentSize(),
            painter = painterResource(state.background),
            contentDescription = state.contentDescription.getValue(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to CARD_BACKGROUND,
                        )
                    )
        )
        if (state.wordmark != null) {
            Image(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                painter = painterResource(state.wordmark),
                contentDescription = null,
            )
        }
    }
}

private const val CARD_HEIGHT = 180

private val CARD_BACKGROUND = Color(0xFF231F20)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        ChooseHardwareWalletView(
            state =
                ChooseHardwareWalletState(
                    title = stringRes("Connect Hardware Wallet"),
                    subtitle = stringRes("Which hardware wallet would you like to connect?"),
                    cards =
                        listOf(
                            HardwareWalletCardState(
                                background = co.electriccoin.zcash.ui.design.R.drawable.image_keystone,
                                wordmark = null,
                                contentDescription = stringRes("Keystone"),
                                testTag = ChooseHardwareWalletTag.KEYSTONE_CARD,
                                onClick = {},
                            ),
                            HardwareWalletCardState(
                                background = R.drawable.img_ledger_card,
                                wordmark = R.drawable.ic_ledger_wordmark_light,
                                contentDescription = stringRes("Ledger"),
                                testTag = ChooseHardwareWalletTag.LEDGER_CARD,
                                onClick = {},
                            ),
                        ),
                    onBack = {},
                )
        )
    }
