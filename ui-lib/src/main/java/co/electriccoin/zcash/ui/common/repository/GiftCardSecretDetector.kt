package co.electriccoin.zcash.ui.common.repository

import java.io.ByteArrayOutputStream
import java.text.Normalizer

/**
 * The last line of defence that keeps gift card secrets away from places that send or store an address: swap and pay
 * requests, and contacts. Unlike [GiftCardLinkPrefixes], which routes text that starts with a gift card link to the
 * redeem flow, this check looks anywhere in the text and sees through the ways a link can be disguised on its way in:
 * text around it (a whole pasted message), any invisible, control or combining character (zero-width spaces, tag
 * characters, fillers, a byte order mark, bidi controls, soft hyphens), whitespace and punctuation inside it,
 * compatibility forms such as full-width letters, another scheme or no scheme at all, and percent-encoding, even
 * repeated.
 *
 * It reduces the text to its ASCII letters and digits, in lower case, and looks for the host names and key prefixes in
 * that. Text that is still changing after [MAX_DECODE_ROUNDS] rounds of percent-decoding is refused.
 *
 * False positives are acceptable: no real swap or Zcash address contains a gift card host or a gift card key's
 * human-readable part, so text that does is refused rather than risk sending a spending secret off the device.
 */
internal object GiftCardSecretDetector {
    /** What any gift card link or key contains, reduced like the text: lower case letters and digits only. */
    private val MARKERS =
        listOf(
            "giftzodlcom",
            "linkvizorcashpaymentlinks",
            "paymentlinksopen",
            "zgift1",
            "zgifttest1",
            "zgiftregtest1",
        )

    /** How many rounds of percent-decoding are undone at most before the text is refused. */
    private const val MAX_DECODE_ROUNDS = 8

    private const val HEX_RADIX = 16

    private const val PERCENT_ESCAPE_LENGTH = 3

    private const val FIRST_PRINTABLE_ASCII = 0x21

    private const val LAST_PRINTABLE_ASCII = 0x7E

    private const val CASE_OFFSET = 'a' - 'A'

    /**
     * Whether [text] contains, or may contain, a gift card link or a gift card key anywhere in it.
     */
    fun mayContainGiftCardSecret(text: String): Boolean {
        val rounds =
            generateSequence(text.printableAscii()) { previous ->
                previous.percentDecoded().printableAscii().takeIf { it != previous }
            }.take(MAX_DECODE_ROUNDS + 1).toList()
        // Still changing after the last round: more layers of encoding than anything real has.
        val isStillChanging = rounds.size > MAX_DECODE_ROUNDS
        return isStillChanging ||
            rounds.any { round ->
                val reduced = round.lettersAndDigitsInLowerCase()
                MARKERS.any { it in reduced }
            }
    }

    /**
     * The printable ASCII characters of the text in compatibility-decomposed form (NFKD, which folds e.g. full-width
     * letters into ASCII and splits a letter from its combining marks). Everything else goes: whitespace, control,
     * format, tag and filler characters, marks and every other non-ASCII character.
     */
    private fun String.printableAscii(): String =
        Normalizer
            .normalize(this, Normalizer.Form.NFKD)
            .filter { it.code in FIRST_PRINTABLE_ASCII..LAST_PRINTABLE_ASCII }

    private fun String.lettersAndDigitsInLowerCase(): String =
        buildString(length) {
            for (char in this@lettersAndDigitsInLowerCase) {
                when (char) {
                    in 'a'..'z', in '0'..'9' -> append(char)
                    in 'A'..'Z' -> append(char + CASE_OFFSET)
                }
            }
        }

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
