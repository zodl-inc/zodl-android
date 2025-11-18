package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.net.Uri
import android.widget.Toast
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import co.electriccoin.zcash.ui.screen.scan.ImageUriToQrCodeConverter
import co.electriccoin.zcash.ui.screen.scan.ScanArgs
import co.electriccoin.zcash.ui.screen.scan.ScanFlow

class HandleSharedPaymentUseCase(
    private val context: Context,
    private val synchronizerProvider: SynchronizerProvider,
    private val zip321ParseUriValidationUseCase: Zip321ParseUriValidationUseCase,
    private val onAddressScanned: OnAddressScannedUseCase,
    private val onZip321Scanned: OnZip321ScannedUseCase
) {
    private val imageUriToQrCodeConverter = ImageUriToQrCodeConverter()

    suspend operator fun invoke(raw: String) {
        val synchronizer =
            runCatching { synchronizerProvider.getSynchronizer() }
                .getOrElse {
                    Twig.info { "Cannot process shared payment - wallet not ready" }
                    return
                }

        val args = ScanArgs(flow = ScanFlow.HOMEPAGE)

        if (handleCandidate(raw, args, synchronizer)) {
            return
        }

        val tokens =
            raw
                .split(Regex("\\s+"))
                .map { token -> token.trim().trim('.', ',', '!', '?', ';', ':') }
                .filter { token -> token.isNotBlank() && token.length > "zcash:".length }

        for (token in tokens) {
            if (handleCandidate(token, args, synchronizer)) {
                return
            }
        }

        showInvalidSharedContentToast()
    }

    suspend operator fun invoke(uri: Uri) {
        val qrCode =
            runCatching { imageUriToQrCodeConverter(context, uri) }
                .getOrNull()

        if (qrCode == null) {
            showInvalidSharedContentToast()
            return
        }

        invoke(qrCode)
    }

    private suspend fun handleCandidate(
        candidate: String,
        args: ScanArgs,
        synchronizer: Synchronizer
    ): Boolean {
        val zip321ValidationResult = zip321ParseUriValidationUseCase(candidate)
        val addressValidationResult = synchronizer.validateAddress(candidate)

        return when {
            zip321ValidationResult is Zip321ParseUriValidation.Valid -> {
                onZip321Scanned(zip321ValidationResult, args)
                true
            }

            zip321ValidationResult is Zip321ParseUriValidation.SingleAddress -> {
                onZip321SingleAddressScanned(zip321ValidationResult, args, synchronizer)
                true
            }

            addressValidationResult is AddressType.Valid -> {
                onAddressScanned(candidate, addressValidationResult, args)
                true
            }

            else -> false
        }
    }

    private suspend fun onZip321SingleAddressScanned(
        zip321ValidationResult: Zip321ParseUriValidation.SingleAddress,
        args: ScanArgs,
        synchronizer: Synchronizer
    ) {
        val singleAddressValidation = synchronizer.validateAddress(zip321ValidationResult.address)
        if (singleAddressValidation is AddressType.Valid) {
            onAddressScanned(zip321ValidationResult.address, singleAddressValidation, args)
        }
    }

    private fun showInvalidSharedContentToast() {
        Toast
            .makeText(
                context,
                context.getString(R.string.send_shared_content_invalid),
                Toast.LENGTH_SHORT
            ).show()
    }
}
