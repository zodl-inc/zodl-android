package co.electriccoin.zcash.ui.screen.voting.votingerror

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VotingErrorMapperTest {
    @Test
    fun configVersionErrorsPromptWalletUpdate() {
        val rawMessage = "Wallet does not support vote_protocol version \"2\". Please update the wallet."

        assertEquals(
            R.string.vote_error_config_title,
            VotingErrorMapper.toConfigErrorTitle(rawMessage).resourceId()
        )
        assertEquals(
            R.string.vote_error_mapper_version,
            VotingErrorMapper.toConfigErrorMessage(rawMessage).resourceId()
        )
    }

    @Test
    fun configFetchErrorsUseNetworkMessage() {
        val rawMessage = "Static voting config fetch failed: HTTP 503"

        assertEquals(
            R.string.vote_error_voting_unavailable_title,
            VotingErrorMapper.toConfigErrorTitle(rawMessage).resourceId()
        )
        assertEquals(
            R.string.vote_error_mapper_network,
            VotingErrorMapper.toConfigErrorMessage(rawMessage).resourceId()
        )
    }

    @Test
    fun configAuthenticationErrorsDoNotExposeInternalDetails() {
        val rawMessage = "Voting config signature is invalid for round abcdef..."

        assertEquals(
            R.string.vote_error_voting_unavailable_title,
            VotingErrorMapper.toConfigErrorTitle(rawMessage).resourceId()
        )
        assertEquals(
            R.string.vote_error_voting_unavailable_message,
            VotingErrorMapper.toConfigErrorMessage(rawMessage).resourceId()
        )
    }
}

private fun StringResource.resourceId(): Int =
    assertIs<StringResource.ByResource>(this).resource
