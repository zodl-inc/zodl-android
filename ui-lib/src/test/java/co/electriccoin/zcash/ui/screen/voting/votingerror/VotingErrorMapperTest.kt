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

    @Test
    fun submissionErrorsUseVotingSpecificMessages() {
        val cases =
            mapOf(
                "No voting server accepted share 2" to R.string.vote_error_mapper_no_reachable_vote_servers,
                "nullifier already spent" to R.string.vote_error_mapper_nullifier_spent,
                "vote round not found" to R.string.vote_error_mapper_round_not_found,
                "No active voting round" to R.string.vote_error_mapper_round_closed,
                "PIR proof root mismatch" to R.string.vote_error_mapper_pir_snapshot_mismatch,
                "PIR proof verification failed" to R.string.vote_error_mapper_pir_invalid_proof,
                "PIR server connect failed" to R.string.vote_error_mapper_pir_unavailable,
                "No PIR endpoints are configured" to R.string.vote_error_mapper_pir_endpoints_missing,
                "Commitment tree did not grow" to R.string.vote_error_mapper_commitment_tree_not_grown,
                "invalid commitment tree anchor height" to R.string.vote_error_mapper_invalid_anchor_height,
                "invalid zero-knowledge proof" to R.string.vote_error_mapper_invalid_proof,
                "delegation bundle build failed" to R.string.vote_error_mapper_proof_generation_failed,
                "NoTreeState" to R.string.vote_error_mapper_no_tree_state,
                "HTTP 503" to R.string.vote_error_mapper_http_5,
                "GRPCStatus unavailable" to R.string.vote_error_mapper_lightwalletd_unavailable,
            )

        cases.forEach { (rawMessage, expectedString) ->
            assertEquals(
                expectedString,
                VotingErrorMapper.toUserFriendlyMessage(rawMessage).resourceId(),
                rawMessage
            )
        }
    }
}

private fun StringResource.resourceId(): Int =
    assertIs<StringResource.ByResource>(this).resource
