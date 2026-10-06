package co.electriccoin.zcash.ui.common.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [GiftCardSecretDetector] finds a gift card link or key anywhere in the text, through every disguise in
 * [GiftCardSecretFixture], and lets ordinary addresses and malformed percent-encoding pass.
 */
class GiftCardSecretDetectorTest {
    @Test
    fun everyDisguiseOfAGiftCardSecretIsDetected() {
        GiftCardSecretFixture.all.forEach { (name, text) ->
            assertTrue(GiftCardSecretDetector.mayContainGiftCardSecret(text), name)
        }
    }

    @Test
    fun theRoutingPrefixCheckMissesTheDisguisesTheDetectorCatches() {
        val missedByPrefix =
            GiftCardSecretFixture.disguisedLinks.filterValues { !GiftCardLinkPrefixes.matches(it) }

        assertTrue(missedByPrefix.isNotEmpty())
        missedByPrefix.forEach { (name, text) ->
            assertTrue(GiftCardSecretDetector.mayContainGiftCardSecret(text), name)
        }
    }

    @Test
    fun ordinaryAddressesPass() {
        GiftCardSecretFixture.ordinaryAddresses.forEach { address ->
            assertFalse(GiftCardSecretDetector.mayContainGiftCardSecret(address), address)
        }
    }

    @Test
    fun malformedPercentEncodingNeverFails() {
        listOf("", "%", "%%", "%4", "%G1", "%E2%80", "%FF%FE", "a%", "%%%25%2", "%25%25%25%25%25%25%25%25")
            .forEach { text -> assertFalse(GiftCardSecretDetector.mayContainGiftCardSecret(text), text) }
    }

    @Test
    fun textWithMoreLayersOfPercentEncodingThanTheCapIsRefused() {
        // A real marker under nine layers of encoding: eight rounds of decoding do not get to it, so it is refused.
        val layered = (1..9).fold("%67ift.zodl.com") { current, _ -> current.replace("%", "%25") }

        assertTrue(GiftCardSecretDetector.mayContainGiftCardSecret(layered), layered)
    }

    @Test
    fun randomLookingTextDoesNotTrigger() {
        val random = java.util.Random(1)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        repeat(20_000) {
            val text = CharArray(64) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
            assertFalse(GiftCardSecretDetector.mayContainGiftCardSecret(text), text)
        }
    }

    @Test
    fun theRoutingPrefixCheckIsUnchanged() {
        assertTrue(GiftCardLinkPrefixes.matches(GiftCardSecretFixture.LINK))
        assertFalse(GiftCardLinkPrefixes.matches("Your gift: ${GiftCardSecretFixture.LINK}"))
        assertFalse(GiftCardLinkPrefixes.matches("http://gift.zodl.com/#v=1"))
    }
}
