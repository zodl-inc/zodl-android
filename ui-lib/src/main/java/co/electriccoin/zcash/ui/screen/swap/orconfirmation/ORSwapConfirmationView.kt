package co.electriccoin.zcash.ui.screen.swap.orconfirmation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BigIconButtonState
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.QrCodeDefaults
import co.electriccoin.zcash.ui.design.component.QrState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiQr
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource

@Composable
fun ORSwapConfirmationView(state: ORSwapConfirmationState) {
    BlankBgScaffold(
        topBar = { TopAppBar(state) }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(it)
        ) {
            Header(state)
            Spacer(12.dp)
            ZashiQr(
                modifier = Modifier.align(CenterHorizontally),
                state = QrState(qrData = state.qr),
                colors =
                    QrCodeDefaults.colors(
                        border = Color.Unspecified
                    )
            )
            Surface(
                modifier = Modifier.align(CenterHorizontally),
                shape = CircleShape,
                color = ZashiColors.Surfaces.bgSecondary
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = state.address.getValue(),
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textPrimary
                    )
                }
            }
            Spacer(12.dp)
            Row {
                BigIconButton(
                    modifier = Modifier.weight(1f),
                    state = state.copyButton
                )
                Spacer(8.dp)
                BigIconButton(
                    modifier = Modifier.weight(1f),
                    state = state.shareButton
                )
            }
            Spacer(24.dp)
            Spacer(1f)
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = state.footer.getValue(),
                textAlign = TextAlign.Center,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary
            )
            Spacer(20.dp)
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = state.primaryButton
            )
        }
    }
}

@Composable
private fun BigIconButton(
    state: BigIconButtonState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = state.onClick,
        color = ZashiColors.Surfaces.bgPrimary,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ZashiColors.Utility.Gray.utilityGray100),
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                modifier =
                    Modifier
                        .padding(start = 16.dp, top = 20.dp, end = 16.dp)
                        .size(24.dp),
                painter = painterResource(state.icon),
                contentDescription = state.text.getValue()
            )
            Spacer(4.dp)
            Text(
                modifier = Modifier.padding(bottom = 20.dp),
                text = state.text.getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun Header(state: ORSwapConfirmationState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = state.onAmountClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
        horizontalAlignment = CenterHorizontally
    ) {
        Row(
            verticalAlignment = CenterVertically
        ) {
            Text(
                text = stringResource(co.electriccoin.zcash.ui.R.string.swapToZec_deposit),
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary
            )
            Spacer(14.dp)
            Image(
                painter = painterResource(id = co.electriccoin.zcash.ui.R.drawable.ic_copy_other),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Text.textPrimary)
            )
        }
        Spacer(4.dp)
        Row(
            verticalAlignment = CenterVertically
        ) {
            if (state.bigIcon is ImageResource.ByDrawable) {
                Box {
                    Image(
                        modifier = Modifier.size(40.dp),
                        painter = painterResource(state.bigIcon.resource),
                        contentDescription = null
                    )
                    if (state.smallIcon is ImageResource.ByDrawable) {
                        if (state.smallIcon.resource == R.drawable.ic_zec_shielded) {
                            Image(
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(4.dp, 4.dp),
                                painter = painterResource(state.smallIcon.resource),
                                contentDescription = null,
                            )
                        } else {
                            Surface(
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(6.dp, 4.dp),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ZashiColors.Surfaces.bgPrimary)
                            ) {
                                Image(
                                    modifier = Modifier.size(20.dp),
                                    painter = painterResource(state.smallIcon.resource),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
                Spacer(12.dp)
            }
            ZashiAutoSizeText(
                text = state.amount.getValue(),
                style = ZashiTypography.header2,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                maxLines = 1
            )
        }
        ZashiAutoSizeText(
            text = state.amountFiat.getValue(),
            style = ZashiTypography.textLg,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary,
            maxLines = 1
        )
    }
}

@Composable
private fun TopAppBar(state: ORSwapConfirmationState) {
    ZashiSmallTopAppBar(
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(co.electriccoin.zcash.ui.R.string.swap_confirmation_title).uppercase(),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        },
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        regularActions = {
            ZashiIconButton(state.info)
            Spacer(20.dp)
        },
    )
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        ORSwapConfirmationView(
            state =
                ORSwapConfirmationState(
                    onBack = {},
                    info = IconButtonState(R.drawable.ic_info) {},
                    bigIcon = imageRes(R.drawable.ic_token_placeholder),
                    smallIcon = imageRes(R.drawable.ic_chain_placeholder),
                    amount = stringResByNumber(1000),
                    amountFiat = stringResByDynamicCurrencyNumber(100, "USD"),
                    onAmountClick = {},
                    qr = "qr",
                    address = stringResByAddress("Some address"),
                    copyButton = BigIconButtonState(stringRes("Copy"), co.electriccoin.zcash.ui.R.drawable.ic_copy) {},
                    shareButton =
                        BigIconButtonState(
                            stringRes("Share QR"),
                            co.electriccoin.zcash.ui.R.drawable.ic_qr_code_other
                        ) {},
                    footer =
                        styledStringResource(
                            resource = co.electriccoin.zcash.ui.R.string.swap_to_zec_footer,
                            styledStringResource(
                                resource = co.electriccoin.zcash.ui.R.string.swapToZec_info2,
                                StyledStringStyle(
                                    color = StringResourceColor.PRIMARY,
                                    fontWeight = FontWeight.Bold,
                                ),
                                "ASSET",
                                "CHAIN"
                            )
                        ),
                    primaryButton = ButtonState(stringRes("I’ve sent the funds")),
                )
        )
    }
