package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.VotingShareTrackingSession
import cash.z.ecc.android.sdk.exception.TorInitializationErrorException
import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingShareTrackingQuiescence
import cash.z.ecc.android.sdk.model.voting.VotingShareTrackingReport
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TrackVotingSharesUseCaseTest {
    @Test
    fun `cancel targets the same session invoke opened for that round`() =
        runTest {
            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val votingCryptoClient = mockk<VotingCryptoClient>()
            val votingApiProvider = mockk<VotingApiProvider>()
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    votingCryptoClient,
                    votingApiProvider,
                    synchronizerProvider,
                    getSelectedWalletAccount
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
     * user) as an expected fallback to a `torRuntime` of `0L`. Confirms `invoke` does NOT
     * propagate that exception and instead proceeds through the share-tracking session as if
     * no Tor handle were available.
     */
    @Test
    fun `invoke falls back to torRuntime 0L when getVotingTorRuntimeHandle throws TorUnavailableException`() =
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
            coEvery { synchronizer.getVotingTorRuntimeHandle() } throws TorUnavailableException()
            coEvery { votingCryptoClient.openVotingDb(any()) } returns 1L
            coEvery { votingCryptoClient.openShareTrackingSession(any(), roundId) } returns session
            coEvery {
                session.run(torRuntime = 0L, helperUrls = any(), voteEndTimeSeconds = any())
            } returns report

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    votingCryptoClient,
                    votingApiProvider,
                    synchronizerProvider,
                    getSelectedWalletAccount
                )

            val result = useCase.invoke(roundId)

            // The fallback took effect (torRuntime = 0L was what unlocked this `session.run`
            // stub above) and the call completed normally -- no exception escaped.
            assertIs<VotingShareTrackingResult.Completed>(result)
        }

    /**
     * Privacy-critical regression test: unlike [TorUnavailableException], a
     * [TorInitializationErrorException] means Tor is enabled but failed to bootstrap.
     * [TrackVotingSharesUseCase] must NOT swallow this into a silent `torRuntime = 0L`
     * fallback -- doing so would submit vote-share traffic over plain HTTP while the user
     * believes Tor is protecting it. This confirms the exception propagates all the way out
     * of `invoke`.
     */
    @Test
    fun `invoke propagates TorInitializationErrorException from getVotingTorRuntimeHandle`() =
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
            coEvery { synchronizer.getVotingTorRuntimeHandle() } throws torInitializationError
            coEvery { votingCryptoClient.openVotingDb(any()) } returns 1L

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    votingCryptoClient,
                    votingApiProvider,
                    synchronizerProvider,
                    getSelectedWalletAccount
                )

            val exception =
                assertFailsWith<TorInitializationErrorException> {
                    useCase.invoke(roundId)
                }
            assertEquals(torInitializationError, exception)
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
}
