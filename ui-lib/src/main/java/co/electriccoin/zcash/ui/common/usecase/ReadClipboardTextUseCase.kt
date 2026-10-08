package co.electriccoin.zcash.ui.common.usecase

import android.content.ClipboardManager
import android.content.Context
import co.electriccoin.zcash.spackle.getSystemService

/**
 * Reads the primary clip as text, for an explicit, user-initiated paste.
 */
class ReadClipboardTextUseCase(
    private val context: Context
) {
    operator fun invoke(): String? =
        context
            .getSystemService<ClipboardManager>()
            .primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
}
