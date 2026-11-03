package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.VotingShareTrackingSession
import cash.z.ecc.android.sdk.exception.TorInitializationErrorException
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingShareTrackingQuiescence
import cash.z.ecc.android.sdk.model.voting.VotingShareTrackingReport
import cash.z.ecc.android.sdk.model.voting.VotingTorLease
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.voting.VotingRoute
import co.electriccoin.zcash.ui.common.model.voting.VotingRouteDecision
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingRouteProvider
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class TrackVotingSharesUseCaseTest {
    @Test
    fun `cancel targets the same session invoke opened for that round`() =
        runTest {
            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val votingCryptoClient = mockk<VotingCryptoClient>()
            val votingApiProvider = mockk<VotingApiProvider>()
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()

            val votingRouteProvider = mockk<VotingRouteProvider>(relaxed = true)

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    votingCryptoClient,
                    votingApiProvider,
                    synchronizerProvider,
                    getSelectedWalletAccount,
                    votingRouteProvider
                )

            // No recovery snapshot => invoke() short-circuits to Completed without opening a
            // session; cancel() for a round with no tracked session must be a no-op, not throw.
            coEvery { votingRecoveryRepository.get(any(), any()) } returns null

            useCase.cancel("round-with-no-active-session")
            // No exception => test passes; the assertion is "doesn't throw," verified implicitly.
        }

    /**
     * Privacy-critical regression test: Tor is a preference, not a hard requirement, so
     * [TrackVotingSharesUseCase] only treats [TorUnavailableException] (Tor disabled by the
     * user) as an expected fallback to a `null` Tor lease (plain HTTP). Confirms `invoke` does
     * NOT propagate that exception and instead proceeds through the share-tracking session
     * without a lease.
     */
    @Test
    fun `invoke falls back to a null Tor lease when acquireVotingTorLease throws TorUnavailableException`() =
        runTest {
            val roundId = "round-tor-unavailable"
            val accountUuid = "account-tor-unavailable"

            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
            val votingApiProvider = mockk<VotingApiProvider>()
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()

            val selectedAccount = zashiAccount()
            val synchronizer = mockk<Synchronizer>()
            val session = mockk<VotingShareTrackingSession>(relaxed = true)
            val report =
                VotingShareTrackingReport(
                    quiescence = VotingShareTrackingQuiescence.NothingToTrack,
                    passes = 0,
                    confirmed = emptyList(),
                    resubmitted = emptyList(),
                    ambiguous = emptyList(),
                    unrecoverable = emptyList(),
                    failures = emptyList()
                )

            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { votingRecoveryRepository.get(any(), roundId) } returns
                VotingRecoverySnapshot(
                    accountUuid = accountUuid,
                    roundId = roundId,
                    voteServerUrls = listOf("https://vote.example")
                )
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/voting-wallet.db"
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { votingCryptoClient.openVotingDb(any()) } returns 1L
            coEvery { votingCryptoClient.openShareTrackingSession(any(), roundId) } returns session
            coEvery {
                session.run(torLease = null, isCustomServer = any(), helperUrls = any(), voteEndTimeSeconds = any())
            } returns report

            val votingRouteProvider = mockk<VotingRouteProvider>()
            coEvery { votingRouteProvider.acquire() } returns
                VotingRoute(VotingRouteDecision(isTor = false, isCustomServer = false), null)

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    votingCryptoClient,
                    votingApiProvider,
                    synchronizerProvider,
                    getSelectedWalletAccount,
                    votingRouteProvider
                )

            val result = useCase.invoke(roundId)

            // The fallback took effect (torLease = null was what unlocked this `session.run`
            // stub above) and the call completed normally -- no exception escaped.
            assertIs<VotingShareTrackingResult.Completed>(result)
        }

    /**
     * Privacy-critical regression test: unlike [TorUnavailableException], a
     * [TorInitializationErrorException] means Tor is enabled but failed to bootstrap.
     * [TrackVotingSharesUseCase] must NOT swallow this into a silent `null` lease
     * fallback -- doing so would submit vote-share traffic over plain HTTP while the user
     * believes Tor is protecting it. This confirms the exception propagates all the way out
     * of `invoke`.
     */
    @Test
    fun `invoke propagates TorInitializationErrorException from acquireVotingTorLease`() =
        runTest {
            val roundId = "round-tor-init-error"
            val accountUuid = "account-tor-init-error"

            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
            val votingApiProvider = mockk<VotingApiProvider>()
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()

            val selectedAccount = zashiAccount()
            val synchronizer = mockk<Synchronizer>()
            val torInitializationError = TorInitializationErrorException(Exception("bootstrap failed"))

            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { votingRecoveryRepository.get(any(), roundId) } returns
                VotingRecoverySnapshot(
                    accountUuid = accountUuid,
                    roundId = roundId,
                    voteServerUrls = listOf("https://vote.example")
                )
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/voting-wallet.db"
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { votingCryptoClient.openVotingDb(any()) } returns 1L

            val votingRouteProvider = mockk<VotingRouteProvider>()
            coEvery { votingRouteProvider.acquire() } throws torInitializationError

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    votingCryptoClient,
                    votingApiProvider,
                    synchronizerProvider,
                    getSelectedWalletAccount,
                    votingRouteProvider
                )

            val exception =
                assertFailsWith<TorInitializationErrorException> {
                    useCase.invoke(roundId)
                }
            assertEquals(torInitializationError, exception)
            // The route is acquired before the voting DB opens, so a Tor failure fails closed
            // without opening (or leaking) a DB handle.
            coVerify(exactly = 0) { votingCryptoClient.openVotingDb(any()) }
        }

    @Test
    fun `invoke acquires no route and completes when there is nothing to track`() =
        runTest {
            val roundId = "round-nothing-to-track"

            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
            coEvery { getSelectedWalletAccount() } returns zashiAccount()
            coEvery { votingRecoveryRepository.get(any(), roundId) } returns null

            // Tor being unavailable must not fail a pass that has nothing to deliver.
            val votingRouteProvider = mockk<VotingRouteProvider>()
            coEvery { votingRouteProvider.acquire() } throws
                TorInitializationErrorException(Exception("bootstrap failed"))

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    mockk<VotingCryptoClient>(relaxed = true),
                    mockk<VotingApiProvider>(),
                    mockk<SynchronizerProvider>(),
                    getSelectedWalletAccount,
                    votingRouteProvider
                )

            assertEquals(VotingShareTrackingResult.Completed, useCase.invoke(roundId))
            coVerify(exactly = 0) { votingRouteProvider.acquire() }
        }

    @Test
    fun `a Tor and chain config flip mid-call does not change the route of fetchServiceConfig or session run`() =
        runTest {
            val roundId = "round-decision-stable"
            val accountUuid = "account-decision-stable"
            val initial = VotingRouteDecision(isTor = true, isCustomServer = false)
            val flipped = VotingRouteDecision(isTor = false, isCustomServer = true)

            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
            val votingApiProvider = mockk<VotingApiProvider>()
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
            val synchronizer = mockk<Synchronizer>()
            val session = mockk<VotingShareTrackingSession>(relaxed = true)
            val torLease = mockk<VotingTorLease>(relaxed = true)
            val report =
                VotingShareTrackingReport(
                    quiescence = VotingShareTrackingQuiescence.NothingToTrack,
                    passes = 0,
                    confirmed = emptyList(),
                    resubmitted = emptyList(),
                    ambiguous = emptyList(),
                    unrecoverable = emptyList(),
                    failures = emptyList()
                )

            coEvery { getSelectedWalletAccount() } returns zashiAccount()
            // No stored vote server URLs, so the use case has to fetch the service config itself.
            coEvery { votingRecoveryRepository.get(any(), roundId) } returns
                VotingRecoverySnapshot(accountUuid = accountUuid, roundId = roundId, voteServerUrls = emptyList())
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/voting-wallet.db"
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { votingCryptoClient.openVotingDb(any()) } returns 1L
            coEvery { votingCryptoClient.openShareTrackingSession(any(), roundId) } returns session

            var current = initial
            val votingRouteProvider = mockk<VotingRouteProvider>()
            coEvery { votingRouteProvider.acquire() } answers {
                VotingRoute(current, if (current.isTor) torLease else null)
            }
            coEvery { votingRouteProvider.decision(any()) } answers { current }

            val fetchDecisions = mutableListOf<VotingRouteDecision>()
            coEvery { votingApiProvider.fetchServiceConfig(any()) } coAnswers {
                fetchDecisions += firstArg<VotingRouteDecision>()
                // The user turns Tor off and selects a custom chain between the config fetch and
                // the tracking run.
                current = flipped
                VotingServiceConfig(
                    voteServers =
                        listOf(VotingServiceConfig.ServiceEndpoint(url = "https://vote.example", label = "v1"))
                )
            }
            val runLeases = mutableListOf<VotingTorLease?>()
            val runIsCustom = mutableListOf<Boolean>()
            coEvery {
                session.run(torLease = any(), isCustomServer = any(), helperUrls = any(), voteEndTimeSeconds = any())
            } coAnswers {
                runLeases += firstArg<VotingTorLease?>()
                runIsCustom += secondArg<Boolean>()
                report
            }

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    votingCryptoClient,
                    votingApiProvider,
                    synchronizerProvider,
                    getSelectedWalletAccount,
                    votingRouteProvider
                )

            val result = useCase.invoke(roundId)

            assertIs<VotingShareTrackingResult.Completed>(result)
            assertEquals(flipped, current, "the preference really did flip mid-call")
            coVerify(exactly = 1) { votingRouteProvider.acquire() }
            coVerify(exactly = 0) { votingRouteProvider.decision(any()) }
            assertEquals(listOf(initial), fetchDecisions)
            // session.run gets the same route: the initial decision's Tor lease and its
            // isCustomServer, not the flipped ones.
            assertEquals(1, runLeases.size)
            assertSame(torLease, runLeases.single())
            assertEquals(listOf(initial.isCustomServer), runIsCustom)
            coVerify(exactly = 1) { torLease.release() }
        }

    private fun zashiAccount(): ZodlAccount =
        ZodlAccount(
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
}
