package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which voting config selections count as the predefined (bundled) one. */
class VotingChainConfigStateTest {
    @Test
    fun defaultSelectionIsOnDefaultConfig() {
        assertTrue(VotingChainConfigState().isOnDefaultConfig)
    }

    @Test
    fun customChainWithItsOwnSourceIsNotOnDefaultConfig() {
        assertFalse(customChainOn("https://custom.example.com/static-voting-config.json").isOnDefaultConfig)
        assertFalse(customChainOn("not a url").isOnDefaultConfig)
    }

    @Test
    fun customChainTypedAsABundledSourceCountsAsPredefined() {
        assertTrue(customChainOn(StaticVotingConfig.BUNDLED_PINNED_SOURCE).isOnDefaultConfig)
        assertTrue(customChainOn(StaticVotingConfig.BUNDLED_PINNED_SOURCE_MIRROR).isOnDefaultConfig)
    }

    @Test
    fun bundledUrlWithAnotherChecksumIsNotOnDefaultConfig() {
        val bundledUrl = StaticVotingConfig.BUNDLED_PINNED_SOURCE.substringBefore("?")
        assertFalse(customChainOn("$bundledUrl?checksum=sha256:" + "0a".repeat(32)).isOnDefaultConfig)
        assertFalse(customChainOn(bundledUrl).isOnDefaultConfig)
    }

    private fun customChainOn(pinnedSource: String) =
        VotingChainConfigState(
            selected = VotingChainConfigSelection.Custom("chain"),
            customChains = listOf(VotingCustomChainConfig(id = "chain", name = "Chain", pinnedSource = pinnedSource))
        )
}
