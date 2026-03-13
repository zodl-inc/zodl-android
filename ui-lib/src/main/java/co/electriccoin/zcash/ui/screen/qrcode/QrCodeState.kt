package co.electriccoin.zcash.ui.screen.qrcode

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import cash.z.ecc.android.sdk.model.WalletAddress
import co.electriccoin.zcash.ui.design.component.QrState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource

@Immutable
sealed class QrCodeState {
    @Immutable
    data object Loading : QrCodeState()

    @Immutable
    data class Prepared(
        val qrCodeType: QrCodeType,
        val formatterAddress: StyledStringResource,
        val walletAddress: WalletAddress,
        val onAddressCopy: (String) -> Unit,
        val onQrCodeShare: (String) -> Unit,
        val onBack: () -> Unit,
    ) : QrCodeState() {
        fun toQrState(
            contentDescription: StringResource? = null,
            @DrawableRes centerImageResId: Int? = null
        ) = QrState(
            qrData = walletAddress.address,
            contentDescription = contentDescription,
            centerImage = centerImageResId
        )
    }
}

enum class QrCodeType {
    ZASHI,
    KEYSTONE
}
