package co.electriccoin.zcash.ui.screen.qrcode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.styleAsAddress
import co.electriccoin.zcash.ui.screen.qrcode.ext.fromReceiveAddressType
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QrCodeVM(
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
    private val addressTypeOrdinal: Int,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val navigationRouter: NavigationRouter,
    private val shareQR: ShareQRUseCase,
    private val context: Context,
) : ViewModel() {
    internal val state =
        observeSelectedWalletAccount
            .require()
            .map { account ->
                val walletAddress = account.fromReceiveAddressType(ReceiveAddressType.fromOrdinal(addressTypeOrdinal))

                if (walletAddress == null) {
                    QrCodeState.Loading
                } else {
                    QrCodeState.Prepared(
                        walletAddress = walletAddress,
                        formatterAddress =
                            if (walletAddress is WalletAddress.Transparent) {
                                stringRes(walletAddress.address).styleAsAddress()
                            } else {
                                stringResByAddress(walletAddress.address, true)
                            },
                        onAddressCopy = { address -> onAddressCopyClick(address) },
                        onQrCodeShare = {
                            viewModelScope.launch {
                                shareQR(
                                    qrData = it,
                                    shareText = context.getString(R.string.qr_code_share_chooser_text, CURRENCY_TICKER),
                                    sharePickerText =
                                        context.getString(
                                            R.string.qr_code_share_chooser_title,
                                            CURRENCY_TICKER
                                        ),
                                    filenamePrefix = "zcash_address_qr_",
                                    centerIcon =
                                        if (walletAddress is WalletAddress.Transparent) {
                                            R.drawable.ic_zec_qr_transparent
                                        } else {
                                            R.drawable.ic_zec_qr_shielded
                                        },
                                )
                            }
                        },
                        onBack = ::onBack,
                        qrCodeType =
                            when (account) {
                                is KeystoneAccount -> QrCodeType.KEYSTONE
                                is ZashiAccount -> QrCodeType.ZASHI
                            }
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = QrCodeState.Loading
            )

    private fun onBack() = navigationRouter.back()

    private fun onAddressCopyClick(address: String) = copyToClipboard(address)
}
