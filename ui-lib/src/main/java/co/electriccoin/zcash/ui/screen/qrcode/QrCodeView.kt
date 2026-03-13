@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.qrcode

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.model.WalletAddress
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.OldZashiBottomBar
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeColors
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiQr
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.styleAsAddress
import kotlinx.coroutines.runBlocking

@Composable
@PreviewScreens
private fun QrCodeLoadingPreview() =
    ZcashTheme(forceDarkMode = true) {
        QrCodeView(
            state = QrCodeState.Loading,
            snackbarHostState = SnackbarHostState(),
        )
    }

@Composable
@PreviewScreens
private fun ZashiPreview() =
    ZcashTheme(forceDarkMode = false) {
        val address = runBlocking { WalletAddressFixture.unified() }
        QrCodeView(
            state =
                QrCodeState.Prepared(
                    qrCodeType = QrCodeType.ZASHI,
                    walletAddress = address,
                    formatterAddress = stringResByAddress(address.address, true),
                    onAddressCopy = {},
                    onQrCodeShare = {},
                    onBack = {},
                ),
            snackbarHostState = SnackbarHostState(),
        )
    }

@Composable
@PreviewScreens
private fun KeystonePreview() =
    ZcashTheme(forceDarkMode = false) {
        val address = runBlocking { WalletAddressFixture.unified() }
        QrCodeView(
            state =
                QrCodeState.Prepared(
                    qrCodeType = QrCodeType.KEYSTONE,
                    walletAddress = address,
                    formatterAddress = stringResByAddress(address.address, true),
                    onAddressCopy = {},
                    onQrCodeShare = {},
                    onBack = {},
                ),
            snackbarHostState = SnackbarHostState(),
        )
    }

@Composable
internal fun QrCodeView(
    state: QrCodeState,
    snackbarHostState: SnackbarHostState,
) {
    when (state) {
        QrCodeState.Loading -> {
            CircularScreenProgressIndicator()
        }

        is QrCodeState.Prepared -> {
            BlankBgScaffold(
                topBar = {
                    QrCodeTopAppBar(
                        onBack = state.onBack,
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    QrCodeBottomBar(
                        state = state,
                    )
                }
            ) { paddingValues ->
                QrCodeContents(
                    state = state,
                    modifier =
                        Modifier.padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding()
                        ),
                )
            }
        }
    }
}

@Composable
private fun QrCodeTopAppBar(
    onBack: () -> Unit,
) {
    ZashiSmallTopAppBar(
        title = null,
        navigationAction = {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .padding(horizontal = ZcashTheme.dimens.spacingDefault)
                        // Making the size bigger by 3.dp so the rounded image corners are not stripped out
                        .size(43.dp),
            ) {
                Image(
                    painter =
                        painterResource(
                            id = co.electriccoin.zcash.ui.design.R.drawable.ic_close_full
                        ),
                    contentDescription = stringResource(id = R.string.qr_code_close_content_description),
                    modifier =
                        Modifier
                            .padding(all = 3.dp)
                )
            }
        },
    )
}

@Composable
private fun QrCodeBottomBar(
    state: QrCodeState.Prepared,
) {
    val buttonModifier =
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()

    OldZashiBottomBar {
        ZashiButton(
            text = stringResource(id = R.string.qr_code_share_btn),
            icon = R.drawable.ic_share,
            onClick = { state.onQrCodeShare(state.walletAddress.address) },
            modifier = buttonModifier
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingTiny))

        ZashiButton(
            text = stringResource(id = R.string.qr_code_copy_btn),
            icon = R.drawable.ic_qr_copy,
            onClick = { state.onAddressCopy(state.walletAddress.address) },
            colors = ZashiButtonDefaults.secondaryColors(),
            modifier = buttonModifier
        )
    }
}

@Composable
private fun QrCodeContents(
    state: QrCodeState.Prepared,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZcashTheme.dimens.screenHorizontalSpacingRegular),
    ) {
        Spacer(Modifier.height(ZcashTheme.dimens.spacingDefault))

        QrCodePanel(state)
    }
}

@Composable
private fun QrCodePanel(
    state: QrCodeState.Prepared,
    modifier: Modifier = Modifier
) {
    var expandedAddress by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .padding(vertical = ZcashTheme.dimens.spacingDefault),
        horizontalAlignment = CenterHorizontally
    ) {
        QrCode(
            state = state,
            modifier =
                Modifier
                    .padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingUpLarge))

        val addressType = state.walletAddress.toAddressType()

        ZashiBadge(
            text = stringResource(id = addressType.badgeText),
            leadingIconVector = painterResource(id = addressType.badgeIcon),
            colors = addressType.badgeColors()
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingDefault))

        Text(
            text =
                stringResource(
                    id =
                        when (state.walletAddress) {
                            is WalletAddress.Unified ->
                                when (state.qrCodeType) {
                                    QrCodeType.ZASHI -> R.string.qr_code_wallet_address_shielded
                                    QrCodeType.KEYSTONE -> R.string.qr_code_wallet_address_shielded_keystone
                                }

                            is WalletAddress.Sapling ->
                                when (state.qrCodeType) {
                                    QrCodeType.ZASHI -> R.string.qr_code_wallet_address_sapling
                                    QrCodeType.KEYSTONE -> R.string.qr_code_wallet_address_sapling_keystone
                                }

                            is WalletAddress.Transparent -> R.string.qr_code_wallet_address_transparent

                            else -> error("Unsupported address type: ${state.walletAddress}")
                        }
                ),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingDefault))

        @OptIn(ExperimentalFoundationApi::class)
        Text(
            text =
                if (expandedAddress) {
                    StringResource.ByString(state.walletAddress.address).styleAsAddress()
                } else {
                    state.formatterAddress
                }.getValue(),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
            textAlign = TextAlign.Center,
            maxLines =
                if (expandedAddress) {
                    Int.MAX_VALUE
                } else {
                    2
                },
            modifier =
                Modifier
                    .animateContentSize()
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { expandedAddress = !expandedAddress },
                        onLongClick = { state.onAddressCopy(state.walletAddress.address) }
                    )
        )
    }
}

@Composable
private fun ColumnScope.QrCode(
    state: QrCodeState.Prepared,
    modifier: Modifier = Modifier
) {
    val addressType = state.walletAddress.toAddressType()
    ZashiQr(
        state =
            state.toQrState(
                contentDescription = stringRes(addressType.qrContentDescription),
                centerImageResId = addressType.qrCenterImage
            ),
        modifier = modifier.align(CenterHorizontally),
    )
}

private enum class AddressType {
    UNIFIED,
    SAPLING,
    TRANSPARENT;

    val badgeText: Int
        get() =
            when (this) {
                UNIFIED, SAPLING -> R.string.qr_code_privacy_level_shielded
                TRANSPARENT -> R.string.qr_code_privacy_level_transparent
            }

    val badgeIcon: Int
        get() =
            when (this) {
                UNIFIED, SAPLING -> R.drawable.ic_solid_check
                TRANSPARENT -> R.drawable.ic_alert_circle
            }

    @Composable
    fun badgeColors() =
        when (this) {
            UNIFIED, SAPLING ->
                ZashiBadgeColors(
                    border = ZashiColors.Utility.Purple.utilityPurple200,
                    text = ZashiColors.Utility.Purple.utilityPurple700,
                    container = ZashiColors.Utility.Purple.utilityPurple50,
                )

            TRANSPARENT ->
                ZashiBadgeColors(
                    border = ZashiColors.Utility.WarningYellow.utilityOrange200,
                    text = ZashiColors.Utility.WarningYellow.utilityOrange700,
                    container = ZashiColors.Utility.WarningYellow.utilityOrange50,
                )
        }

    val qrContentDescription: Int
        get() =
            when (this) {
                UNIFIED -> R.string.qr_code_unified_content_description
                SAPLING -> R.string.qr_code_sapling_content_description
                TRANSPARENT -> R.string.qr_code_transparent_content_description
            }

    val qrCenterImage: Int
        get() =
            when (this) {
                UNIFIED, SAPLING -> R.drawable.ic_zec_qr_shielded
                TRANSPARENT -> R.drawable.ic_zec_qr_transparent
            }
}

private fun WalletAddress.toAddressType() =
    when (this) {
        is WalletAddress.Unified -> AddressType.UNIFIED
        is WalletAddress.Sapling -> AddressType.SAPLING
        is WalletAddress.Transparent -> AddressType.TRANSPARENT
        else -> error("Unsupported address type: $this")
    }
