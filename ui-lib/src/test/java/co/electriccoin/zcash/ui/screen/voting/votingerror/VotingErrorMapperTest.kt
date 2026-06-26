package co.electriccoin.zcash.ui.screen.voting.votingerror

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.StringResource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VotingErrorMapperTest {
    @Test
    fun configVersionErrorsPromptWalletUpdate() {
        val rawMessage = "Wallet does not support vote_protocol version \"2\". Please update the wallet."

        assertEquals(
            R.string.coinVote_configError_title,
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
            R.string.coinVote_error_configUnavailableTitle,
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
            R.string.coinVote_error_configUnavailableTitle,
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
                "No voting server accepted share 2" to R.string.coinVote_store_userError_noReachableVoteServers,
                "nullifier already spent" to R.string.coinVote_store_userError_nullifierAlreadySpent,
                "vote round not found" to R.string.coinVote_store_userError_roundNotFound,
                "No active voting round" to R.string.coinVote_store_userError_roundNotActive,
                "PIR proof root mismatch" to R.string.coinVote_store_userError_pirSnapshotMismatch,
                "PIR proof verification failed" to R.string.coinVote_store_userError_pirInvalidProofData,
                "PIR server connect failed" to R.string.coinVote_store_userError_pirUnavailable,
                "No PIR endpoints are configured" to R.string.coinVote_store_userError_pirEndpointsMissing,
                "Commitment tree did not grow" to R.string.coinVote_store_userError_commitmentTreeNotGrown,
                "invalid commitment tree anchor height" to R.string.coinVote_store_userError_invalidAnchorHeight,
                "invalid zero-knowledge proof" to R.string.coinVote_store_userError_invalidProof,
                "delegation bundle build failed" to R.string.coinVote_store_userError_proofGenerationFailed,
                "NoTreeState" to R.string.coinVote_store_userError_noTreeState,
                "HTTP 503" to R.string.coinVote_store_userError_http5,
                "GRPCStatus unavailable" to R.string.coinVote_store_userError_lightwalletdUnavailable,
            )

        cases.forEach { (rawMessage, expectedString) ->
            assertEquals(
                expectedString,
                VotingErrorMapper.toUserFriendlyMessage(rawMessage).resourceId(),
                rawMessage
            )
        }
    }

    @Test
    fun insufficientSnapshotBalanceFormatsWeightAndDivisor() {
        val rawMessage =
            "Failed to build delegation bundle: total_weight must yield at least 1 ballot " +
                "(weight = 8000000, divisor = 12500000)"
        // 0.080 ZEC weight, 0.125 ZEC divisor.
        val mapped =
            VotingErrorMapper.toUserFriendlyMessage(
                rawMessage = rawMessage,
                eligibleWeightZatoshi = 8_000_000L,
                ballotDivisorZatoshi = 12_500_000L
            )
        val resource = assertIs<StringResource.ByResource>(mapped)
        assertEquals(R.string.coinVote_delegation_insufficientSnapshotBalance, resource.resource)
        assertContentEquals(listOf("0.080", "0.125"), resource.args)
    }

    @Test
    fun insufficientSnapshotBalanceFallsBackWhenWeightUnknown() {
        val rawMessage = "total_weight must yield at least 1 ballot"
        // Without weight or divisor we can't parameterize; the generic catch-all
        // (in this case the raw-text fallback) should handle it.
        assertEquals(
            VotingErrorMapper.toUserFriendlyMessage(rawMessage),
            VotingErrorMapper.toUserFriendlyMessage(
                rawMessage = rawMessage,
                eligibleWeightZatoshi = null,
                ballotDivisorZatoshi = 12_500_000L
            )
        )
    }
}

private fun StringResource.resourceId(): Int =
    assertIs<StringResource.ByResource>(this).resource
