package co.electriccoin.zcash.ui.common.usecase

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import co.electriccoin.zcash.spackle.AndroidApiVersion
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.spackle.getSystemService

/**
 * Clears the primary clip, e.g. once a pasted secret has been taken in, so it does not stay on the clipboard. Below
 * Android 9, which has no API to clear it, the clip is replaced by an empty one.
 */
class ClearClipboardUseCase(
    private val context: Context
) {
    @Suppress("TooGenericExceptionCaught")
    operator fun invoke() {
        try {
            val clipboard = context.getSystemService<ClipboardManager>()
            if (AndroidApiVersion.isAtLeastP) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (e: RuntimeException) {
            Twig.warn { "Clearing the clipboard failed: ${e::class.simpleName}" }
        }
    }
}
