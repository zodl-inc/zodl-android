package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.redeemgift.paste.PasteGiftCardLinkArgs
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The first step of the redeem flow: scan a gift card QR code, pick an image of one, or go on to paste its link.
 * Every callback runs on the main thread without suspending, so they never interleave.
 */
class ScanGiftCardVM(
    args: ScanGiftCardArgs,
    private val navigateToRedeemGiftCard: NavigateToRedeemGiftCardUseCase,
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

    private var hasBeenScannedSuccessfully = false

    fun onScanned(result: String) = viewModelScope.launch { handle(result) }

    /**
     * Opens the screen where the user pastes or types the gift card link; the scanner stays below it. Ignored once a
     * scanned link is taking the scanner's place.
     */
    fun onPaste() {
        if (hasBeenScannedSuccessfully) return
        navigationRouter.forward(PasteGiftCardLinkArgs)
    }

    fun onScannedError() =
        viewModelScope.launch {
            if (!hasBeenScannedSuccessfully) setValidation(ScanValidationState.INVALID)
        }

    fun onImageScanned(result: ImageToQrCodeResult) =
        viewModelScope.launch {
            if (hasBeenScannedSuccessfully) return@launch
            when (result) {
                is ImageToQrCodeResult.SingleCode -> handle(result.text)
                ImageToQrCodeResult.MultipleCodes -> setValidation(ScanValidationState.SEVERAL_CODES_FOUND)
                ImageToQrCodeResult.NoCode -> setValidation(ScanValidationState.INVALID_IMAGE)
            }
        }

    private fun onBack() = navigationRouter.back()

    /**
     * Opens the redeem screen for [text] if it is a gift card link and no link was taken yet.
     *
     * This must stay non-suspending. Its callers all run on the main thread, so without a suspension point between
     * reading and setting [hasBeenScannedSuccessfully] no other scan can interleave, and that is what makes the
     * scanned-once guard race-free without a mutex. Adding a suspending call here would need a mutex instead.
     */
    private fun handle(text: String) {
        if (hasBeenScannedSuccessfully) return
        if (navigateToRedeemGiftCard.isGiftCardLink(text)) {
            hasBeenScannedSuccessfully = true
            setValidation(ScanValidationState.VALID)
            navigateToRedeemGiftCard.replaceGiftCardScanWithRedeem(text.trim())
        } else {
            setValidation(ScanValidationState.INVALID)
        }
    }

    private fun setValidation(validation: ScanValidationState) =
        mutableState.update { it.copy(validation = validation) }
}
