package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.NetworkDimension
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.zecdev.zip321.ZIP321
import org.zecdev.zip321.model.PaymentRequest
import org.zecdev.zip321.parser.ParserContext

class Zip321ParseUriValidationUseCase(
    private val synchronizerProvider: SynchronizerProvider
) {
    suspend operator fun invoke(zip321Uri: String) = validateZip321Uri(zip321Uri)

    private suspend fun validateZip321Uri(zip321Uri: String): Zip321ParseUriValidation {
        val synchronizer = synchronizerProvider.getSynchronizer()

        val paymentRequest =
            withContext(Dispatchers.Default) {
                runCatching {
                    ZIP321.request(
                        uriString = zip321Uri,
                        context =
                            when (VersionInfo.NETWORK_DIMENSION) {
                                NetworkDimension.MAINNET -> ParserContext.MAINNET
                                NetworkDimension.TESTNET -> ParserContext.TESTNET
                            },
                        validatingRecipients = { address -> validateRecipientBlocking(address, synchronizer) }
                    )
                }.onFailure {
                    Twig.debug { "Not valid Zip321 URI scanned" }
                }.getOrElse {
                    false
                }
            }

        Twig.info { "Payment Request Zip321 validation result: $paymentRequest." }

        return when (paymentRequest) {
            is ZIP321.ParserResult.Request -> {
                Zip321ParseUriValidation.Valid(zip321Uri, paymentRequest.paymentRequest)
            }

            is ZIP321.ParserResult.SingleAddress -> {
                Zip321ParseUriValidation.SingleAddress(paymentRequest.singleRecipient.value)
            }

            else -> {
                Zip321ParseUriValidation.Invalid
            }
        }
    }

    /**
     * Zip321's `validatingRecipients` callback is synchronous, so this bridges into the suspend
     * [Synchronizer.validateAddress] via a bounded `runBlocking`: the call is a light, non-suspending
     * JNI parse under the hood, and this already executes on [Dispatchers.Default], never Main.
     */
    private fun validateRecipientBlocking(
        address: String,
        synchronizer: Synchronizer
    ): Boolean =
        runBlocking {
            synchronizer.validateAddress(address).let { validation ->
                when (validation) {
                    is AddressType.Invalid -> {
                        Twig.error { "Address from Zip321 validation failed: ${validation.reason}" }
                        false
                    }

                    else -> {
                        validation is AddressType.Valid
                    }
                }
            }
        }

    sealed class Zip321ParseUriValidation {
        data class Valid(
            val zip321Uri: String,
            val payment: PaymentRequest,
        ) : Zip321ParseUriValidation()

        data class SingleAddress(
            val address: String
        ) : Zip321ParseUriValidation()

        data object Invalid : Zip321ParseUriValidation()
    }
}
