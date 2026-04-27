package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.OptionTally
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.ProposalTally
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Fake [VotingApiProvider] for development / review testing.
 *
 * Returns hardcoded mock data so the full voting UI flow can be tested
 * without a live vote server or Rust backend.
 *
 * TODO: Remove / replace with [KtorVotingApiProvider] before production release.
 */
class FakeVotingApiProvider : VotingApiProvider {
    override suspend fun fetchServiceConfig(): VotingServiceConfig = VotingServiceConfig.FALLBACK

    override suspend fun fetchActiveVotingSession(): VotingSession {
        delay(800) // Simulate network
        return MOCK_SESSION
    }

    override suspend fun fetchAllRounds(): List<VotingRound> = ALL_ROUNDS

    override suspend fun fetchRoundById(roundIdHex: String): VotingRound = MOCK_ROUND

    override suspend fun fetchTallyResults(roundIdHex: String): TallyResults {
        delay(600)
        return TallyResults(
            roundId = roundIdHex,
            proposals =
                listOf(
                    ProposalTally(
                        proposalId = 0,
                        options =
                            listOf(
                                OptionTally(optionId = 0, weight = 7_300_000_000L),
                                OptionTally(optionId = 1, weight = 2_100_000_000L),
                                OptionTally(optionId = 2, weight = 600_000_000L),
                            )
                    ),
                    ProposalTally(
                        proposalId = 1,
                        options =
                            listOf(
                                OptionTally(optionId = 0, weight = 5_400_000_000L),
                                OptionTally(optionId = 1, weight = 4_600_000_000L),
                            )
                    )
                )
        )
    }

    override suspend fun submitDelegation(registration: DelegationRegistration) {
        delay(400)
    }

    override suspend fun submitVoteCommitment(bundle: VoteCommitmentBundle, signature: CastVoteSignature) {
        delay(400)
    }

    override suspend fun delegateShares(shares: List<SharePayload>, roundIdHex: String) {
        delay(800)
    }

    override suspend fun fetchTxConfirmation(txHash: String): Boolean {
        delay(300)
        return true
    }

    companion object {
        private val ROUND_ID = ByteArray(32) { it.toByte() }

        val MOCK_SESSION =
            VotingSession(
                voteRoundId = ROUND_ID,
                snapshotHeight = 2_800_000L,
                proposalsHash = ByteArray(32),
                voteEndTime = Instant.now().plusSeconds(7 * 24 * 3600),
                ceremonyStart = Instant.now().minusSeconds(3600),
                eaPK = ByteArray(32),
                vkZkp1 = ByteArray(32),
                vkZkp2 = ByteArray(32),
                vkZkp3 = ByteArray(32),
                ncRoot = ByteArray(32),
                nullifierIMTRoot = ByteArray(32),
                creator = "Zcash Foundation",
                title = "ZF Grant Funding — Q3 2026",
                description = "Shielded vote on the allocation of Zcash Foundation grant funds for Q3 2026.",
                discussionUrl = "https://forum.zcashcommunity.com/t/zf-grant-q3-2026",
                proposals =
                    listOf(
                        Proposal(
                            id = 0,
                            title = "Allocate 50 ZEC to privacy research",
                            description =
                                "Fund a 3-month research engagement focused on improving " +
                                    "Orchard note encryption and batch proof verification performance.",
                            options =
                                listOf(
                                    VoteOption(0, "Approve"),
                                    VoteOption(1, "Reject"),
                                    VoteOption(2, "Abstain"),
                                ),
                            zipNumber = "1200",
                            forumUrl = "https://forum.zcashcommunity.com/t/proposal-privacy-research"
                        ),
                        Proposal(
                            id = 1,
                            title = "Extend light-client protocol support",
                            description =
                                "Fund development of a reference light-client server with " +
                                    "full Orchard and transparent UTXO indexing.",
                            options =
                                listOf(
                                    VoteOption(0, "Approve"),
                                    VoteOption(1, "Reject"),
                                ),
                            zipNumber = null,
                            forumUrl = "https://forum.zcashcommunity.com/t/proposal-lightclient"
                        )
                    ),
                status = SessionStatus.ACTIVE,
                createdAtHeight = 2_799_000L
            )

        val MOCK_ROUND =
            VotingRound(
                id = ROUND_ID.toHexString(),
                title = MOCK_SESSION.title,
                description = MOCK_SESSION.description,
                discussionUrl = MOCK_SESSION.discussionUrl,
                snapshotHeight = MOCK_SESSION.snapshotHeight,
                snapshotDate = Instant.ofEpochSecond(1_745_000_000),
                votingStart = Instant.now().minusSeconds(3600),
                votingEnd = MOCK_SESSION.voteEndTime,
                proposals = MOCK_SESSION.proposals,
                status = SessionStatus.ACTIVE
            )

        val MOCK_CLOSED_ROUND =
            VotingRound(
                id = "deadbeef00000000000000000000000000000000000000000000000000000001",
                title = "ZF Grant Funding — Q2 2026",
                description = "Completed vote on Q2 2026 Zcash Foundation grant allocation.",
                discussionUrl = null,
                snapshotHeight = 2_700_000L,
                snapshotDate = Instant.ofEpochSecond(1_740_000_000),
                votingStart = Instant.now().minusSeconds(30 * 24 * 3600),
                votingEnd = Instant.now().minusSeconds(7 * 24 * 3600),
                proposals =
                    listOf(
                        Proposal(
                            id = 0,
                            title = "Fund Zebra node development",
                            description = "...",
                            options =
                                listOf(
                                    VoteOption(0, "Approve"),
                                    VoteOption(1, "Reject")
                                )
                        )
                    ),
                status = SessionStatus.COMPLETED
            )

        val ALL_ROUNDS = listOf(MOCK_CLOSED_ROUND, MOCK_ROUND) // newest last → reversed in UI

        private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
    }
}
