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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Coverage for [PrepareVotingRoundUseCase.invoke]'s eligibility branches on the RESUME (existing
 * `RoundStateInfo`) bundle-setup path -- previously untested beyond
 * `PrepareVotingRoundUseCaseVoteEndTest`'s single WalletSyncing-gate scenario (nit #13 of Milan's
 * review of PR #6; the deleted pre-4.0 `PrepareVotingRoundUseCaseTest.kt` covered this class more
 * broadly with no replacement). `PrepareVotingRoundUseCaseRecoveryTest` covers the FRESH
 * (`getRoundState` returns `null`) setup path and the resume path's fail-closed/cancellation
 * scenarios -- `feature-voting`'s own `build.gradle.kts` carries a plain-JVM `org.json:json` test
 * dependency (added for `VotingApiProvider`), so [PrepareVotingRoundUseCase]'s inline
 * `org.json.JSONArray(notesJson).length()` check is JVM-testable here, unlike the SDK repo's
 * `sdk-lib` module (no such dependency there -- see `VotingSdkRoundDriveProgressMappersTest`'s own
 * doc comment for that constraint). [VoteIneligibilityReason.NO_NOTES] itself (the FRESH path's
 * empty-notes branch) is not yet covered by either file -- a real remaining gap, not a JVM-test
 * limitation.
 */
class PrepareVotingRoundUseCaseEligibilityTest {
    @Test
    fun `zero eligible weight from a recovered bundle setup maps to Ineligible BALANCE_TOO_LOW`() =
        runTest {
            val env = Env()
            env.stubExistingRoundState(hotkeyAddress = "existing-hotkey")
            env.stubRecoveredBundleSetup(dbBundleCount = 2, computedWeights = listOf(0L, 0L))

            val result = env.invoke()

            assertIs<VotingRoundPreparationResult.Ineligible>(result)
            assertEquals(VoteIneligibilityReason.BALANCE_TOO_LOW, result.reason)
            assertEquals(0L, result.eligibleWeight)
        }

    @Test
    fun `a resumed round with an already-bound hotkey reuses it rather than generating a new one`() =
        runTest {
            val env = Env()
            env.stubExistingRoundState(hotkeyAddress = "existing-hotkey")
            env.stubRecoveredBundleSetup(dbBundleCount = 2, computedWeights = listOf(5L, 5L))
            // getOrCreateHotkeySeed is called unconditionally regardless of hotkeyBound (see
            // PrepareVotingRoundUseCase.invoke's own call site) -- stubbed even though this
            // scenario's hotkeyAddress branch below never uses the returned seed.
            coEvery { env.votingHotkeySeedProvider.get(env.accountUuid) } returns ByteArray(64)

            val result = env.invoke()

            assertIs<VotingRoundPreparationResult.Ready>(result)
            assertEquals("existing-hotkey", result.hotkeyAddress)
            coVerify(exactly = 0) { env.votingCryptoClient.generateHotkey(any(), any()) }
        }

    @Test
    fun `a resumed round with no bound hotkey yet generates and persists a fresh one`() =
        runTest {
            val env = Env()
            env.stubExistingRoundState(hotkeyAddress = null)
            env.stubRecoveredBundleSetup(dbBundleCount = 1, computedWeights = listOf(10L))
            coEvery { env.votingHotkeySeedProvider.get(env.accountUuid) } returns ByteArray(64)
            coEvery {
                env.votingCryptoClient.generateHotkey(any(), any())
            } returns VotingHotkey(rawAddress = ByteArray(32), address = "fresh-hotkey")

            val result = env.invoke()

            assertIs<VotingRoundPreparationResult.Ready>(result)
            assertEquals("fresh-hotkey", result.hotkeyAddress)
            coVerify(exactly = 1) {
                env.votingRecoveryRepository.storeHotkey(env.accountUuid, env.roundId, "fresh-hotkey")
            }
        }

    /** Shared fixture wiring for [PrepareVotingRoundUseCase.invoke]'s RESUME path. */
    private class Env {
        val roundIdBytes = ByteArray(32) { 0x0B }
        val roundId = roundIdBytes.toHex()
        val accountUuid: String

        val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        val votingSessionStore = mockk<VotingSessionStore>(relaxed = true)
        val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
        val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>(relaxed = true)
        val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        val synchronizerProvider = mockk<SynchronizerProvider>()
        val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
        val synchronizer = mockk<Synchronizer>()

        private val useCase =
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

        init {
            val voteEndTime = Instant.parse("2026-09-21T12:00:00Z")
            val session = votingSession(voteRoundId = roundIdBytes, voteEndTime = voteEndTime, snapshotHeight = 100L)
            val selectedAccount = zashiAccount()
            accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            // Fully synced past the round's snapshot height -- clears the WalletSyncing gate so
            // every scenario here reaches the bundle-setup/eligibility logic under test.
            every { synchronizer.fullyScannedHeight } returns MutableStateFlow(BlockHeight.new(200L))
            every { synchronizer.network } returns ZcashNetwork.Testnet

            coEvery { resolveVotingRoundSession(roundId) } returns
                VotingRoundSessionContext(session = session, serviceConfig = VotingServiceConfig.EMPTY)
            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/voting-wallet.db"
            coEvery { votingCryptoClient.openVotingDb(any()) } returns 1L
            coEvery { votingCryptoClient.getWalletNotesJson(any(), any(), any(), any()) } returns "[]"
        }

        fun stubExistingRoundState(hotkeyAddress: String?) {
            coEvery { votingCryptoClient.getRoundState(any(), roundId) } returns
                RoundStateInfo(
                    roundId = roundId,
                    phase = RoundPhase.DELEGATION,
                    snapshotHeight = 100L,
                    hotkeyAddress = hotkeyAddress,
                    delegatedWeight = null,
                    proofGenerated = false
                )
        }

        fun stubRecoveredBundleSetup(
            dbBundleCount: Int,
            computedWeights: List<Long>
        ) {
            coEvery { votingCryptoClient.getBundleCount(any(), roundId) } returns dbBundleCount
            coEvery { votingCryptoClient.computeBundleSetup(any()) } returns
                VotingBundleSetupResult(
                    bundleCount = computedWeights.size,
                    eligibleWeight = computedWeights.sum(),
                    bundleWeights = computedWeights
                )
        }

        suspend fun invoke() = useCase(roundId)

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
}
