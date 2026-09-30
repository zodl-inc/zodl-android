package co.electriccoin.zcash.ui.screen.signledgertransaction

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sign sheet's copy as the resource files hold it: every `ledger_sign_` key in both languages,
 * the English status and body lines as Figma words them, and none of the keys the sheet dropped.
 * The files are read from the module directory, which is the unit tests' working directory.
 */
class LedgerSignStringsTest {
    private val english = strings("values")

    private val spanish = strings("values-es")

    @Test
    fun everySignSheetKeyExistsInEnglishAndSpanish() {
        val englishKeys = english.keys.filter { it.startsWith(SIGN_PREFIX) }.toSet()
        val spanishKeys = spanish.keys.filter { it.startsWith(SIGN_PREFIX) }.toSet()

        assertTrue(englishKeys.isNotEmpty())
        assertEquals(emptySet(), englishKeys - spanishKeys, "missing in values-es")
        assertEquals(emptySet(), spanishKeys - englishKeys, "missing in values")
        spanishKeys.forEach { key -> assertTrue(spanish.getValue(key).isNotBlank(), key) }
    }

    @Test
    fun theEnglishStatusAndBodyLinesAreFigmasCopy() {
        mapOf(
            "ledger_sign_title" to "Confirm Transaction",
            "ledger_sign_body_beforeReview" to "Keep your Ledger unlocked and nearby.",
            "ledger_sign_body_review" to "Confirm the transaction on your Ledger device.",
            "ledger_sign_scanning" to "Looking for your Ledger",
            "ledger_sign_select_title" to "Choose your Ledger",
            "ledger_sign_connecting" to "Connecting to your Ledger",
            "ledger_sign_openingApp" to "Open the Zcash app on your Ledger",
            "ledger_sign_preparing" to "Preparing your transaction",
            "ledger_sign_streaming" to "Sending the details to your Ledger",
            "ledger_sign_awaitingReview" to "Check and approve on your Ledger",
            "ledger_sign_signing" to "Signing your transaction",
            "ledger_sign_cancel" to "Cancel Transaction",
        ).forEach { (key, value) -> assertEquals(value, english[key], key) }
    }

    @Test
    fun statusLinesEndWithoutPunctuationInEitherLanguage() {
        STATUS_KEYS.forEach { key ->
            listOf(english, spanish).forEach { language ->
                val value = language.getValue(key)
                assertFalse(value.endsWith(".") || value.endsWith("…"), "$key: $value")
            }
        }
    }

    @Test
    fun theDroppedPickerKeysAreGone() {
        listOf("ledger_sign_select_message", "ledger_sign_select_cta").forEach { key ->
            assertFalse(key in english, key)
            assertFalse(key in spanish, key)
        }
    }

    private fun strings(folder: String): Map<String, String> {
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue(file.isFile, "${file.absolutePath} is missing")
        return STRING
            .findAll(file.readText())
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    private companion object {
        const val SIGN_PREFIX = "ledger_sign_"

        val STRING = Regex("""<string name="([^"]+)">([^<]*)</string>""")

        val STATUS_KEYS =
            listOf(
                "ledger_sign_scanning",
                "ledger_sign_select_title",
                "ledger_sign_connecting",
                "ledger_sign_openingApp",
                "ledger_sign_preparing",
                "ledger_sign_streaming",
                "ledger_sign_awaitingReview",
                "ledger_sign_signing",
            )
    }
}
