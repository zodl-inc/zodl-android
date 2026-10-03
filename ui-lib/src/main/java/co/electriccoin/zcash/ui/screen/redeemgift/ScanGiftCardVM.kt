package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.common.usecase.ReadClipboardTextUseCase
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The first step of the redeem flow: scan a gift card QR code, pick an image of one, or paste its link.
 */
class ScanGiftCardVM(
    private val navigateToRedeemGiftCard: NavigateToRedeemGiftCardUseCase,
    private val readClipboardText: ReadClipboardTextUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ScanValidationState.NONE)

    val state = mutableState.asStateFlow()

    private val mutex = Mutex()

    private var hasBeenScannedSuccessfully = false

    fun onScanned(result: String) =
        viewModelScope.launch {
            mutex.withLock { handle(result) }
        }

    fun onPaste() =
        viewModelScope.launch {
            mutex.withLock { handle(readClipboardText().orEmpty()) }
        }

    fun onScannedError() =
        viewModelScope.launch {
            mutex.withLock {
                if (!hasBeenScannedSuccessfully) mutableState.value = ScanValidationState.INVALID
            }
        }

    fun onImageScanned(result: ImageToQrCodeResult) =
        viewModelScope.launch {
            mutex.withLock {
                if (hasBeenScannedSuccessfully) return@withLock
                when (result) {
                    is ImageToQrCodeResult.SingleCode -> handle(result.text)
                    ImageToQrCodeResult.MultipleCodes -> mutableState.value = ScanValidationState.SEVERAL_CODES_FOUND
                    ImageToQrCodeResult.NoCode -> mutableState.value = ScanValidationState.INVALID_IMAGE
                }
            }
        }

    fun onBack() = navigationRouter.back()

    private fun handle(text: String) {
        if (hasBeenScannedSuccessfully) return
        if (navigateToRedeemGiftCard.isGiftCardLink(text)) {
            hasBeenScannedSuccessfully = true
            mutableState.value = ScanValidationState.VALID
            navigateToRedeemGiftCard.replaceWithRedeem(text.trim())
        } else {
            mutableState.value = ScanValidationState.INVALID
        }
    }
}
