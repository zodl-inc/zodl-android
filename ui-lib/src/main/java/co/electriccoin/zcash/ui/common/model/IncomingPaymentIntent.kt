package co.electriccoin.zcash.ui.common.model

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import co.electriccoin.zcash.ui.common.usecase.HandleSharedPaymentUseCase

internal sealed interface IncomingPaymentIntent {
    data object ThirdPartyView : IncomingPaymentIntent

    data class Text(
        val text: String
    ) : IncomingPaymentIntent

    data class Image(
        val uri: Uri
    ) : IncomingPaymentIntent
}

/** Reads only the payload appropriate for the action; VIEW always retains the third-party warning. */
@Suppress("TooGenericExceptionCaught")
internal fun Intent.incomingPayment(): IncomingPaymentIntent? {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null

    // This Activity is exported. Wrongly typed or unparcelable extras must not crash the wallet.
    return try {
        when (action) {
            Intent.ACTION_VIEW -> data?.let { IncomingPaymentIntent.ThirdPartyView }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> sharedPayment()
            else -> null
        }
    } catch (_: RuntimeException) {
        null
    }
}

private fun Intent.sharedPayment(): IncomingPaymentIntent? {
    if (type?.startsWith("image/") == true) {
        val uri =
            when (action) {
                Intent.ACTION_SEND_MULTIPLE -> {
                    IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
                }

                else -> {
                    IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
                }
            }
        // Read granted provider content only, not a path to this app's own private files. A caption
        // must not turn an invalid image share into a different payment.
        return uri
            ?.takeIf { it.scheme == "content" && !it.authority.isNullOrBlank() }
            ?.let { IncomingPaymentIntent.Image(it) }
    }

    // Styled text is a CharSequence, not necessarily a String. Bound it before making a copy.
    return if (action == Intent.ACTION_SEND && type?.startsWith("text/") == true) {
        getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.takeIf { it.length <= HandleSharedPaymentUseCase.MAX_SHARED_TEXT_LENGTH && it.isNotBlank() }
            ?.toString()
            ?.let { IncomingPaymentIntent.Text(it) }
    } else {
        null
    }
}
