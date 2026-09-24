package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingRoundQuiescence
import cash.z.ecc.android.sdk.model.voting.VotingRoundRunReport
import cash.z.ecc.android.sdk.model.voting.VotingRoundStepFailure
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSessionHolder
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Coverage for [SubmitVotesUseCase]'s Keystone submission path (`submitKeystoneVotes`, reached
 * via [SubmitVotesUseCase.invoke] when the selected account is a [KeystoneAccountFixture]-shaped
 * account) -- previously entirely untested (nit #13 of Milan's review of PR #6, alongside
 * `SubmitVotesUseCaseSuccessPathTest` for the non-Keystone path). Fixture pattern matches
 * `SubmitVotesUseCaseBundleFailureRetryTest`/`SubmitVotesUseCaseSuccessPathTest`, minus the
 * `VotingRoundSession` mock (the Keystone path drives [VotingKeystoneSessionHolder] directly, not
 * a freshly-opened session).
 */
class SubmitVotesUseCaseKeystonePathTest {
    @Test
    fun `a successful Keystone submission closes the retained session and records recovery side effects`() =
        runTest {
            val env = Env()
            val report = env.runReport(quiescence = VotingRoundQuiescence.NoWorkLeft, completedProposals = 1)
            coEvery {
                env.votingKeystoneSessionHolder.runToCompletion(env.roundId, any(), any())
            } returns report

            val result = env.invoke(choices = mapOf(1 to 0))

            assertEquals(1, result.submittedProposalCount)
            coVerify(exactly = 1) { env.votingKeystoneSessionHolder.close(env.roundId) }
            coVerify(exactly = 1) {
                env.votingRecoveryRepository.setPhase(
                    env.accountUuidString,
                    env.roundId,
                    VotingRecoveryPhase.VOTES_SUBMITTED
                )
            }
            coVerify(exactly = 1) { env.votingShareTrackingScheduler.schedule(env.roundId) }
        }

    @Test
    fun `a failed Keystone submission never closes the retained session, so a retry can resume it`() =
        runTest {
            val env = Env()
            val failureReport =
                env.runReport(
                    quiescence = VotingRoundQuiescence.Failures,
                    completedProposals = 0,
                    failures =
                        listOf(
                            VotingRoundStepFailure(
                                step = null,
                                bundleIndex = 0,
                                kind = "DelegationTargetMismatch",
                                message = "synthetic non-retryable failure"
                            )
                        )
                )
            coEvery {
                env.votingKeystoneSessionHolder.runToCompletion(env.roundId, any(), any())
            } returns failureReport

            assertFailsWith<VotingSubmissionRecoverableException> {
                env.invoke(choices = mapOf(1 to 0))
            }

            // The doc comment on submitKeystoneVotes's close() call is explicit about why: unlike
            // the non-Keystone path's freshly-opened-and-unconditionally-closed session, this one
            // is retained across the Sign/Scan flow and must survive a transient failure so a
            // retry can resume it via ensureDelegationPipeline's no-op path.
            coVerify(exactly = 0) { env.votingKeystoneSessionHolder.close(any()) }
        }

    @Test
    fun `ballot intents are set on the retained session before running to completion`() =
        runTest {
            val env = Env()
            coEvery {
                env.votingKeystoneSessionHolder.runToCompletion(env.roundId, any(), any())
            } returns env.runReport(quiescence = VotingRoundQuiescence.NoWorkLeft, completedProposals = 1)

            env.invoke(choices = mapOf(1 to 0))

            coVerify(exactly = 1) { env.votingKeystoneSessionHolder.setBallotIntents(env.roundId, any()) }
        }

    /** Shared fixture wiring for the Keystone [SubmitVotesUseCase.invoke] path. */
    private class Env {
        val roundIdBytes = ByteArray(32) { 0x0E }
        val roundId = roundIdBytes.toHex()
        val accountUuidString: String

        val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        val prepareVotingRound = mockk<PrepareVotingRoundUseCase>()
        val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
        val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>()
        val votingShareTrackingScheduler = mockk<VotingShareTrackingScheduler>(relaxed = true)
        val votingKeystoneSessionHolder = mockk<VotingKeystoneSessionHolder>(relaxed = true)
        val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        val synchronizerProvider = mockk<SynchronizerProvider>()
        val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
        val getWalletSeedBytes = mockk<GetWalletSeedBytesUseCase>()
        val synchronizer = mockk<Synchronizer>()

        private val useCase =
            SubmitVotesUseCase(
                resolveVotingRoundSession = resolveVotingRoundSession,
                votingCryptoClient = votingCryptoClient,
                votingHotkeySeedProvider = votingHotkeySeedProvider,
                synchronizerProvider = synchronizerProvider,
                getSelectedWalletAccount = getSelectedWalletAccount,
                getWalletSeedBytes = getWalletSeedBytes,
                prepareVotingRound = prepareVotingRound,
                votingShareTrackingScheduler = votingShareTrackingScheduler,
                votingRecoveryRepository = votingRecoveryRepository,
                votingKeystoneSessionHolder = votingKeystoneSessionHolder,
                votingProofPrecomputeRepository = votingProofPrecomputeRepository
            )

        init {
            val voteEndTime = Instant.parse("2026-09-23T12:00:00Z")
            val session = votingSession(voteRoundId = roundIdBytes, voteEndTime = voteEndTime, snapshotHeight = 100L)
            val selectedAccount = keystoneAccount()
            accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            val serviceConfig =
                VotingServiceConfig(
                    voteServers =
                        listOf(VotingServiceConfig.ServiceEndpoint(url = "https://vote.example", label = "v1")),
                    pirEndpoints =
                        listOf(VotingServiceConfig.ServiceEndpoint(url = "https://pir.example", label = "p1")),
                    pirLayout = VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048)
                )
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { synchronizer.getTreeState(any()) } returns ByteArray(32)

            coEvery { resolveVotingRoundSession(roundId) } returns
                VotingRoundSessionContext(session = session, serviceConfig = serviceConfig)
            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/voting-wallet.db"
            coEvery { votingHotkeySeedProvider.get(any()) } returns ByteArray(32)
        }

        suspend fun invoke(choices: Map<Int, Int>) = useCase.invoke(roundId = roundId, choices = choices)

        fun runReport(
            quiescence: VotingRoundQuiescence,
            completedProposals: Int,
            failures: List<VotingRoundStepFailure> = emptyList()
        ): VotingRoundRunReport =
            VotingRoundRunReport(
                quiescence = quiescence,
                plan = null,
                completedProposals = completedProposals,
                totalProposals = 1,
                remainingObligations = 0,
                failures = failures,
                skippedBundles = emptyList(),
                chainOutcomes = emptyList(),
                shareDeliveries = emptyList(),
                delegationsSignedCount = 0
            )

        private fun keystoneAccount(): KeystoneAccount =
            KeystoneAccount(
                sdkAccount = AccountFixture.new(),
                unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
                transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
                orchardBalance = WalletBalanceFixture.newLong(),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparentBalance = Zatoshi(0),
                isSelected = true
            )

        private fun votingSession(
            voteRoundId: ByteArray,
            voteEndTime: Instant,
            snapshotHeight: Long
        ) = VotingSession(
            voteRoundId = voteRoundId,
            snapshotHeight = snapshotHeight,
            snapshotBlockhash = ByteArray(32) { 2 },
            proposalsHash = ByteArray(32) { 3 },
            voteEndTime = voteEndTime,
            ceremonyStart = voteEndTime.minusSeconds(3_600),
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
}
