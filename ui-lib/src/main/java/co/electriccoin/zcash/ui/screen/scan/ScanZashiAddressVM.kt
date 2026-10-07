package co.electriccoin.zcash.ui.screen.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.common.usecase.OnAddressScannedUseCase
import co.electriccoin.zcash.ui.common.usecase.OnZip321ScannedUseCase
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ScanZashiAddressVM(
    private val args: ScanArgs,
    private val synchronizerProvider: SynchronizerProvider,
    private val zip321ParseUriValidationUseCase: Zip321ParseUriValidationUseCase,
    private val onAddressScanned: OnAddressScannedUseCase,
    private val zip321Scanned: OnZip321ScannedUseCase,
    private val navigateToRedeemGiftCard: NavigateToRedeemGiftCardUseCase,
) : ViewModel() {
    val state = MutableStateFlow(ScanValidationState.NONE)

    private val mutex = Mutex()

    private var hasBeenScannedSuccessfully = false

    /**
     * Gift card links are handled first and open the redeem flow: a link carries a spending secret, so it must never
     * reach the ZIP-321 or address parsers, nor their logging.
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
                        val zip321ValidationResult = zip321ParseUriValidationUseCase(result)
                        val addressValidationResult = synchronizerProvider.getSynchronizer().validateAddress(result)

                        when {
                            zip321ValidationResult is Zip321ParseUriValidation.Valid -> {
                                onZip321Scanned(zip321ValidationResult)
                            }

                            zip321ValidationResult is Zip321ParseUriValidation.SingleAddress -> {
                                onZip321SingleAddressScanned(zip321ValidationResult)
                            }

                            addressValidationResult is AddressType.Valid -> {
                                onAddressScanned(result, addressValidationResult)
                            }

                            else -> {
                                onInvalidScan()
                            }
                        }
                    }
                }
            }
        }

    private fun onGiftCardScanned(link: String) {
        state.update { ScanValidationState.VALID }
        hasBeenScannedSuccessfully = true
        navigateToRedeemGiftCard.replaceWithRedeem(link.trim())
    }

    private fun onInvalidScan() {
        hasBeenScannedSuccessfully = false
        state.update { ScanValidationState.INVALID }
    }

    private fun onAddressScanned(
        result: String,
        addressValidationResult: AddressType
    ) {
        state.update { ScanValidationState.VALID }
        onAddressScanned(result, addressValidationResult, args)
        hasBeenScannedSuccessfully = true
    }

    private suspend fun onZip321SingleAddressScanned(zip321ValidationResult: Zip321ParseUriValidation.SingleAddress) {
        val singleAddressValidation =
            synchronizerProvider
                .getSynchronizer()
                .validateAddress(zip321ValidationResult.address)
        if (singleAddressValidation is AddressType.Invalid) {
            state.update { ScanValidationState.INVALID }
        } else {
            state.update { ScanValidationState.VALID }
            onAddressScanned(zip321ValidationResult.address, singleAddressValidation, args)
        }
        hasBeenScannedSuccessfully = true
    }

    private suspend fun onZip321Scanned(zip321ValidationResult: Zip321ParseUriValidation.Valid) {
        state.update { ScanValidationState.VALID }
        zip321Scanned(zip321ValidationResult, args)
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
}
