package co.electriccoin.zcash.ui.common.model.voting

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VotingSessionTest {
    @Test
    fun lastMomentBufferUsesFortyPercentOfRoundDuration() {
        val start = Instant.parse("2026-05-05T12:00:00Z")
        val end = start.plusSeconds(600)
        val session = votingSession(ceremonyStart = start, voteEndTime = end)

        assertEquals(240L, session.lastMomentBufferSeconds())
        assertEquals(end.epochSecond - 240L, session.shareSubmissionDeadlineEpochSeconds(singleShare = false))
        assertFalse(session.isLastMoment(now = end.minusSeconds(241)))
        assertTrue(session.isLastMoment(now = end.minusSeconds(240)))
    }

    @Test
    fun lastMomentBufferIsCappedAtSixHours() {
        val start = Instant.parse("2026-05-05T12:00:00Z")
        val end = start.plusSeconds(7 * 24 * 60 * 60)
        val session = votingSession(ceremonyStart = start, voteEndTime = end)

        assertEquals(21_600L, session.lastMomentBufferSeconds())
        assertEquals(end.epochSecond - 21_600L, session.shareSubmissionDeadlineEpochSeconds(singleShare = false))
    }

    @Test
    fun shareSubmissionDeadlineIsNullInSingleShareMode() {
        val start = Instant.parse("2026-05-05T12:00:00Z")
        val end = start.plusSeconds(600)
        val session = votingSession(ceremonyStart = start, voteEndTime = end)

        assertNull(session.shareSubmissionDeadlineEpochSeconds(singleShare = true))
    }

    @Test
    fun invalidRoundTimesDisableLastMomentBuffer() {
        val start = Instant.parse("2026-05-05T12:00:00Z")
        val session = votingSession(ceremonyStart = start, voteEndTime = start)

        assertNull(session.lastMomentBufferSeconds())
        assertNull(session.shareSubmissionDeadlineEpochSeconds(singleShare = false))
        assertFalse(session.isLastMoment(now = start))
    }

    private fun votingSession(
        ceremonyStart: Instant,
        voteEndTime: Instant
    ) = VotingSession(
        voteRoundId = ByteArray(32) { 1 },
        snapshotHeight = 1,
        snapshotBlockhash = ByteArray(32) { 2 },
        proposalsHash = ByteArray(32) { 3 },
        voteEndTime = voteEndTime,
        ceremonyStart = ceremonyStart,
        eaPK = ByteArray(32) { 4 },
        vkZkp1 = ByteArray(32) { 5 },
        vkZkp2 = ByteArray(32) { 6 },
        vkZkp3 = ByteArray(32) { 7 },
        ncRoot = ByteArray(32) { 8 },
        nullifierIMTRoot = ByteArray(32) { 9 },
        creator = "creator",
        title = "Voting Round",
        description = "Voting round description",
        discussionUrl = null,
        proposals =
            listOf(
                Proposal(
                    id = 1,
                    title = "Proposal",
                    description = "Proposal description",
                    options =
                        listOf(
                            VoteOption(id = 0, label = "No"),
                            VoteOption(id = 1, label = "Yes")
                        )
                )
            ),
        status = SessionStatus.ACTIVE,
        createdAtHeight = 1
    )
}
