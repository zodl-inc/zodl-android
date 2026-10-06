package co.electriccoin.zcash.ui.screen.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

internal class ScanGenericAddressVM(
    private val args: ScanGenericAddressArgs,
    private val parseZip321: Zip321ParseUriValidationUseCase,
    private val navigateToScanAddress: NavigateToScanGenericAddressUseCase,
    private val navigateToRedeemGiftCard: NavigateToRedeemGiftCardUseCase,
) : ViewModel() {
    val state = MutableStateFlow(ScanValidationState.NONE)

    private val mutex = Mutex()

    private var hasBeenScannedSuccessfully = false

    /**
     * A gift card link opens the redeem flow and resolves the pending scan as cancelled: a link carries a spending
     * secret, so it is never handed to the caller as an address.
     */
    fun onScanned(result: String) =
        viewModelScope.launch {
            mutex.withLock {
                if (!hasBeenScannedSuccessfully) {
                    if (navigateToRedeemGiftCard.isGiftCardLink(result)) {
                        onGiftCardScanned(result)
                        return@withLock
                    }
                    runCatching {
                        when (val zip321Result = parseZip321(result)) {
                            is Zip321ParseUriValidation.Valid -> onZip321Scanned(zip321Result)
                            is Zip321ParseUriValidation.SingleAddress -> onZip321SingleAddressScanned(zip321Result)
                            else -> onAddressScanned(result)
                        }
                    }
                }
            }
        }

    private suspend fun onGiftCardScanned(link: String) {
        state.update { ScanValidationState.VALID }
        hasBeenScannedSuccessfully = true
        navigateToScanAddress.onScanRedirected(args)
        navigateToRedeemGiftCard.replaceWithRedeem(link.trim())
    }

    private suspend fun onAddressScanned(result: String) {
        state.update { ScanValidationState.VALID }
        navigateToScanAddress.onScanned(
            address = result,
            amount = null,
            args = args
        )
        hasBeenScannedSuccessfully = true
    }

    private suspend fun onZip321SingleAddressScanned(result: Zip321ParseUriValidation.SingleAddress) {
        state.update { ScanValidationState.VALID }
        navigateToScanAddress.onScanned(
            address = result.address,
            amount = null,
            args = args
        )
        hasBeenScannedSuccessfully = true
    }

    private suspend fun onZip321Scanned(result: Zip321ParseUriValidation.Valid) {
        state.update { ScanValidationState.VALID }
        val address =
            result.payment.payments[0]
                .recipientAddress.value
        val amount =
            result.payment.payments[0]
                .nonNegativeAmount
                ?.toZecValueString()
                ?.toBigDecimal() ?: BigDecimal.ZERO
        navigateToScanAddress.onScanned(address, amount, args)
        hasBeenScannedSuccessfully = true
    }

    fun onScannedError() =
        viewModelScope.launch {
            mutex.withLock {
                if (!hasBeenScannedSuccessfully) {
                    state.update { ScanValidationState.INVALID }
                }
            }
        }

    fun onImageScanned(result: ImageToQrCodeResult) =
        viewModelScope.launch {
            mutex.withLock {
                if (!hasBeenScannedSuccessfully) {
                    when (result) {
                        is ImageToQrCodeResult.SingleCode -> {
                            onScanned(result.text)
                        }

                        ImageToQrCodeResult.MultipleCodes -> {
                            hasBeenScannedSuccessfully = false
                            state.update { ScanValidationState.SEVERAL_CODES_FOUND }
                        }

                        ImageToQrCodeResult.NoCode -> {
                            hasBeenScannedSuccessfully = false
                            state.update { ScanValidationState.INVALID_IMAGE }
                        }
                    }
                }
            }
        }

    fun onBack() = viewModelScope.launch { navigateToScanAddress.onScanCancelled(args) }
}
