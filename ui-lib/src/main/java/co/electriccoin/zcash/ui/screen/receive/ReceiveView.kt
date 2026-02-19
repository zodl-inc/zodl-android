package co.electriccoin.zcash.ui.screen.receive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppbar
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiImageButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.ZashiMainTopAppBarStateFixture
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressState.ColorMode.DEFAULT
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressState.ColorMode.KEYSTONE
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressState.ColorMode.ZASHI
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER

@Composable
internal fun ReceiveView(
    state: ReceiveState,
    appBarState: ZashiMainTopAppBarState?,
) {
    when {
        state.items.isNullOrEmpty() && state.isLoading -> {
            CircularScreenProgressIndicator()
        }

        else -> {
            BlankBgScaffold(
                topBar = {
                    ZashiTopAppbar(
                        title = stringRes(R.string.receive_title, CURRENCY_TICKER),
                        state = appBarState,
                        showHideBalances = false,
                        onBack = state.onBack
                    )
                },
            ) { paddingValues ->
                ReceiveContents(
                    items = state.items.orEmpty(),
                    modifier =
                        Modifier.scaffoldScrollPadding(
                            paddingValues = paddingValues,
                            top = paddingValues.calculateTopPadding()
                        ),
                )
            }
        }
    }
}

@Suppress("UnstableCollections")
@Composable
private fun ReceiveContents(
    items: List<ReceiveAddressState>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(all = ZcashTheme.dimens.spacingSmall),
    ) {
        items.forEachIndexed { index, state ->
            if (index != 0) {
                Spacer(Modifier.height(8.dp))
            }

            AddressPanel(
                state = state,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(24.dp)
        Spacer(1f)
        Image(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            painter = painterResource(R.drawable.ic_receive_info),
            contentDescription = ""
        )
        Spacer(8.dp)
        Text(
            text = stringResource(id = R.string.receive_prioritize_shielded),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(8.dp)
    }
}

@Composable
private fun AddressPanel(
    state: ReceiveAddressState,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        when (state.colorMode) {
            ZASHI -> ZashiColors.Utility.Purple.utilityPurple50
            KEYSTONE -> ZashiColors.Utility.Indigo.utilityIndigo50
            DEFAULT -> ZashiColors.Surfaces.bgSecondary
        }

    val buttonColor =
        when (state.colorMode) {
            ZASHI -> ZashiColors.Utility.Purple.utilityPurple100
            KEYSTONE -> ZashiColors.Utility.Indigo.utilityIndigo100
            DEFAULT -> ZashiColors.Surfaces.bgTertiary
        }

    val buttonTextColor =
        when (state.colorMode) {
            ZASHI -> ZashiColors.Utility.Purple.utilityPurple800
            KEYSTONE -> ZashiColors.Utility.Indigo.utilityIndigo800
            DEFAULT -> ZashiColors.Text.textPrimary
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .wrapContentHeight()
                .background(containerColor, RoundedCornerShape(ZashiDimensions.Radius.radius3xl))
                .clip(RoundedCornerShape(ZashiDimensions.Radius.radius3xl))
                .clickable(onClick = state.onClick)
                .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box {
                Image(
                    modifier = Modifier.size(40.dp),
                    painter = painterResource(id = state.icon),
                    contentDescription = null
                )
                if (state.isShielded) {
                    Image(
                        modifier =
                            Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .offset(1.5.dp, .5.dp),
                        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded),
                        contentDescription = "",
                    )
                }
            }

            Spacer(Modifier.width(ZcashTheme.dimens.spacingDefault))

            Column {
                Text(
                    text = state.title.getValue(),
                    color = ZashiColors.Text.textPrimary,
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(4.dp)
                Text(
                    text = state.subtitle.getValue(),
                    color = ZashiColors.Text.textTertiary,
                    style = ZashiTypography.textSm
                )
            }

            Spacer(Modifier.width(ZcashTheme.dimens.spacingSmall))

            Spacer(modifier = Modifier.weight(1f))

            ZashiImageButton(
                modifier = Modifier.size(32.dp),
                state = state.infoIconButton,
            )
        }

        AnimatedVisibility(visible = state.isExpanded) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = ZcashTheme.dimens.spacingDefault)
            ) {
                if (state.isShielded) {
                    ReceiveIconButton(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor,
                        iconPainter = painterResource(id = R.drawable.ic_copy_shielded),
                        onClick = state.onCopyClicked,
                        text = stringResource(id = R.string.receive_copy),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(ZcashTheme.dimens.spacingSmall))
                }

                ReceiveIconButton(
                    containerColor = buttonColor,
                    contentColor = buttonTextColor,
                    iconPainter = painterResource(id = R.drawable.ic_qr_code_shielded),
                    onClick = state.onQrClicked,
                    text = stringResource(id = R.string.receive_qr_code),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(ZcashTheme.dimens.spacingSmall))

                ReceiveIconButton(
                    containerColor = buttonColor,
                    contentColor = buttonTextColor,
                    iconPainter = painterResource(id = R.drawable.ic_request_shielded),
                    onClick = state.onRequestClicked,
                    text = stringResource(id = R.string.receive_request),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ReceiveIconButton(
    containerColor: Color,
    contentColor: Color,
    iconPainter: Painter,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .background(containerColor, RoundedCornerShape(ZashiDimensions.Radius.radiusXl))
                .clip(RoundedCornerShape(ZashiDimensions.Radius.radiusXl))
                .clickable { onClick() }
                .padding(12.dp)
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = text,
            tint = contentColor
        )
        Spacer(4.dp)
        Text(
            text = text,
            color = contentColor,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
@PreviewScreens
private fun LoadingPreview() =
    ZcashTheme(forceDarkMode = true) {
        ReceiveView(
            state =
                ReceiveState(
                    items = null,
                    isLoading = true,
                    onBack = {}
                ),
            appBarState = ZashiMainTopAppBarStateFixture.new()
        )
    }

@PreviewScreens
@Composable
private fun ZashiPreview() =
    ZcashTheme {
        ReceiveView(
            state =
                ReceiveState(
                    items =
                        listOf(
                            ReceiveAddressState(
                                icon = R.drawable.ic_zec_round_full,
                                title = stringRes("Zodl"),
                                subtitle = stringRes("subtitle"),
                                isShielded = true,
                                onCopyClicked = {},
                                onQrClicked = { },
                                onRequestClicked = {},
                                isExpanded = true,
                                onClick = {},
                                colorMode = ZASHI,
                                infoIconButton =
                                    IconButtonState(
                                        R.drawable.ic_receive_zashi_shielded_info,
                                        onClick = {}
                                    )
                            ),
                            ReceiveAddressState(
                                icon = R.drawable.ic_zec_round_full,
                                title = stringRes("Zodl"),
                                subtitle = stringRes("subtitle"),
                                isShielded = false,
                                onCopyClicked = {},
                                onQrClicked = { },
                                onRequestClicked = { },
                                isExpanded = true,
                                onClick = {},
                                colorMode = DEFAULT,
                                infoIconButton =
                                    IconButtonState(
                                        R.drawable.ic_receive_zashi_shielded_info,
                                        onClick = {}
                                    )
                            )
                        ),
                    isLoading = false,
                    onBack = {}
                ),
            appBarState = ZashiMainTopAppBarStateFixture.new()
        )
    }

@PreviewScreens
@Composable
private fun KeystonePreview() =
    ZcashTheme {
        ReceiveView(
            state =
                ReceiveState(
                    items =
                        listOf(
                            ReceiveAddressState(
                                icon = co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone,
                                title = stringRes("Zodl"),
                                subtitle = stringRes("subtitle"),
                                isShielded = true,
                                onCopyClicked = {},
                                onQrClicked = { },
                                onRequestClicked = {},
                                isExpanded = true,
                                onClick = {},
                                colorMode = KEYSTONE,
                                infoIconButton =
                                    IconButtonState(
                                        R.drawable.ic_receive_zashi_shielded_info,
                                        onClick = {}
                                    )
                            ),
                            ReceiveAddressState(
                                icon = co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone,
                                title = stringRes("Zodl"),
                                subtitle = stringRes("subtitle"),
                                isShielded = false,
                                onCopyClicked = {},
                                onQrClicked = { },
                                onRequestClicked = { },
                                isExpanded = true,
                                onClick = {},
                                colorMode = DEFAULT,
                                infoIconButton =
                                    IconButtonState(
                                        R.drawable.ic_receive_zashi_shielded_info,
                                        onClick = {}
                                    )
                            )
                        ),
                    isLoading = false,
                    onBack = {}
                ),
            appBarState = ZashiMainTopAppBarStateFixture.new()
        )
    }
