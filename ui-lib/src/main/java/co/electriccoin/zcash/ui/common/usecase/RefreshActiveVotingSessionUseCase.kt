package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ext.toHex
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingConfigSource

class RefreshActiveVotingSessionUseCase(
    private val votingApiProvider: VotingApiProvider,
    private val votingConfigRepository: VotingConfigRepository,
    private val votingApiRepository: VotingApiRepository,
) {
    suspend operator fun invoke() {
        val serviceConfig = votingApiProvider.fetchServiceConfig()
        val session = runCatching {
            votingApiProvider.fetchActiveVotingSession()
        }.getOrElse { throwable ->
            if (throwable is VotingConfigException) {
                // Config errors are fatal regardless of user pin: the pinned snapshot's
                // serviceConfig may itself be stale. Match iOS behaviour of surfacing
                // the failure to the polls-list configIssue path.
                votingConfigRepository.clear()
            }
            throw throwable
        }
        // iOS treats the user's tap as the active-round source of truth
        // (`VotingStore+Session.swift:69-77`, `:588-640`). On Android, while a user pin
        // is held by `VoteCoinholderPollingVM.openRound`, suppress writes from
        // `/rounds/active` that do not match the pinned id so the auto-refresh poll
        // cannot wipe the user's selection. The pinned snapshot is in-memory only;
        // the persisted snapshot still reflects whatever `/rounds/active` returned
        // last, which is what cold-launch flows like HomeVM's recovery want to see.
        val pinnedRoundId = votingConfigRepository.userSelectedRoundId.value
        if (session == null) {
            if (pinnedRoundId != null) {
                return
            }
            votingConfigRepository.clear()
            return
        }

        val sessionRoundId = session.voteRoundId.toHex()
        // Cheap fast-path: skip the post-network store entirely when the pin we read
        // here already disagrees. The repository's `storeUnlessPinnedToOther` re-checks
        // the pin under its mutex, so the authoritative decision still happens there —
        // this branch just lets us skip allocating the snapshot when we'd suppress.
        if (pinnedRoundId != null && !pinnedRoundId.equals(sessionRoundId, ignoreCase = true)) {
            // Still upsert the round so list rendering picks up status transitions,
            // but do not touch currentConfig — the user is on a different round.
            votingApiRepository.upsertRound(session.toVotingRound())
            return
        }

        // Atomic: re-reads the pin under the repository's mutex and only writes
        // `currentConfig` if no concurrent `setUserSelected` has clobbered it
        // between this use case's pin-check and the post-network write. Without
        // this, the encrypted-prefs `putString` suspension inside `store` could
        // resume after a user tap and overwrite the pinned snapshot.
        votingConfigRepository.storeUnlessPinnedToOther(
            snapshot = VotingConfigSnapshot(
                session = session,
                serviceConfig = serviceConfig,
                source = VotingConfigSource.REMOTE
            ),
            fetchedRoundId = sessionRoundId
        )
        votingApiRepository.upsertRound(session.toVotingRound())
    }
}

private fun VotingSession.toVotingRound() =
    co.electriccoin.zcash.ui.common.model.voting.VotingRound(
        id = voteRoundId.joinToString(separator = "") { byte -> "%02x".format(byte) },
        title = title,
        description = description,
        discussionUrl = discussionUrl,
        createdAtHeight = createdAtHeight,
        snapshotHeight = snapshotHeight,
        snapshotDate = ceremonyStart.takeIf { it.epochSecond > 0 } ?: voteEndTime,
        votingStart = ceremonyStart,
        votingEnd = voteEndTime,
        proposals = proposals,
        status = status
    )
