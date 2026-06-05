package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.Proposal
import java.time.Instant

fun VotingRecoverySnapshot.effectiveChoices(
    proposals: List<Proposal>,
    inMemoryDraftChoices: Map<Int, Int> = emptyMap(),
): Map<Int, Int> =
    buildMap {
        proposals.forEach { proposal ->
            val choiceId =
                inMemoryDraftChoices[proposal.id]
                    ?: draftChoices[proposal.id]
                    ?: proposalSelections[proposal.id]?.choiceId

            if (choiceId != null) {
                put(proposal.id, choiceId)
            }
        }
    }

internal fun VotingRecoverySnapshot.withProposalSubmitted(
    proposalId: Int,
    updatedAt: Instant = Instant.now()
): VotingRecoverySnapshot =
    copy(
        draftChoices = draftChoices - proposalId,
        submittedProposalIds = submittedProposalIds + proposalId,
        updatedAt = updatedAt
    )

internal fun VotingRecoverySnapshot.withRemainingKeystoneBundlesSkipped(
    keepCount: Int,
    updatedAt: Instant = Instant.now()
): VotingRecoverySnapshot {
    val currentBundleCount =
        requireNotNull(bundleCount) {
            "Voting round $roundId has no prepared bundle count"
        }
    require(keepCount > 0) {
        "At least one Keystone voting bundle must be signed before skipping"
    }
    require(keepCount < currentBundleCount) {
        "Keystone skip requires unsigned remaining bundles"
    }
    require((0 until keepCount).all { bundleIndex -> bundleIndex in keystoneBundleSignatures }) {
        "Keystone skip requires a signed bundle prefix"
    }
    require(bundleWeights.size >= currentBundleCount) {
        "Voting round $roundId is missing per-bundle voting weights"
    }

    val keptBundleWeights = bundleWeights.take(keepCount)
    return copy(
        bundleCount = keepCount,
        eligibleWeight = keptBundleWeights.sum(),
        bundleWeights = keptBundleWeights,
        keystoneBundleSignatures =
            keystoneBundleSignatures.filterKeys { bundleIndex ->
                bundleIndex < keepCount
            },
        skippedBundleCount = skippedBundleCount + currentBundleCount - keepCount,
        pendingKeystoneRequest = null,
        updatedAt = updatedAt
    )
}
