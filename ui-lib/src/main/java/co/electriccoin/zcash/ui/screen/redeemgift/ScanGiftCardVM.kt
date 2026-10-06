package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.ClearClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.common.usecase.ReadClipboardTextUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The first step of the redeem flow: scan a gift card QR code, pick an image of one, or paste its link.
 */
class ScanGiftCardVM(
    args: ScanGiftCardArgs,
    private val navigateToRedeemGiftCard: NavigateToRedeemGiftCardUseCase,
    private val readClipboardText: ReadClipboardTextUseCase,
    private val clearClipboard: ClearClipboardUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            ScanGiftCardState(
                validation = ScanValidationState.NONE,
                invalidQrText = stringRes(R.string.redeemGift_scan_invalid),
                infoText = if (args.isFromExternalLink) stringRes(R.string.redeemGift_scan_externalLink) else null,
                onBack = ::onBack
            )
        )

    val state: StateFlow<ScanGiftCardState> = mutableState.asStateFlow()

    private val mutex = Mutex()

    private var hasBeenScannedSuccessfully = false

    fun onScanned(result: String) =
        viewModelScope.launch {
            mutex.withLock { handle(result) }
        }

    fun onPaste() =
        viewModelScope.launch {
            mutex.withLock {
                if (handle(readClipboardText().orEmpty())) clearClipboard()
            }
        }

    fun onScannedError() =
        viewModelScope.launch {
            mutex.withLock {
                if (!hasBeenScannedSuccessfully) setValidation(ScanValidationState.INVALID)
            }
        }

    fun onImageScanned(result: ImageToQrCodeResult) =
        viewModelScope.launch {
            mutex.withLock {
                if (hasBeenScannedSuccessfully) return@withLock
                when (result) {
                    is ImageToQrCodeResult.SingleCode -> handle(result.text)
                    ImageToQrCodeResult.MultipleCodes -> setValidation(ScanValidationState.SEVERAL_CODES_FOUND)
                    ImageToQrCodeResult.NoCode -> setValidation(ScanValidationState.INVALID_IMAGE)
                }
            }
        }

    private fun onBack() = navigationRouter.back()

    /**
     * Opens the redeem screen for [text] if it is a gift card link. Returns whether it was one and was taken.
     */
    private fun handle(text: String): Boolean {
        if (hasBeenScannedSuccessfully) return false
        return if (navigateToRedeemGiftCard.isGiftCardLink(text)) {
            hasBeenScannedSuccessfully = true
            setValidation(ScanValidationState.VALID)
            navigateToRedeemGiftCard.replaceWithRedeem(text.trim())
            true
        } else {
            setValidation(ScanValidationState.INVALID)
            false
        }
    }

    private fun setValidation(validation: ScanValidationState) =
        mutableState.update { it.copy(validation = validation) }
}
