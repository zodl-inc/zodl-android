package co.electriccoin.zcash.ui.screen.voting.signkeystone

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.QrState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.ZashiAccountInfoListItemState

data class AuthorizeVoteSignKeystoneState(
    val onBack: () -> Unit,
    val accountInfo: ZashiAccountInfoListItemState,
    val badgeText: StringResource,
    val qrData: String?,
    val generateNextQrCode: () -> Unit,
    val currentBundleNumber: Int,
    val totalBundles: Int,
    val signedBundleCount: Int,
    val signedZec: StringResource,
    val pendingZec: StringResource,
    val memoText: StringResource,
    val useSignedBundlesOnly: UseSignedBundlesOnlyState?,
    val scanButton: ButtonState,
) {
    val bundleProgress: Float
        get() = if (totalBundles > 0) signedBundleCount.toFloat() / totalBundles else 0f

    fun toQrState(): QrState {
        val data = requireNotNull(qrData) { "QR data required at this point" }
        return QrState(qrData = data, contentDescription = null, centerImage = null)
    }
}

data class UseSignedBundlesOnlyState(
    val remainingZec: StringResource,
    val onClick: () -> Unit,
)
