package co.electriccoin.zcash.ui.common.model

/**
 * The length limit of an account's name, counted in Unicode code points rather than UTF-16
 * chars, so an emoji counts once and capping never cuts a surrogate pair in half.
 */
object AccountNameLimit {
    const val MAX_LENGTH = 12

    /** How many code points [name] has, which is what the name counter shows. */
    fun lengthOf(name: String): Int = name.codePointCount(0, name.length)

    /**
     * [name] cut to its first [MAX_LENGTH] code points. A cut that lands inside a zero-width-joiner
     * sequence, such as a family emoji, drops the joiners left dangling at the end, so the name never
     * ends in an invisible character. A name within the limit is returned as it is, so typing a
     * joiner sequence one code point at a time still works.
     */
    fun cap(name: String): String =
        if (lengthOf(name) <= MAX_LENGTH) {
            name
        } else {
            name.substring(0, name.offsetByCodePoints(0, MAX_LENGTH)).trimEnd(ZERO_WIDTH_JOINER)
        }

    /**
     * [name] as it is saved: trimmed, capped with [cap] and trimmed again, so a cut never leaves
     * trailing whitespace. Every path that writes a name to the wallet uses this one form.
     */
    fun normalize(name: String): String = cap(name.trim()).trim()

    private const val ZERO_WIDTH_JOINER = '\u200D'
}
