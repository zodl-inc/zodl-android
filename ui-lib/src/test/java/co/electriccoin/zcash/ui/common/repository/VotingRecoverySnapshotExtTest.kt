package co.electriccoin.zcash.ui.common.repository

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VotingRecoverySnapshotExtTest {
    @Test
    fun submittedProposalIsRemovedFromOutstandingDrafts() {
        val selection = VotingProposalSelection(choiceId = 10, numOptions = 3)
        val updatedAt = Instant.parse("2026-05-05T12:00:00Z")
        val snapshot =
            VotingRecoverySnapshot(
                accountUuid = "account",
                roundId = "round",
                draftChoices =
                    mapOf(
                        1 to 10,
                        2 to 20
                    ),
                proposalSelections =
                    mapOf(
                        1 to selection,
                        2 to VotingProposalSelection(choiceId = 20, numOptions = 3)
                    ),
                submittedProposalIds = setOf(3)
            )

        val submitted =
            snapshot.withProposalSubmitted(
                proposalId = 1,
                updatedAt = updatedAt
            )

        assertEquals(mapOf(2 to 20), submitted.draftChoices)
        assertEquals(setOf(1, 3), submitted.submittedProposalIds)
        assertEquals(selection, submitted.proposalSelections[1])
        assertEquals(updatedAt, submitted.updatedAt)
    }

    @Test
    fun skippedKeystoneBundlesReduceBundleCountAndEligibleWeight() {
        val updatedAt = Instant.parse("2026-05-05T12:00:00Z")
        val snapshot =
            VotingRecoverySnapshot(
                accountUuid = "account",
                roundId = "round",
                bundleCount = 3,
                eligibleWeight = 600,
                bundleWeights = listOf(300L, 200L, 100L),
                keystoneBundleSignatures =
                    mapOf(
                        0 to signature(),
                        1 to signature(),
                        2 to signature()
                    ),
                pendingKeystoneRequest =
                    VotingPendingKeystoneRequest(
                        bundleIndex = 2,
                        actionIndex = 0,
                        redactedPcztBase64 = "redacted",
                        expectedSighashBase64 = "sighash"
                    )
            )

        val skipped =
            snapshot.withRemainingKeystoneBundlesSkipped(
                keepCount = 2,
                updatedAt = updatedAt
            )

        assertEquals(2, skipped.bundleCount)
        assertEquals(500, skipped.eligibleWeight)
        assertEquals(listOf(300L, 200L), skipped.bundleWeights)
        assertEquals(1, skipped.skippedBundleCount)
        assertEquals(null, skipped.pendingKeystoneRequest)
        assertEquals(
            snapshot.keystoneBundleSignatures.filterKeys { bundleIndex -> bundleIndex < 2 },
            skipped.keystoneBundleSignatures
        )
        assertEquals(updatedAt, skipped.updatedAt)
    }

    @Test
    fun skippedKeystoneBundlesRequireSignedPrefix() {
        val snapshot =
            VotingRecoverySnapshot(
                accountUuid = "account",
                roundId = "round",
                bundleCount = 3,
                eligibleWeight = 600,
                bundleWeights = listOf(300L, 200L, 100L),
                keystoneBundleSignatures = mapOf(1 to signature())
            )

        assertFailsWith<IllegalArgumentException> {
            snapshot.withRemainingKeystoneBundlesSkipped(keepCount = 1)
        }
    }

    @Test
    fun skippedKeystoneBundlesRequireBundleWeights() {
        val snapshot =
            VotingRecoverySnapshot(
                accountUuid = "account",
                roundId = "round",
                bundleCount = 2,
                eligibleWeight = 500,
                keystoneBundleSignatures = mapOf(0 to signature())
            )

        assertFailsWith<IllegalArgumentException> {
            snapshot.withRemainingKeystoneBundlesSkipped(keepCount = 1)
        }
    }

    private fun signature() =
        VotingKeystoneBundleSignature(
            spendAuthSigBase64 = "signature",
            sighashBase64 = "sighash"
        )
}
