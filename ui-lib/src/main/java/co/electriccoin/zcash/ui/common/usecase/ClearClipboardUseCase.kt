package co.electriccoin.zcash.ui.common.usecase

import android.content.ClipboardManager
import android.content.Context
import co.electriccoin.zcash.spackle.AndroidApiVersion
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.spackle.getSystemService

/**
 * Clears the primary clip, e.g. once a pasted secret has been taken in, so it does not stay on the clipboard. Does
 * nothing below Android 9, which has no API to clear the clipboard.
 */
class ClearClipboardUseCase(
    private val context: Context
) {
    @Suppress("TooGenericExceptionCaught")
    operator fun invoke() {
        if (!AndroidApiVersion.isAtLeastP) return
        try {
            context.getSystemService<ClipboardManager>().clearPrimaryClip()
        } catch (e: RuntimeException) {
            Twig.warn { "Clearing the clipboard failed: ${e::class.simpleName}" }
        }
    }
}
