package co.electriccoin.zcash.ui.screen.request.model

import androidx.annotation.DrawableRes
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.sdk.type.ZcashCurrency
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.QrState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.QrCodeColors
import co.electriccoin.zcash.ui.design.util.StringResource

internal sealed class RequestState {
    data object Loading : RequestState()

    sealed class Prepared(
        open val onBack: () -> Unit
    ) : RequestState()

    data class Amount(
        val request: Request,
        val exchangeRateState: ExchangeRateState,
        val zcashCurrency: ZcashCurrency,
        val onAmount: (OnAmount) -> Unit,
        val onSwitch: (RequestCurrency) -> Unit,
        override val onBack: () -> Unit,
        val onDone: () -> Unit,
    ) : Prepared(onBack)

    data class Memo(
        val icon: ImageResource,
        val request: Request,
        val walletAddress: WalletAddress,
        val zcashCurrency: ZcashCurrency,
        val onMemo: (MemoState) -> Unit,
        val onDone: () -> Unit,
        override val onBack: () -> Unit,
    ) : Prepared(onBack)

    data class QrCode(
        val icon: Int,
        val request: Request,
        val walletAddress: WalletAddress,
        val onQrCodeShare: (colors: QrCodeColors, pixels: Int, uri: String) -> Unit,
        override val onBack: () -> Unit,
        val onClose: () -> Unit,
        val zcashCurrency: ZcashCurrency,
    ) : Prepared(onBack) {
        fun toQrState(
            contentDescription: StringResource? = null,
            @DrawableRes centerImage: Int? = null
        ) = QrState(
            qrData = request.qrCodeState.requestUri,
            contentDescription = contentDescription,
            centerImage = centerImage
        )
    }
}
