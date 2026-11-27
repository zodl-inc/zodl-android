package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.voting.VotingBallotIntent
import co.electriccoin.zcash.ui.common.model.voting.Proposal

/**
 * Builds one [VotingBallotIntent] per proposal in the round's full [proposals] roster -- not one
 * per entry in [choices] -- so a proposal the user left unanswered produces an explicit
 * `choice = null` (Skipped) intent instead of no intent at all. This distinction matters: the
 * round driver treats "no intent recorded" as a blocking [VotingRoundQuiescence.NeedsBallot]
 * (mapped to [co.electriccoin.zcash.ui.common.model.voting.VotingErrors.OmittedCommittedProposal]
 * by [VotingRoundQuiescenceMapper]), even for a legitimate partial ballot -- leaving some
 * questions unanswered is a supported, valid case, and must be represented as an explicit
 * Skipped decision (`zcash_voting::session::Decision::Skipped`, see [VotingBallotIntent]'s own
 * doc comment), not an omission.
 */
internal fun buildBallotIntents(
    proposals: List<Proposal>,
    choices: Map<Int, Int>
): List<VotingBallotIntent> =
    proposals.map { proposal ->
        VotingBallotIntent(proposalId = proposal.id, choice = choices[proposal.id])
    }
