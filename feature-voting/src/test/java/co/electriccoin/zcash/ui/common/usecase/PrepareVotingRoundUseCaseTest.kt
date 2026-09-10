package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.RoundPhase
import co.electriccoin.zcash.ui.common.model.voting.RoundStateInfo
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingBundleSetupResult
import co.electriccoin.zcash.ui.common.model.voting.VotingHotkey
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class PrepareVotingRoundUseCaseTest {
    @Test
    fun preparedRecoveryResumesWithoutReadingNativeBundleCount() =
        runTest {
            var bundleCountReads = 0

            val action =
                existingRoundRecoveryAction(
                    hasPreparedRecovery = true,
                    getBundleCount = {
                        bundleCountReads += 1
                        0
                    }
                )

            assertEquals(ExistingRoundRecoveryAction.RESUME, action)
            assertEquals(0, bundleCountReads)
        }

    @Test
    fun nativeBundlesResumeWhenPreferenceRecoveryIsMissing() =
        runTest {
            val action =
                existingRoundRecoveryAction(
                    hasPreparedRecovery = false,
                    getBundleCount = { 1 }
                )

            assertEquals(ExistingRoundRecoveryAction.RESUME, action)
        }

    @Test
    fun emptyExistingRoundIsSafeToReinitialize() =
        runTest {
            val action =
                existingRoundRecoveryAction(
                    hasPreparedRecovery = false,
                    getBundleCount = { 0 }
                )

            assertEquals(ExistingRoundRecoveryAction.REINITIALIZE, action)
        }

    @Test
    fun unknownNativeStateFailsClosedInsteadOfRequestingClear() =
        runTest {
            val action =
                existingRoundRecoveryAction(
                    hasPreparedRecovery = false,
                    getBundleCount = { error("database unavailable") }
                )

            assertEquals(ExistingRoundRecoveryAction.FAIL_CLOSED, action)
        }

    @Test
    fun nativeBundleLookupCancellationPropagates() =
        runTest {
            val cancellation = CancellationException("cancelled")

            val thrown =
                assertFailsWith<CancellationException> {
                    existingRoundRecoveryAction(
                        hasPreparedRecovery = false,
                        getBundleCount = { throw cancellation }
                    )
                }

            assertSame(cancellation, thrown)
        }

    @Test
    fun trimDeletesSuffixAndStoresKeptSetup() =
        runTest {
            val fixture = PrepareFixture(bundleWeights = listOf(5_000 * ZEC, 5_000 * ZEC, 5 * ZEC, 5 * ZEC))

            val result = fixture.useCase()(ROUND_ID)

            val ready = assertIs<VotingRoundPreparationResult.Ready>(result)
            assertEquals(2, ready.bundleCount)
            assertEquals(10_000 * ZEC, ready.eligibleWeight)
            assertEquals(listOf(2), fixture.deletedSuffixKeepCounts)
            assertEquals(
                StoredBundleSetup(
                    bundleCount = 2,
                    eligibleWeight = 10_000 * ZEC,
                    bundleWeights = listOf(5_000 * ZEC, 5_000 * ZEC),
                    trimmedBundleCount = 2,
                    trimmedWeight = 10 * ZEC
                ),
                fixture.storedBundleSetups.single()
            )
            assertEquals(listOf(0, 1), fixture.witnessedBundleIndices)
        }

    @Test
    fun noTrimSkipsDelete() =
        runTest {
            val fixture = PrepareFixture(bundleWeights = listOf(5_000 * ZEC, 5_000 * ZEC))

            val result = fixture.useCase()(ROUND_ID)

            val ready = assertIs<VotingRoundPreparationResult.Ready>(result)
            assertEquals(2, ready.bundleCount)
            assertEquals(emptyList(), fixture.deletedSuffixKeepCounts)
            assertEquals(
                StoredBundleSetup(
                    bundleCount = 2,
                    eligibleWeight = 10_000 * ZEC,
                    bundleWeights = listOf(5_000 * ZEC, 5_000 * ZEC),
                    trimmedBundleCount = 0,
                    trimmedWeight = 0
                ),
                fixture.storedBundleSetups.single()
            )
        }

    @Test
    fun resumedRoundNeverReRunsSetup() =
        runTest {
            val fixture =
                PrepareFixture(
                    bundleWeights = listOf(5_000 * ZEC, 5_000 * ZEC, 5 * ZEC, 5 * ZEC),
                    existingRoundState =
                        RoundStateInfo(
                            roundId = ROUND_ID,
                            phase = RoundPhase.DELEGATION,
                            snapshotHeight = SNAPSHOT_HEIGHT,
                            hotkeyAddress = HOTKEY_ADDRESS,
                            delegatedWeight = null,
                            proofGenerated = false
                        ),
                    recovery =
                        VotingRecoverySnapshot(
                            accountUuid = ACCOUNT_UUID,
                            roundId = ROUND_ID,
                            phase = VotingRecoveryPhase.BUNDLES_PREPARED,
                            bundleCount = 2,
                            eligibleWeight = 10_000 * ZEC,
                            bundleWeights = listOf(5_000 * ZEC, 5_000 * ZEC),
                            trimmedBundleCount = 2,
                            trimmedWeight = 10 * ZEC,
                            hotkeyAddress = HOTKEY_ADDRESS
                        )
                )

            val result = fixture.useCase()(ROUND_ID)

            val ready = assertIs<VotingRoundPreparationResult.Ready>(result)
            assertEquals(2, ready.bundleCount)
            coVerify(exactly = 0) { fixture.crypto.setupBundles(any(), any(), any()) }
            coVerify(exactly = 0) { fixture.crypto.deleteSkippedBundles(any(), any(), any()) }
            assertEquals(emptyList(), fixture.storedBundleSetups)
        }

    private data class StoredBundleSetup(
        val bundleCount: Int,
        val eligibleWeight: Long,
        val bundleWeights: List<Long>,
        val trimmedBundleCount: Int,
        val trimmedWeight: Long
    )

    private class PrepareFixture(
        private val bundleWeights: List<Long>,
        private val existingRoundState: RoundStateInfo? = null,
        private val recovery: VotingRecoverySnapshot? = null
    ) {
        val crypto = mockk<VotingCryptoClient>(relaxed = true)
        val deletedSuffixKeepCounts = mutableListOf<Int>()
        val storedBundleSetups = mutableListOf<StoredBundleSetup>()
        val witnessedBundleIndices = mutableListOf<Int>()

        private val recoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        private val sessionStore = mockk<VotingSessionStore>(relaxed = true)
        private val hotkeySeedProvider = mockk<VotingHotkeySeedProvider>(relaxed = true)
        private val proofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        private val synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true)
        private val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        private val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
        private val selectedAccount = keystoneAccount()

        init {
            val synchronizer = mockk<Synchronizer>(relaxed = true)
            every { synchronizer.network } returns ZcashNetwork.Mainnet
            every { synchronizer.fullyScannedHeight } returns
                MutableStateFlow(BlockHeight.new(SNAPSHOT_HEIGHT + 10))
            coEvery { synchronizer.getTreeState(any()) } returns ByteArray(0)
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/wallet/data.sqlite3"
            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { resolveVotingRoundSession(ROUND_ID) } returns
                VotingRoundSessionContext(
                    session = votingSession(),
                    serviceConfig =
                        VotingServiceConfig(
                            voteServers = listOf(VotingServiceConfig.ServiceEndpoint("https://vote", "vote")),
                            pirEndpoints = listOf(VotingServiceConfig.ServiceEndpoint("https://pir", "pir")),
                            pirLayout = VotingPirLayout(pirDepth = 1, tier0Layers = 1, tier1Layers = 1, polyLen = 4096)
                        )
                )
            coEvery { recoveryRepository.get(ACCOUNT_UUID, ROUND_ID) } returns recovery
            coEvery {
                recoveryRepository.storeBundleSetup(any(), any(), any(), any(), any(), any(), any())
            } answers {
                storedBundleSetups +=
                    StoredBundleSetup(
                        bundleCount = thirdArg<Int>(),
                        eligibleWeight = arg<Long>(3),
                        bundleWeights = arg<List<Long>>(4),
                        trimmedBundleCount = arg<Int>(5),
                        trimmedWeight = arg<Long>(6)
                    )
            }
            coEvery { hotkeySeedProvider.get(ACCOUNT_UUID) } returns ByteArray(64) { 9 }

            coEvery { crypto.openVotingDb(any()) } returns 1
            coEvery { crypto.getRoundState(any(), any()) } returns existingRoundState
            coEvery { crypto.getBundleCount(any(), any()) } returns bundleWeights.size
            coEvery { crypto.getWalletNotesJson(any(), any(), any(), any()) } returns notesJson(bundleWeights)
            coEvery { crypto.setupBundles(any(), any(), any()) } returns
                VotingBundleSetupResult(
                    bundleCount = bundleWeights.size,
                    eligibleWeight = bundleWeights.sum(),
                    bundleWeights = bundleWeights
                )
            coEvery { crypto.computeBundleSetup(any()) } returns
                VotingBundleSetupResult(
                    bundleCount = bundleWeights.size,
                    eligibleWeight = bundleWeights.sum(),
                    bundleWeights = bundleWeights
                )
            coEvery { crypto.deleteSkippedBundles(any(), any(), any()) } answers {
                deletedSuffixKeepCounts += thirdArg<Int>()
                0L
            }
            coEvery { crypto.generateNoteWitnessesJson(any(), any(), any(), any(), any(), any()) } answers {
                witnessedBundleIndices += thirdArg<Int>()
                "{}"
            }
            coEvery { crypto.delegationPhases(any(), any()) } returns emptyList()
            coEvery { crypto.generateHotkey(any(), any()) } returns
                VotingHotkey(rawAddress = ByteArray(32), address = HOTKEY_ADDRESS)
        }

        fun useCase() =
            PrepareVotingRoundUseCase(
                resolveVotingRoundSession = resolveVotingRoundSession,
                votingRecoveryRepository = recoveryRepository,
                votingSessionStore = sessionStore,
                votingCryptoClient = crypto,
                votingHotkeySeedProvider = hotkeySeedProvider,
                votingProofPrecomputeRepository = proofPrecomputeRepository,
                synchronizerProvider = synchronizerProvider,
                getSelectedWalletAccount = getSelectedWalletAccount,
                getWalletSeedBytes = mockk(relaxed = true)
            )
    }

    private companion object {
        const val ROUND_ID = "1111111111111111111111111111111111111111111111111111111111111111"
        const val HOTKEY_ADDRESS = "hotkey"
        const val SNAPSHOT_HEIGHT = 3_459_350L
        const val ZEC = 100_000_000L
        const val NOTES_PER_BUNDLE = 5

        val ACCOUNT_UUID: String = AccountFixture.new().accountUuid.toVotingAccountScopeId()

        /**
         * Builds a notes array the Kotlin chunker turns back into exactly [bundleWeights]: every
         * bundle is a full run of [NOTES_PER_BUNDLE] equal notes, and the runs are already in
         * value-DESC order.
         */
        fun notesJson(bundleWeights: List<Long>): String {
            var position = 0L
            val notes =
                bundleWeights.flatMap { bundleWeight ->
                    List(NOTES_PER_BUNDLE) {
                        """{"value":${bundleWeight / NOTES_PER_BUNDLE},"position":${position++}}"""
                    }
                }
            return notes.joinToString(prefix = "[", postfix = "]")
        }

        fun votingSession() =
            VotingSession(
                voteRoundId = ROUND_ID.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                snapshotHeight = SNAPSHOT_HEIGHT,
                snapshotBlockhash = ByteArray(32),
                proposalsHash = ByteArray(32),
                voteEndTime = Instant.parse("2100-01-02T00:00:00Z"),
                ceremonyStart = Instant.parse("2100-01-01T00:00:00Z"),
                eaPK = ByteArray(32),
                vkZkp1 = ByteArray(32),
                vkZkp2 = ByteArray(32),
                vkZkp3 = ByteArray(32),
                ncRoot = ByteArray(32),
                nullifierIMTRoot = ByteArray(32),
                creator = "creator",
                title = "Round",
                description = "Round",
                discussionUrl = null,
                proposals =
                    listOf(
                        Proposal(
                            id = 1,
                            title = "Proposal 1",
                            description = "Proposal 1",
                            options = listOf(VoteOption(0, "Yes"), VoteOption(1, "No"))
                        )
                    ),
                status = SessionStatus.ACTIVE,
                createdAtHeight = 1
            )

        fun keystoneAccount() =
            KeystoneAccount(
                sdkAccount = AccountFixture.new(),
                unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
                orchardBalance = WalletBalanceFixture.newLong(),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
                transparentBalance = Zatoshi(0),
                isSelected = true
            )
    }
}
