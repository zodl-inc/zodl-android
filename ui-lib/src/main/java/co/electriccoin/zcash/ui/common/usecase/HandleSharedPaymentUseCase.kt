package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.net.Uri
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ImageUriToQrCodeConverter
import co.electriccoin.zcash.ui.screen.send.Send
import kotlinx.coroutines.flow.first
import org.zecdev.zip321.model.Payment
import cash.z.ecc.sdk.model.AddressType as RouteAddressType

/**
 * Resolves a payment shared into the app from another app - either as text or as an image containing
 * a QR code - and opens Send with it filled in.
 *
 * The share target's intent filters are exported, so any installed app can deliver a payment here
 * without the user ever seeing a share sheet. Shared content is therefore only ever prefilled: no
 * proposal is built and the user is never placed on a transaction that is one confirmation away from
 * spending. Scanning the same URI with the in-app scanner still goes straight to review, because
 * there the user chose what the camera was pointed at.
 */
class HandleSharedPaymentUseCase(
    private val context: Context,
    private val imageUriToQrCodeConverter: ImageUriToQrCodeConverter,
    private val synchronizerProvider: SynchronizerProvider,
    private val walletRepository: WalletRepository,
    private val zip321ParseUriValidationUseCase: Zip321ParseUriValidationUseCase,
    private val navigationRouter: NavigationRouter,
    private val showError: ShowErrorUseCase
) {
    suspend operator fun invoke(text: String) {
        val synchronizer = synchronizerProvider.getSynchronizerOrNull()

        if (synchronizer == null) {
            Twig.info { "Ignoring shared payment - no wallet available" }
            return
        }

        // Home has to exist underneath before Send can go on top of it: the share may have launched
        // the app straight into this, in which case the graph is still on onboarding, and pushing
        // Send there leaves Back on the "create or restore a wallet" screen. Waiting out LOADING
        // rather than waiting for READY, so that a wallet deleted meanwhile ends the job instead of
        // leaving it parked until the Activity goes away and then resuming onto the next wallet.
        if (walletRepository.secretState.first { it != SecretState.LOADING } != SecretState.READY) {
            Twig.info { "Ignoring shared payment - wallet is no longer available" }
            return
        }

        val handled = paymentCandidates(text).any { handleCandidate(it, synchronizer) }

        if (!handled) {
            showError(stringRes(R.string.scan_sharedContentInvalid))
        }
    }

    suspend operator fun invoke(uri: Uri) {
        when (val result = imageUriToQrCodeConverter(context, uri)) {
            is ImageToQrCodeResult.SingleCode -> invoke(result.text)
            ImageToQrCodeResult.MultipleCodes -> showError(stringRes(R.string.scan_severalCodesFound))
            ImageToQrCodeResult.NoCode -> showError(stringRes(R.string.scan_invalidImage))
        }
    }

    /**
     * Pulls the substrings that could be a payment out of the shared text, so that an address
     * quoted, bracketed or embedded in a sentence is still found. Everything else is dropped before
     * validation: [handleCandidate] costs a ZIP-321 parse plus an address lookup that serializes
     * onto the SDK's single database thread, and shared text can be an arbitrarily long article.
     *
     * Sorted so that a `zcash:` URI is tried first - it also carries the amount and the memo, so it
     * must win over a bare address that happens to appear earlier in the same text.
     */
    private fun paymentCandidates(text: String): Sequence<String> =
        PAYMENT_CANDIDATE
            .findAll(text)
            .flatMap { match ->
                // A ZIP-321 query value can legitimately end in a character that also ends a
                // sentence, so try the match as found and then again trimmed, rather than guessing.
                sequenceOf(match.value, match.value.trimEnd('.', ',', '!', '?', ';', ':'))
            }.distinct()
            .sortedByDescending { it.startsWith(ZCASH_URI_SCHEME, ignoreCase = true) }

    private suspend fun handleCandidate(
        candidate: String,
        synchronizer: Synchronizer
    ): Boolean =
        when (val zip321 = zip321ParseUriValidationUseCase(candidate)) {
            is Zip321ParseUriValidation.Valid -> {
                val payments = zip321.payment.payments

                if (payments.size != 1) {
                    // Send shows one recipient, so anything else would drop the rest of a request
                    // the sender considers whole. Better to reject it than to send part of it.
                    Twig.info { "Rejecting shared payment - ${payments.size} payments, expected 1" }
                    false
                } else {
                    openSend(payments[0].recipientAddress.value, payments[0], synchronizer)
                }
            }

            is Zip321ParseUriValidation.SingleAddress -> {
                openSend(zip321.address, payment = null, synchronizer = synchronizer)
            }

            Zip321ParseUriValidation.Invalid -> {
                openSend(candidate, payment = null, synchronizer = synchronizer)
            }
        }

    /**
     * Opens Send with [address], and with the amount and memo of [payment] when the shared content
     * was a ZIP-321 URI rather than a bare address.
     *
     * The payment travels on the route rather than through [PrefillSendUseCase] because this pops
     * back to Home before pushing Send, which remounts the screen if Send happened to be the one the
     * user was already on. That bus is a rendezvous channel, so a value published across the remount
     * is consumed by the outgoing screen and lost.
     */
    private suspend fun openSend(
        address: String,
        payment: Payment?,
        synchronizer: Synchronizer
    ): Boolean {
        val addressType = synchronizer.validateAddress(address)

        if (addressType !is AddressType.Valid) return false

        navigationRouter.replaceAll(
            Send(
                recipientAddress = address,
                recipientAddressType =
                    when (addressType) {
                        AddressType.Tex -> RouteAddressType.TEX

                        AddressType.Transparent -> RouteAddressType.TRANSPARENT

                        // Shielded and Unified both collapse into UNIFIED, matching what
                        // OnAddressScannedUseCase does, so that the same address reaches Send
                        // identically however it got there. Invalid cannot reach this.
                        else -> RouteAddressType.UNIFIED
                    },
                amount = payment?.zatoshiValue(),
                memo =
                    payment
                        ?.memo
                        ?.data
                        ?.decodeToString()
            )
        )
        return true
    }

    private fun Payment.zatoshiValue(): Long? =
        nonNegativeAmount
            ?.toZecValueString()
            ?.toBigDecimal()
            ?.convertZecToZatoshi()
            ?.value

    companion object {
        private const val ZCASH_URI_SCHEME = "zcash:"

        /**
         * A `zcash:` URI up to the first character that cannot appear in one, or a run of at least
         * 20 alphanumerics. Every Zcash address encoding in use - base58, bech32 and bech32m - is
         * alphanumeric and at least 35 characters long, so 20 is a loose floor that keeps ordinary
         * prose out without risking a real address.
         */
        private val PAYMENT_CANDIDATE =
            Regex("""zcash:[^\s"'<>()\[\]]+|[a-zA-Z0-9]{20,}""", RegexOption.IGNORE_CASE)
    }
}
