@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.request.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.z.ecc.sdk.type.ZcashCurrency
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.OldZashiBottomBar
import co.electriccoin.zcash.ui.design.component.QrCodeDefaults
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.request.model.AmountState
import co.electriccoin.zcash.ui.screen.request.model.MemoState
import co.electriccoin.zcash.ui.screen.request.model.QrCodeState
import co.electriccoin.zcash.ui.screen.request.model.Request
import co.electriccoin.zcash.ui.screen.request.model.RequestCurrency
import co.electriccoin.zcash.ui.screen.request.model.RequestState
import kotlin.math.roundToInt

@Composable
@PreviewScreens
private fun RequestLoadingPreview() =
    ZcashTheme(forceDarkMode = true) {
        RequestView(
            state = RequestState.Loading,
            snackbarHostState = SnackbarHostState(),
        )
    }

@Composable
@PreviewScreens
private fun RequestPreview() =
    ZcashTheme(forceDarkMode = false) {
        RequestView(
            state =
                RequestState.Amount(
                    request =
                        Request(
                            amountState = AmountState("2.25", RequestCurrency.ZEC, true),
                            memoState = MemoState.Valid("", 0, "2.25"),
                            qrCodeState =
                                QrCodeState(
                                    "zcash:t1duiEGg7b39nfQee3XaTY4f5McqfyJKhBi?amount=1&memo=VGhpcyBpcyBhIHNpbXBsZSBt",
                                    "0.25",
                                    memo = "Text memo",
                                ),
                        ),
                    exchangeRateState = ExchangeRateState.OptedOut,
                    zcashCurrency = ZcashCurrency.ZEC,
                    onAmount = {},
                    onSwitch = {},
                    onBack = {}
                ) {},
            snackbarHostState = SnackbarHostState(),
        )
    }

@Composable
internal fun RequestView(
    state: RequestState,
    snackbarHostState: SnackbarHostState,
) {
    when (state) {
        RequestState.Loading -> {
            CircularScreenProgressIndicator()
        }

        is RequestState.Prepared -> {
            BlankBgScaffold(
                topBar = {
                    RequestTopAppBar(
                        onBack = state.onBack,
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    RequestBottomBar(state = state)
                }
            ) { paddingValues ->
                RequestContents(
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
private fun RequestTopAppBar(
    onBack: () -> Unit,
) {
    ZashiSmallTopAppBar(
        title = stringResource(id = R.string.receive_request),
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        },
    )
}

@Composable
private fun RequestBottomBar(
    state: RequestState.Prepared,
    modifier: Modifier = Modifier,
) {
    OldZashiBottomBar(modifier = modifier.fillMaxWidth()) {
        when (state) {
            is RequestState.Amount -> {
                ZashiButton(
                    text = stringResource(id = R.string.general_next),
                    onClick = state.onDone,
                    enabled = state.request.amountState.isValid == true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                )
            }

            is RequestState.Memo -> {
                ZashiButton(
                    enabled = state.request.memoState.isValid(),
                    onClick = state.onDone,
                    text = stringResource(id = R.string.receive_request),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                )
            }

            is RequestState.QrCode -> {
                val sizePixels = with(LocalDensity.current) { DEFAULT_QR_CODE_SIZE.toPx() }.roundToInt()
                val colors = QrCodeDefaults.colors()

                ZashiButton(
                    text = stringResource(id = R.string.requestZec_summary_shareQR),
                    icon = R.drawable.ic_share,
                    onClick = { state.onQrCodeShare(colors, sizePixels, state.request.qrCodeState.requestUri) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingTiny))

                ZashiButton(
                    colors = ZashiButtonDefaults.secondaryColors(),
                    onClick = state.onClose,
                    text = stringResource(id = R.string.general_close),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                )
            }
        }
    }
}

val DEFAULT_QR_CODE_SIZE = 320.dp

@Composable
private fun RequestContents(
    state: RequestState.Prepared,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when (state) {
            is RequestState.Amount -> {
                RequestAmountView(state = state)
            }

            is RequestState.Memo -> {
                RequestMemoView(state = state)
            }

            is RequestState.QrCode -> {
                RequestQrCodeView(state = state)
            }
        }
    }
}

// TODO [#1635]: Learn AutoSizingText scale up
// TODO [#1635]: https://github.com/Electric-Coin-Company/zashi-android/issues/1635
@Composable
internal fun AutoSizingText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    var fontSize by remember { mutableStateOf(style.fontSize) }

    Text(
        text = text,
        fontSize = fontSize,
        fontFamily = style.fontFamily,
        lineHeight = style.lineHeight,
        fontWeight = style.fontWeight,
        maxLines = 1,
        modifier = modifier,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowHeight) {
                fontSize = (fontSize.value - 1).sp
            } else {
                // We should make the text bigger again
            }
        }
    )
}
