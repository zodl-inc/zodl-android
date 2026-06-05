package co.electriccoin.zcash.ui.screen.voting

import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoteTrustIndicatorTest {
    @Test
    fun defaultConfigShowsZodlIndicatorOnlyForEndorsedRounds() {
        assertEquals(
            VoteTrustIndicator.ZODL,
            voteTrustIndicatorFor(
                roundId = ROUND_ID,
                endorsedRoundIds = setOf(ROUND_ID),
                isOnDefaultConfig = true
            )
        )

        assertNull(
            voteTrustIndicatorFor(
                roundId = ROUND_ID,
                endorsedRoundIds = emptySet(),
                isOnDefaultConfig = true
            )
        )
    }

    @Test
    fun customConfigAlwaysShowsUnverifiedIndicator() {
        assertEquals(
            VoteTrustIndicator.UNVERIFIED,
            voteTrustIndicatorFor(
                roundId = ROUND_ID,
                endorsedRoundIds = setOf(ROUND_ID),
                isOnDefaultConfig = false
            )
        )
    }

    @Test
    fun defaultConfigRequiresDefaultSelectionAndNoOverride() {
        assertTrue(isDefaultVotingConfig(VotingChainConfigState(), configuration = null))
        assertFalse(
            isDefaultVotingConfig(
                chainConfig =
                    VotingChainConfigState(
                        selected = VotingChainConfigSelection.Custom(CUSTOM_CHAIN_ID),
                        customChains =
                            listOf(
                                VotingCustomChainConfig(
                                    id = CUSTOM_CHAIN_ID,
                                    name = "Custom source",
                                    pinnedSource = "https://example.com/static-voting-config.json"
                                )
                            )
                    ),
                configuration = null
            )
        )
    }

    private companion object {
        const val ROUND_ID = "58d9319ac86933b81769a7c0972444fa39212ad3790646398de6ce6534de2225"
        const val CUSTOM_CHAIN_ID = "custom"
    }
}
