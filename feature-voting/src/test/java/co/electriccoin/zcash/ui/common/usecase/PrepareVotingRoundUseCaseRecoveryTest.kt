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
import co.electriccoin.zcash.ui.common.model.voting.RoundPhase
import co.electriccoin.zcash.ui.common.model.voting.RoundStateInfo
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteIneligibilityReason
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingBundleSetupResult
import co.electriccoin.zcash.ui.common.model.voting.VotingHotkey
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Regression coverage for PrepareVotingRoundUseCase's new-round vs. resume branching --
 * genuinely current behavior against [VotingCryptoClient.getRoundState]/[VotingCryptoClient
 * .getBundleCount]/[VotingCryptoClient.computeBundleSetup], not ported from the deleted
 * pre-5.0.0 PrepareVotingRoundUseCaseTest.kt: that file's "prefers persisted recovery over a
 * native bundle-count read" scenarios have no equivalent here -- the current implementation
 * always calls [VotingCryptoClient.getBundleCount] on the resume path (see
 * `recoverExistingBundleSetup`), there is no persisted-recovery-first optimization to test.
 * What IS still real and untested here: the new-round setup path, the resume path's
 * fail-closed guard when the native DB has more bundles than the current snapshot's note set
 * can account for, and that a genuine coroutine cancellation from a native call propagates
 * rather than being swallowed by this use case's own try/finally.
 */
class PrepareVotingRoundUseCaseRecoveryTest {
    @Test
    fun `a round with no existing native state is set up from scratch and reaches Ready`() =
        runTest {
            val env = Environment()
            env.stubHappyPathUpToDbOpen()
            coEvery { env.votingCryptoClient.getRoundState(DB_HANDLE, ROUND_ID) } returns null
            coEvery {
                env.votingCryptoClient.getWalletNotesJson(any(), any(), any(), any())
            } returns NON_EMPTY_NOTES_JSON
            coEvery { env.votingCryptoClient.setupBundles(DB_HANDLE, ROUND_ID, NON_EMPTY_NOTES_JSON) } returns
                VotingBundleSetupResult(
                    bundleCount = 3,
                    eligibleWeight = 900L,
                    bundleWeights = listOf(300L, 300L, 300L)
                )
            coEvery { env.votingCryptoClient.generateHotkey(DB_HANDLE, any()) } returns
                VotingHotkey(rawAddress = ByteArray(32), address = "hotkey-address")

            val result = env.useCase()(ROUND_ID)

            assertIs<VotingRoundPreparationResult.Ready>(result)
            assertEquals(3, result.bundleCount)
            assertEquals(900L, result.eligibleWeight)
            assertEquals("hotkey-address", result.hotkeyAddress)
            coVerify(exactly = 1) {
                env.votingRecoveryRepository.storeBundleSetup(
                    accountUuid = ACCOUNT_UUID,
                    roundId = ROUND_ID,
                    bundleCount = 3,
                    eligibleWeight = 900L,
                    bundleWeights = listOf(300L, 300L, 300L)
                )
            }
        }

    @Test
    fun `an empty wallet note set at the snapshot height maps to Ineligible NO_NOTES`() =
        runTest {
            val env = Environment()
            env.stubHappyPathUpToDbOpen()
            coEvery { env.votingCryptoClient.getRoundState(DB_HANDLE, ROUND_ID) } returns null
            coEvery {
                env.votingCryptoClient.getWalletNotesJson(any(), any(), any(), any())
            } returns EMPTY_NOTES_JSON

            val result = env.useCase()(ROUND_ID)

            assertIs<VotingRoundPreparationResult.Ineligible>(result)
            assertEquals(VoteIneligibilityReason.NO_NOTES, result.reason)
            assertEquals(0L, result.eligibleWeight)
            coVerify(exactly = 0) { env.votingCryptoClient.setupBundles(any(), any(), any()) }
        }

    @Test
    fun `resuming a round whose native bundle count exceeds the current snapshot fails closed`() =
        runTest {
            val env = Environment()
            env.stubHappyPathUpToDbOpen()
            coEvery { env.votingCryptoClient.getRoundState(DB_HANDLE, ROUND_ID) } returns
                RoundStateInfo(
                    roundId = ROUND_ID,
                    phase = RoundPhase.DELEGATION,
                    snapshotHeight = SNAPSHOT_HEIGHT,
                    hotkeyAddress = "existing-hotkey",
                    delegatedWeight = 500L,
                    proofGenerated = false
                )
            // The native DB claims 5 bundles were set up, but recomputing bundle setup from the
            // current snapshot's own note set only accounts for 3 -- an inconsistency between
            // persisted native state and the snapshot it should have been derived from.
            coEvery { env.votingCryptoClient.getBundleCount(DB_HANDLE, ROUND_ID) } returns 5
            coEvery {
                env.votingCryptoClient.getWalletNotesJson(any(), any(), any(), any())
            } returns NON_EMPTY_NOTES_JSON
            coEvery { env.votingCryptoClient.computeBundleSetup(NON_EMPTY_NOTES_JSON) } returns
                VotingBundleSetupResult(
                    bundleCount = 3,
                    eligibleWeight = 300L,
                    bundleWeights = listOf(100L, 100L, 100L)
                )

            assertFailsWith<IllegalArgumentException> {
                env.useCase()(ROUND_ID)
            }

            // Fails closed, not silently reset: clearRound/resetVotingSessionState must never be
            // reached from this path -- the inconsistency is surfaced as an error instead of
            // being papered over by discarding the round's persisted state.
            coVerify(exactly = 0) { env.votingCryptoClient.clearRound(any(), any()) }
            coVerify(exactly = 0) { env.votingCryptoClient.resetVotingSessionState(any(), any()) }
            // Still closes the DB handle even on this failure path (the finally block).
            coVerify(exactly = 1) { env.votingCryptoClient.closeVotingDb(DB_HANDLE) }
        }

    @Test
    fun `resuming a round recovers bundle setup truncated to the native bundle count`() =
        runTest {
            val env = Environment()
            env.stubHappyPathUpToDbOpen()
            coEvery { env.votingCryptoClient.getRoundState(DB_HANDLE, ROUND_ID) } returns
                RoundStateInfo(
                    roundId = ROUND_ID,
                    phase = RoundPhase.DELEGATION,
                    snapshotHeight = SNAPSHOT_HEIGHT,
                    hotkeyAddress = "existing-hotkey",
                    delegatedWeight = 500L,
                    proofGenerated = false
                )
            // hotkeyBound is true on this path (existingRoundState.hotkeyAddress != null), which
            // requires an already-stored seed -- generating a fresh one is only for a first-time
            // hotkey bind.
            coEvery { env.votingHotkeySeedProvider.get(any()) } returns ByteArray(64) { 0x07 }
            coEvery { env.votingCryptoClient.getBundleCount(DB_HANDLE, ROUND_ID) } returns 2
            coEvery {
                env.votingCryptoClient.getWalletNotesJson(any(), any(), any(), any())
            } returns NON_EMPTY_NOTES_JSON
            // The snapshot's own note set now accounts for 3 bundles (the wallet gained notes
            // since setup), but only the first 2 -- the ones the native DB actually persisted --
            // are recovered; the third is deliberately dropped, not silently added.
            coEvery { env.votingCryptoClient.computeBundleSetup(NON_EMPTY_NOTES_JSON) } returns
                VotingBundleSetupResult(
                    bundleCount = 3,
                    eligibleWeight = 300L,
                    bundleWeights = listOf(100L, 100L, 100L)
                )

            val result = env.useCase()(ROUND_ID)

            assertIs<VotingRoundPreparationResult.Ready>(result)
            assertEquals(2, result.bundleCount)
            assertEquals(200L, result.eligibleWeight)
            assertEquals("existing-hotkey", result.hotkeyAddress)
            coVerify(exactly = 1) {
                env.votingRecoveryRepository.storeBundleSetup(
                    accountUuid = ACCOUNT_UUID,
                    roundId = ROUND_ID,
                    bundleCount = 2,
                    eligibleWeight = 200L,
                    bundleWeights = listOf(100L, 100L)
                )
            }
            // hotkeyBound was true (existingRoundState.hotkeyAddress != null) -- no fresh hotkey
            // is generated or persisted on the resume path.
            coVerify(exactly = 0) { env.votingCryptoClient.generateHotkey(any(), any()) }
        }

    @Test
    fun `cancellation from a native bundle-count lookup propagates rather than being swallowed`() =
        runTest {
            val env = Environment()
            env.stubHappyPathUpToDbOpen()
            coEvery { env.votingCryptoClient.getRoundState(DB_HANDLE, ROUND_ID) } returns
                RoundStateInfo(
                    roundId = ROUND_ID,
                    phase = RoundPhase.DELEGATION,
                    snapshotHeight = SNAPSHOT_HEIGHT,
                    hotkeyAddress = "existing-hotkey",
                    delegatedWeight = 500L,
                    proofGenerated = false
                )
            coEvery { env.votingCryptoClient.getBundleCount(DB_HANDLE, ROUND_ID) } throws
                CancellationException("cancelled")

            assertFailsWith<CancellationException> {
                env.useCase()(ROUND_ID)
            }

            // Cancellation still runs the finally block's cleanup (wrapped in NonCancellable in
            // the use case itself), even though the cancellation propagates past it.
            coVerify(exactly = 1) { env.votingCryptoClient.closeVotingDb(DB_HANDLE) }
        }

    /**
     * Shared mocks plus the wiring every scenario above needs to get from `invoke(roundId)` past
     * the WalletSyncing gate and DB-open, leaving each test to stub only its own
     * getRoundState/getBundleCount/computeBundleSetup/setupBundles branch.
     */
    private class Environment {
        val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        val votingSessionStore = mockk<VotingSessionStore>(relaxed = true)
        val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
        val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>(relaxed = true)
        val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        val synchronizerProvider = mockk<SynchronizerProvider>()
        val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()

        fun useCase() =
            PrepareVotingRoundUseCase(
                resolveVotingRoundSession = resolveVotingRoundSession,
                votingRecoveryRepository = votingRecoveryRepository,
                votingSessionStore = votingSessionStore,
                votingCryptoClient = votingCryptoClient,
                votingHotkeySeedProvider = votingHotkeySeedProvider,
                votingProofPrecomputeRepository = votingProofPrecomputeRepository,
                synchronizerProvider = synchronizerProvider,
                getSelectedWalletAccount = getSelectedWalletAccount
            )

        suspend fun stubHappyPathUpToDbOpen() {
            val session =
                votingSession(voteRoundId = ROUND_ID_BYTES, snapshotHeight = SNAPSHOT_HEIGHT)
            val selectedAccount = zashiAccount()

            val synchronizer = mockk<Synchronizer>()
            every { synchronizer.fullyScannedHeight } returns MutableStateFlow(BlockHeight.new(SNAPSHOT_HEIGHT))
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { synchronizer.getTreeState(any()) } returns ByteArray(32)

            coEvery { resolveVotingRoundSession(ROUND_ID) } returns
                VotingRoundSessionContext(session = session, serviceConfig = VotingServiceConfig.EMPTY)
            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/data/wallet.db"
            coEvery { votingCryptoClient.openVotingDb(any()) } returns DB_HANDLE
            // Explicit rather than relying on the relaxed mock's default: MockK's relaxed
            // auto-answer for a suspend fun returning a nullable type is not reliably `null`
            // and produces a ClassCastException deep in getOrCreateHotkeySeed otherwise.
            coEvery { votingRecoveryRepository.get(any(), any()) } returns null
            coEvery { votingHotkeySeedProvider.get(any()) } returns null
        }
    }

    private companion object {
        val ROUND_ID_BYTES = ByteArray(32) { 0x0A }
        val ROUND_ID = ROUND_ID_BYTES.toHex()
        const val DB_HANDLE = 42L
        const val SNAPSHOT_HEIGHT = 100L
        val ACCOUNT_UUID = zashiAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
        const val NON_EMPTY_NOTES_JSON = "[{\"note\":1}]"
        const val EMPTY_NOTES_JSON = "[]"

        fun zashiAccount(): ZashiAccount =
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

        fun votingSession(
            voteRoundId: ByteArray,
            snapshotHeight: Long
        ): VotingSession {
            val voteEndTime = Instant.parse("2026-09-21T12:00:00Z")
            return VotingSession(
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
}
