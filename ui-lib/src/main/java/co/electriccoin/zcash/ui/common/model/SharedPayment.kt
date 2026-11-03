package co.electriccoin.zcash.ui.common.model

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * What another app shared into the wallet: text that may hold a payment, or an image that may hold its QR code.
 */
internal sealed interface SharedPayment {
    data class Text(
        val text: String
    ) : SharedPayment

    data class Image(
        val uri: Uri
    ) : SharedPayment
}

/**
 * The content of a SEND or SEND_MULTIPLE intent, or null for any other intent, including one redelivered from
 * recents. The share target is exported, so an extra that cannot be read is treated as no share rather than crashing.
 */
@Suppress("TooGenericExceptionCaught")
internal fun Intent.sharedPayment(): SharedPayment? {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    val isShare = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
    return try {
        when {
            !isShare -> {
                null
            }

            type?.startsWith("image/") == true -> {
                sharedImage()?.let { SharedPayment.Image(it) }
            }

            action == Intent.ACTION_SEND && type?.startsWith("text/") == true -> {
                sharedText()?.let { SharedPayment.Text(it) }
            }

            else -> {
                null
            }
        }
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * Only a content provider's URI is opened: a `file:` URI would be read with this app's own file permissions. The app's
 * own provider is not refused; one of its own images can only fill in the Send form.
 */
private fun Intent.sharedImage(): Uri? {
    val uri =
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
        } else {
            IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
        }
    return uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
}

/** Styled text is a [CharSequence], not necessarily a [String]. */
private fun Intent.sharedText(): String? =
    getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
