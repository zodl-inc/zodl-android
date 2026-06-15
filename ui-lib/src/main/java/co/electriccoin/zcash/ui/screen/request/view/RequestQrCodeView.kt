package co.electriccoin.zcash.ui.screen.request.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.WalletAddress
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeColors
import co.electriccoin.zcash.ui.design.component.ZashiQr
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.request.model.RequestState

@Composable
internal fun RequestQrCodeView(
    state: RequestState.QrCode,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZcashTheme.dimens.spacingLarge),
    ) {
        Spacer(Modifier.height(ZcashTheme.dimens.spacingDefault))

        when (state.walletAddress) {
            is WalletAddress.Transparent -> {
                ZashiBadge(
                    text = stringResource(id = R.string.request_privacy_level_transparent),
                    leadingIconVector = painterResource(id = R.drawable.ic_alert_circle),
                    colors =
                        ZashiBadgeColors(
                            border = ZashiColors.Utility.WarningYellow.utilityOrange200,
                            text = ZashiColors.Utility.WarningYellow.utilityOrange700,
                            container = ZashiColors.Utility.WarningYellow.utilityOrange50,
                        )
                )
            }

            is WalletAddress.Unified, is WalletAddress.Sapling -> {
                ZashiBadge(
                    text = stringResource(id = R.string.request_privacy_level_shielded),
                    leadingIconVector = painterResource(id = R.drawable.ic_solid_check),
                    colors =
                        ZashiBadgeColors(
                            border = ZashiColors.Utility.Purple.utilityPurple200,
                            text = ZashiColors.Utility.Purple.utilityPurple700,
                            container = ZashiColors.Utility.Purple.utilityPurple50,
                        )
                )
            }

            else -> {
                error("Unsupported address type")
            }
        }

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingMid))

        RequestQrCodeZecAmountView(
            state = state,
            modifier = Modifier.padding(horizontal = ZcashTheme.dimens.spacingSmall)
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingBig))

        QrCode(
            requestState = state,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingDefault))
    }
}

@Composable
private fun ColumnScope.QrCode(
    requestState: RequestState.QrCode,
    modifier: Modifier = Modifier
) {
    ZashiQr(
        state =
            requestState.toQrState(
                contentDescription = stringRes(R.string.request_qr_code_content_description),
                centerImage = requestState.icon
            ),
        modifier = modifier.align(CenterHorizontally),
    )
}

@Composable
private fun RequestQrCodeZecAmountView(
    state: RequestState.QrCode,
    modifier: Modifier = Modifier
) {
    val zecText =
        buildAnnotatedString {
            withStyle(style = SpanStyle(color = ZashiColors.Text.textPrimary)) {
                append(state.request.qrCodeState.zecAmount)
            }
            append("\u2009") // Add an extra thin space between the texts
            withStyle(style = SpanStyle(color = ZashiColors.Text.textQuaternary)) {
                append(state.zcashCurrency.localizedName(LocalContext.current))
            }
        }

    AutoSizingText(
        text = zecText,
        style =
            ZashiTypography.header1.copy(
                fontWeight = FontWeight.SemiBold
            ),
        modifier = modifier
    )
}
