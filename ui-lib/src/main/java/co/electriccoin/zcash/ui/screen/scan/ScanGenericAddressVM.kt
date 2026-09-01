package co.electriccoin.zcash.ui.screen.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.common.model.CrossPayParseResult
import co.electriccoin.zcash.ui.common.model.CrossPayRequestParser
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
) : ViewModel() {
    val state = MutableStateFlow(ScanValidationState.NONE)

    private val mutex = Mutex()

    private var hasBeenScannedSuccessfully = false

    fun onScanned(result: String) =
        viewModelScope.launch {
            mutex.withLock {
                if (!hasBeenScannedSuccessfully) {
                    runCatching {
                        when (val zip321Result = parseZip321(result)) {
                            is Zip321ParseUriValidation.Valid -> onZip321Scanned(zip321Result)
                            is Zip321ParseUriValidation.SingleAddress -> onZip321SingleAddressScanned(zip321Result)
                            else -> onAddressScanned(result)
                        }
                    }.onFailure {
                        // e.g. a JNI/native-library failure from CrossPayRequestParser.parse() --
                        // without this, `state` never transitions and the scan screen silently
                        // stops responding (same failure class as #2441 for Keystone PCZT scans).
                        state.update { ScanValidationState.INVALID }
                    }
                }
            }
        }

    private suspend fun onAddressScanned(result: String) {
        when (val parseResult = CrossPayRequestParser.parse(result)) {
            is CrossPayParseResult.Request -> {
                state.update { ScanValidationState.VALID }
                navigateToScanAddress.onScanned(
                    address = parseResult.request.address,
                    amount = parseResult.request.amount?.value,
                    args = args,
                    request = parseResult.request
                )
                hasBeenScannedSuccessfully = true
            }

            CrossPayParseResult.NotAPaymentRequest -> {
                state.update { ScanValidationState.VALID }
                navigateToScanAddress.onScanned(address = result, amount = null, args = args)
                hasBeenScannedSuccessfully = true
            }

            CrossPayParseResult.Rejected -> {
                // A recognized-but-out-of-scope URI (a Solana Pay transaction-request link, or an
                // unsupported EIP-681 method) must not fall through to being treated as a literal
                // recipient address -- it's a URI this app explicitly declined to honour, not an
                // address. Leaves hasBeenScannedSuccessfully false so the user can rescan.
                state.update { ScanValidationState.INVALID }
            }
        }
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
