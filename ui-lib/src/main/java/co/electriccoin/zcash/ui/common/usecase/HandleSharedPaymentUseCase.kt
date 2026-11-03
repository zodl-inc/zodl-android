package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.net.Uri
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretDetector
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ImageUriToQrCodeConverter
import co.electriccoin.zcash.ui.screen.send.Send
import co.electriccoin.zcash.ui.screen.send.toRouteAddressType
import org.zecdev.zip321.model.Payment

/**
 * Opens Send filled in with a payment shared from another app: a Zcash address or a `zcash:` URI, in text or in a
 * QR code in an image.
 *
 * The share target is exported, so any installed app can deliver a payment here without the user seeing a share
 * sheet. Shared content therefore only prefills the form: unlike a code scanned in the app, it never builds a
 * proposal or opens the review screen. As with a link opened from outside the app, a gift card link is never used;
 * it only opens the gift card scanner, see [HandleExternalLinkUseCase].
 */
class HandleSharedPaymentUseCase(
    private val context: Context,
    private val imageUriToQrCodeConverter: ImageUriToQrCodeConverter,
    private val synchronizerProvider: SynchronizerProvider,
    private val zip321ParseUriValidationUseCase: Zip321ParseUriValidationUseCase,
    private val validateAddress: ValidateAddressUseCase,
    private val rejectIfSelectedAccountCannotSpend: RejectIfSelectedAccountCannotSpendUseCase,
    private val giftCardLinkStore: GiftCardLinkStore,
    private val navigationRouter: NavigationRouter,
    private val showError: ShowErrorUseCase,
) {
    suspend operator fun invoke(text: String) {
        if (!hasWallet()) return
        handleText(text)
    }

    suspend operator fun invoke(image: Uri) {
        if (!hasWallet()) return
        when (val result = imageUriToQrCodeConverter(context, image)) {
            is ImageToQrCodeResult.SingleCode -> handleText(result.text)
            ImageToQrCodeResult.MultipleCodes -> showError(stringRes(R.string.scan_severalCodesFound))
            ImageToQrCodeResult.NoCode -> showError(stringRes(R.string.scan_invalidImage))
        }
    }

    /** Waits for the wallet to load. Without one, says so rather than reporting the shared content as invalid. */
    private suspend fun hasWallet(): Boolean {
        if (synchronizerProvider.getSynchronizerOrNull() != null) return true
        showError(stringRes(R.string.scan_sharedNoWallet))
        return false
    }

    private suspend fun handleText(text: String) {
        if (GiftCardSecretDetector.mayContainGiftCardSecret(text)) {
            giftCardLinkStore.requestInAppScan()
            return
        }
        val send = paymentCandidates(text).firstNotNullOfOrNull { resolve(it) }
        if (send == null) {
            showError(stringRes(R.string.scan_sharedContentInvalid))
        } else if (!rejectIfSelectedAccountCannotSpend()) {
            // Over Home only, whichever screen was open: a share may have launched the app, and Back must not leave it.
            navigationRouter.replaceAll(send)
        }
    }

    /**
     * The substrings of [text] that may be a payment, so that an address inside a sentence, in quotes or brackets, or
     * followed by punctuation is still found. A `zcash:` URI is tried before a bare address, since it also carries
     * the amount and the memo. At most [MAX_PAYMENT_CANDIDATES] are tried: each one costs an SDK lookup.
     */
    private fun paymentCandidates(text: String): Sequence<String> =
        PAYMENT_CANDIDATE
            .findAll(text)
            .take(MAX_PAYMENT_CANDIDATES)
            .flatMap { match ->
                // A period after a URI may end the sentence or a query value, so the match is tried both ways.
                sequenceOf(match.value, match.value.trimEnd('.', ',', '!', '?', ';', ':'))
            }.distinct()
            .sortedByDescending { it.startsWith(ZCASH_URI_SCHEME, ignoreCase = true) }

    /** The Send route for [candidate], or null when it is not a payment to a valid address. */
    private suspend fun resolve(candidate: String): Send? {
        val (address, payment) = recipientOf(candidate) ?: return null
        return when (val addressType = validateAddress(address)) {
            is AddressType.Valid -> {
                Send(
                    recipientAddress = address,
                    recipientAddressType = addressType.toRouteAddressType(),
                    amount = payment?.zatoshi()?.value,
                    memo = payment?.memo?.data?.decodeToString()
                )
            }

            is AddressType.Invalid -> {
                null
            }
        }
    }

    /** The address [candidate] pays and, for a ZIP 321 URI, the rest of the request; null when Send cannot show it. */
    private suspend fun recipientOf(candidate: String): Pair<String, Payment?>? =
        when (val parsed = zip321ParseUriValidationUseCase(candidate)) {
            // Send takes one recipient, so a request for several is refused rather than sent in part.
            is Zip321ParseUriValidation.Valid -> {
                parsed.payment.payments
                    .singleOrNull()
                    ?.let { it.recipientAddress.value to it }
            }

            is Zip321ParseUriValidation.SingleAddress -> {
                parsed.address to null
            }

            // The parser knows the ZIP 321 grammar; what it rejects can still be an address the SDK accepts.
            Zip321ParseUriValidation.Invalid -> {
                candidate to null
            }
        }

    private companion object {
        const val MAX_PAYMENT_CANDIDATES = 64
        const val ZCASH_URI_SCHEME = "zcash:"

        /**
         * A `zcash:` URI up to a character that cannot be in one, or a word that starts like a Zcash address: a
         * unified, Sapling or TEX prefix and `1`, or a transparent `t1`, `t3`, `tm` or `t2`, then at least 20 more
         * characters of the address alphabet. The word is bounded by that alphabet rather than `\b`, so that a
         * Markdown `_` stays out of it, and the prefix keeps hashes and IDs from using up [MAX_PAYMENT_CANDIDATES].
         */
        val PAYMENT_CANDIDATE =
            listOf(
                """zcash:[^\s"'<>()\[\]]+""",
                """(?<![a-z0-9])(?:(?:u|utest|zs|ztestsapling|tex|textest)1|t[123m])[a-z0-9]{20,}"""
            ).joinToString("|").toRegex(RegexOption.IGNORE_CASE)
    }
}

private fun Payment.zatoshi(): Zatoshi? =
    nonNegativeAmount
        ?.toZecValueString()
        ?.toBigDecimal()
        ?.convertZecToZatoshi()
