package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.ShareConfirmationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingShareDelegationRecord
import co.electriccoin.zcash.ui.common.model.voting.toEncryptedSharesJson
import co.electriccoin.zcash.ui.common.model.voting.toSharePayloads
import co.electriccoin.zcash.ui.common.model.voting.withSubmitAt
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

sealed interface VotingShareTrackingResult {
    data object Completed : VotingShareTrackingResult

    data class Pending(
        val delayMillis: Long
    ) : VotingShareTrackingResult
}

class TrackVotingSharesUseCase(
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingApiProvider: VotingApiProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) {
    suspend operator fun invoke(roundId: String): VotingShareTrackingResult =
        withContext(Dispatchers.IO) {
            val selectedAccount = getSelectedWalletAccount()
            val accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
            val recovery =
                votingRecoveryRepository.get(accountUuidString, roundId)
                    ?: return@withContext VotingShareTrackingResult.Completed
            val roundVoteServerUrls =
                recovery.voteServerUrls
                    .ifEmpty {
                        runCatching {
                            votingApiProvider
                                .fetchServiceConfig()
                                .voteServers
                                .map { endpoint -> endpoint.url.trimEnd('/') }
                                .distinct()
                        }.getOrDefault(emptyList())
                    }
            if (roundVoteServerUrls.isEmpty()) {
                return@withContext VotingShareTrackingResult.Pending(DEFAULT_DELAY_MILLIS)
            }

            val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
            val votingDbPath =
                File(walletDbPath)
                    .parentFile
                    ?.resolve("voting.sqlite3")
                    ?.absolutePath
                    ?: error("Unable to derive voting DB path from $walletDbPath")

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            try {
                votingCryptoClient.setWalletId(dbHandle, selectedAccount.sdkAccount.accountUuid.toString())
                val shareDelegations = votingCryptoClient.getShareDelegations(dbHandle, roundId)
                if (shareDelegations.isEmpty()) {
                    return@withContext VotingShareTrackingResult.Completed
                }

                val nowEpochSeconds = System.currentTimeMillis() / 1_000
                var nextDelayMillis = DEFAULT_DELAY_MILLIS

                shareDelegations
                    .filterNot { delegation -> delegation.confirmed }
                    .forEach { delegation ->
                        val firstCheckAt =
                            delegation.submitAt
                                .takeIf { submitAt -> submitAt > 0L }
                                ?.plus(CHECK_GRACE_SECONDS)
                                ?: 0L
                        if (firstCheckAt > nowEpochSeconds) {
                            nextDelayMillis =
                                min(
                                    nextDelayMillis,
                                    ((firstCheckAt - nowEpochSeconds) * 1_000L).coerceAtLeast(MIN_DELAY_MILLIS)
                                )
                            return@forEach
                        }

                        val statusProbeUrls =
                            delegation.sentToUrls
                                .map { url -> url.trimEnd('/') }
                                .filter(String::isNotEmpty)
                                .ifEmpty { roundVoteServerUrls }
                                .distinct()
                        val isConfirmed =
                            statusProbeUrls.any { helperBaseUrl ->
                                runCatching {
                                    votingApiProvider.fetchShareStatus(
                                        helperBaseUrl = helperBaseUrl,
                                        roundIdHex = roundId,
                                        nullifierHex = delegation.nullifier.toLowerHex()
                                    )
                                }.getOrNull() == ShareConfirmationResult.CONFIRMED
                            }

                        if (isConfirmed) {
                            votingCryptoClient.markShareConfirmed(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = delegation.bundleIndex,
                                proposalId = delegation.proposalId,
                                shareIndex = delegation.shareIndex
                            )
                            return@forEach
                        }

                        val resubmitAt = delegation.resubmitAt(recovery.voteEndEpochSeconds)
                        if (resubmitAt == null) {
                            return@forEach
                        }
                        if (resubmitAt > nowEpochSeconds) {
                            nextDelayMillis =
                                min(
                                    nextDelayMillis,
                                    ((resubmitAt - nowEpochSeconds) * 1_000L).coerceAtLeast(MIN_DELAY_MILLIS)
                                )
                            return@forEach
                        }
                        if (recovery.voteEndEpochSeconds != null &&
                            recovery.voteEndEpochSeconds <= nowEpochSeconds + RESUBMIT_CUTOFF_SECONDS
                        ) {
                            return@forEach
                        }

                        val proposalSelection =
                            recovery.proposalSelections[delegation.proposalId]
                                ?: return@forEach
                        val commitmentRecord =
                            votingCryptoClient.getCommitmentBundle(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = delegation.bundleIndex,
                                proposalId = delegation.proposalId
                            ) ?: return@forEach
                        if (commitmentRecord.vcTreePosition <= 0L) {
                            return@forEach
                        }

                        val payload =
                            votingCryptoClient
                                .buildSharePayloadsJson(
                                    encSharesJson = commitmentRecord.bundle.encShares.toEncryptedSharesJson(),
                                    commitmentJson = commitmentRecord.bundleJson,
                                    voteDecision = proposalSelection.choiceId,
                                    numOptions = proposalSelection.numOptions,
                                    vcTreePosition = commitmentRecord.vcTreePosition,
                                    singleShareMode = recovery.singleShareMode == true
                                ).toSharePayloads()
                                .map { generated -> generated.withSubmitAt(0) }
                                .firstOrNull { generated ->
                                    generated.encShare.shareIndex == delegation.shareIndex
                                } ?: return@forEach

                        val acceptedServers =
                            runCatching {
                                votingApiProvider.resubmitShare(
                                    payload = payload,
                                    roundIdHex = roundId,
                                    candidateUrls = roundVoteServerUrls,
                                    excludeUrls = delegation.sentToUrls
                                )
                            }.getOrDefault(emptyList())

                        if (acceptedServers.isNotEmpty()) {
                            votingCryptoClient.addSentServers(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = delegation.bundleIndex,
                                proposalId = delegation.proposalId,
                                shareIndex = delegation.shareIndex,
                                newUrls = acceptedServers
                            )
                            nextDelayMillis = min(nextDelayMillis, POST_RESUBMIT_DELAY_MILLIS)
                        }
                    }

                val hasPendingShares =
                    votingCryptoClient
                        .getShareDelegations(dbHandle, roundId)
                        .any { delegation -> !delegation.confirmed }
                if (hasPendingShares) {
                    VotingShareTrackingResult.Pending(nextDelayMillis.coerceAtLeast(MIN_DELAY_MILLIS))
                } else {
                    VotingShareTrackingResult.Completed
                }
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }
        }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val CHECK_GRACE_SECONDS = 10L
        const val RESUBMIT_CUTOFF_SECONDS = 10L
        const val MIN_DELAY_MILLIS = 3_000L
        const val DEFAULT_DELAY_MILLIS = 15_000L
        const val POST_RESUBMIT_DELAY_MILLIS = 10_000L
    }
}

private fun VotingShareDelegationRecord.resubmitAt(voteEndEpochSeconds: Long?): Long? {
    if (submitAt <= 0L || voteEndEpochSeconds == null) {
        return null
    }

    val remainingAtSubmit = (voteEndEpochSeconds - submitAt).coerceAtLeast(0L)
    val overdueThreshold =
        max(
            RESUBMIT_MIN_DELAY_SECONDS,
            min(
                RESUBMIT_MAX_DELAY_SECONDS,
                remainingAtSubmit / 4
            )
        )
    return submitAt + overdueThreshold
}

private const val RESUBMIT_MIN_DELAY_SECONDS = 30L
private const val RESUBMIT_MAX_DELAY_SECONDS = 3_600L
