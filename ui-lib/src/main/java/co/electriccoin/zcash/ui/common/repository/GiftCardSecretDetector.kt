package co.electriccoin.zcash.ui.common.repository

import java.io.ByteArrayOutputStream
import java.text.Normalizer

/**
 * The last line of defence that keeps gift card secrets away from places that send or store an address: swap and pay
 * requests, and contacts. Unlike [GiftCardLinkPrefixes], which routes text that starts with a gift card link to the
 * redeem flow, this check looks anywhere in the text and sees through the ways a link can be disguised on its way in:
 * text around it (a whole pasted message), invisible or format characters (zero-width spaces, a byte order mark, bidi
 * controls, soft hyphens), whitespace inside it, compatibility forms such as full-width letters, another scheme or no
 * scheme at all, and percent-encoding, even repeated.
 *
 * False positives are acceptable: no real swap or Zcash address contains a gift card host or a gift card key's
 * human-readable part, so text that does is refused rather than risk sending a spending secret off the device.
 */
internal object GiftCardSecretDetector {
    /** What any gift card link or key contains, compared ignoring case. */
    private val MARKERS =
        listOf(
            "gift.zodl.com",
            "link.vizor.cash/payment-links",
            "zgift1",
            "zgifttest1",
        )

    /** How many rounds of percent-decoding are undone at most. */
    private const val MAX_DECODE_ROUNDS = 5

    private const val HEX_RADIX = 16

    private const val PERCENT_ESCAPE_LENGTH = 3

    /**
     * Whether [text] contains, or may contain, a gift card link or a gift card key anywhere in it.
     */
    fun mayContainGiftCardSecret(text: String): Boolean =
        decodings(text).any { decoded -> MARKERS.any { decoded.contains(it, ignoreCase = true) } }

    /**
     * [text] in canonical form, then each further round of percent-decoding it, up to [MAX_DECODE_ROUNDS] rounds or
     * until a round changes nothing.
     */
    private fun decodings(text: String): Sequence<String> =
        generateSequence(text.canonical()) { current ->
            current.percentDecoded().canonical().takeIf { it != current }
        }.take(MAX_DECODE_ROUNDS + 1)

    /**
     * The text in compatibility-normalized form (NFKC, which folds e.g. full-width letters into ASCII), without format
     * characters (Unicode category Cf: zero-width spaces and joiners, the byte order mark, bidi controls, soft
     * hyphens, word joiners and invisible operators) and without whitespace.
     */
    private fun String.canonical(): String =
        Normalizer
            .normalize(this, Normalizer.Form.NFKC)
            .filterNot { Character.getType(it) == Character.FORMAT.toInt() || it.isWhitespace() }

    /**
     * One round of percent-decoding as UTF-8. A `%` not followed by two hex digits is kept as it is, and bytes that
     * are not valid UTF-8 decode to the replacement character, so malformed input never fails.
     */
    private fun String.percentDecoded(): String {
        if ('%' !in this) return this
        val bytes = ByteArrayOutputStream(length)
        var index = 0
        while (index < length) {
            val escaped = percentEscapedByteAt(index)
            if (escaped != null) {
                bytes.write(escaped)
                index += PERCENT_ESCAPE_LENGTH
            } else {
                val codePoint = codePointAt(index)
                bytes.write(String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8))
                index += Character.charCount(codePoint)
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    /** The byte that a `%XX` escape starting at [index] stands for, or `null` when there is none there. */
    private fun String.percentEscapedByteAt(index: Int): Int? {
        if (this[index] != '%' || index + PERCENT_ESCAPE_LENGTH > length) return null
        val high = Character.digit(this[index + 1], HEX_RADIX)
        val low = Character.digit(this[index + 2], HEX_RADIX)
        return if (high < 0 || low < 0) null else high * HEX_RADIX + low
    }
}
