package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertIs

class PrepareVotingRoundUseCaseVoteEndTest {
    @Test
    fun `invoke stores the session's vote end time into recovery`() =
        runTest {
            val roundIdBytes = ByteArray(32) { 0x0A }
            val roundId = roundIdBytes.toHex()
            val voteEndTime = Instant.parse("2026-09-21T12:00:00Z")
            val session = votingSession(voteRoundId = roundIdBytes, voteEndTime = voteEndTime, snapshotHeight = 100L)
            val selectedAccount = zashiAccount()
            val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val votingSessionStore = mockk<VotingSessionStore>(relaxed = true)
            val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
            val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>(relaxed = true)
            val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
            val getWalletSeedBytes = mockk<GetWalletSeedBytesUseCase>()

            // A synchronizer that is still behind the round's snapshot height. This deliberately
            // trips the WalletSyncing short-circuit gate a few lines below the new call, so the
            // fixture doesn't need to wire up the (unrelated) native-crypto/hotkey happy path --
            // the new storeVoteEndEpochSeconds call sits before that gate, so it still fires.
            val synchronizer = mockk<Synchronizer>()
            every { synchronizer.fullyScannedHeight } returns MutableStateFlow(BlockHeight.new(1L))
            every { synchronizer.network } returns ZcashNetwork.Testnet

            coEvery { resolveVotingRoundSession(roundId) } returns
                VotingRoundSessionContext(session = session, serviceConfig = VotingServiceConfig.EMPTY)
            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer

            val useCase =
                PrepareVotingRoundUseCase(
                    resolveVotingRoundSession = resolveVotingRoundSession,
                    votingRecoveryRepository = votingRecoveryRepository,
                    votingSessionStore = votingSessionStore,
                    votingCryptoClient = votingCryptoClient,
                    votingHotkeySeedProvider = votingHotkeySeedProvider,
                    votingProofPrecomputeRepository = votingProofPrecomputeRepository,
                    synchronizerProvider = synchronizerProvider,
                    getSelectedWalletAccount = getSelectedWalletAccount,
                    getWalletSeedBytes = getWalletSeedBytes
                )

            val result = useCase(roundId)

            assertIs<VotingRoundPreparationResult.WalletSyncing>(result)
            coVerify(exactly = 1) {
                votingRecoveryRepository.storeVoteEndEpochSeconds(
                    accountUuid,
                    roundId,
                    voteEndTime.epochSecond
                )
            }
        }

    private fun zashiAccount(): ZashiAccount =
        ZashiAccount(
            sdkAccount = AccountFixture.new(),
            unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
            transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
            saplingAddress = WalletAddressFixture.SAPLING_ADDRESS_STRING,
            orchardBalance = WalletBalanceFixture.newLong(),
            saplingBalance = WalletBalanceFixture.newLong(0, 0, 0),
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
