package co.electriccoin.zcash.voting

import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingPendingKeystoneRequest
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshActiveVotingSessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VotingHomeHooksImplTest {
    @Test
    fun recoverPendingRouteIfNeededSkipsNetworkRefreshWhenNothingPending() =
        runTest {
            val account = keystoneAccount()
            val accountUuid = account.sdkAccount.accountUuid.toVotingAccountScopeId()

            val votingRecoveryRepository = mockk<VotingRecoveryRepository>()
            coEvery { votingRecoveryRepository.getRoundIdsWithPendingKeystoneRequest(accountUuid) } returns emptyList()

            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
            coEvery { getSelectedWalletAccount() } returns account

            val refreshActiveVotingSession = mockk<RefreshActiveVotingSessionUseCase>()

            val hooks =
                VotingHomeHooksImpl(
                    votingRecoveryRepository = votingRecoveryRepository,
                    votingApiRepository = mockk(relaxed = true),
                    votingSessionStore = mockk(relaxed = true),
                    getSelectedWalletAccount = getSelectedWalletAccount,
                    refreshActiveVotingSession = refreshActiveVotingSession,
                    votingShareTrackingScheduler = mockk(relaxed = true),
                    navigationRouter = mockk(relaxed = true)
                )

            val result = hooks.recoverPendingRouteIfNeeded()

            assertFalse(result)
            coVerify(exactly = 0) { refreshActiveVotingSession() }
        }

    @Test
    fun recoverPendingRouteIfNeededRefreshesAndNavigatesWhenPendingRequestExists() =
        runTest {
            val roundId = "abcd1234"
            val account = keystoneAccount()
            val accountUuid = account.sdkAccount.accountUuid.toVotingAccountScopeId()
            val pendingRequest =
                VotingPendingKeystoneRequest(
                    bundleIndex = 0,
                    actionIndex = 0,
                    redactedPcztBase64 = "AAAA",
                    expectedSighashBase64 = "AAAA"
                )
            val recovery =
                VotingRecoverySnapshot(
                    accountUuid = accountUuid,
                    roundId = roundId,
                    pendingKeystoneRequest = pendingRequest,
                    draftChoices = mapOf(1 to 0)
                )

            val votingRecoveryRepository = mockk<VotingRecoveryRepository>()
            coEvery {
                votingRecoveryRepository.getRoundIdsWithPendingKeystoneRequest(accountUuid)
            } returns listOf(roundId)
            coEvery { votingRecoveryRepository.get(accountUuid, roundId) } returns recovery

            val session = votingSessionFixture(roundId)
            val snapshotFlow = MutableStateFlow(VotingApiSnapshot(sessionsByRoundId = mapOf(roundId to session)))
            val votingApiRepository = mockk<VotingApiRepository>(relaxed = true)
            every { votingApiRepository.snapshot } returns snapshotFlow

            val refreshActiveVotingSession = mockk<RefreshActiveVotingSessionUseCase>()
            coEvery { refreshActiveVotingSession() } returns Unit

            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
            coEvery { getSelectedWalletAccount() } returns account

            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val votingSessionStore = mockk<VotingSessionStore>(relaxed = true)

            val hooks =
                VotingHomeHooksImpl(
                    votingRecoveryRepository = votingRecoveryRepository,
                    votingApiRepository = votingApiRepository,
                    votingSessionStore = votingSessionStore,
                    getSelectedWalletAccount = getSelectedWalletAccount,
                    refreshActiveVotingSession = refreshActiveVotingSession,
                    votingShareTrackingScheduler = mockk(relaxed = true),
                    navigationRouter = navigationRouter
                )

            val result = hooks.recoverPendingRouteIfNeeded()

            assertTrue(result)
            coVerify(exactly = 1) { refreshActiveVotingSession() }
            coVerify(exactly = 1) { votingSessionStore.restoreDraftVotes(accountUuid, roundId, recovery.draftChoices) }
        }

    @Test
    fun recoverPendingRouteIfNeededIgnoresPendingRequestForRoundNoLongerActive() =
        runTest {
            val roundId = "abcd1234"
            val account = keystoneAccount()
            val accountUuid = account.sdkAccount.accountUuid.toVotingAccountScopeId()
            val recovery =
                VotingRecoverySnapshot(
                    accountUuid = accountUuid,
                    roundId = roundId,
                    pendingKeystoneRequest =
                        VotingPendingKeystoneRequest(
                            bundleIndex = 0,
                            actionIndex = 0,
                            redactedPcztBase64 = "AAAA",
                            expectedSighashBase64 = "AAAA"
                        ),
                    draftChoices = mapOf(1 to 0)
                )

            val votingRecoveryRepository = mockk<VotingRecoveryRepository>()
            coEvery {
                votingRecoveryRepository.getRoundIdsWithPendingKeystoneRequest(accountUuid)
            } returns listOf(roundId)
            coEvery { votingRecoveryRepository.get(accountUuid, roundId) } returns recovery

            // Round closed/expired server-side between the local check and the refresh.
            val snapshotFlow = MutableStateFlow(VotingApiSnapshot(sessionsByRoundId = emptyMap()))
            val votingApiRepository = mockk<VotingApiRepository>(relaxed = true)
            every { votingApiRepository.snapshot } returns snapshotFlow

            val refreshActiveVotingSession = mockk<RefreshActiveVotingSessionUseCase>()
            coEvery { refreshActiveVotingSession() } returns Unit

            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
            coEvery { getSelectedWalletAccount() } returns account

            val hooks =
                VotingHomeHooksImpl(
                    votingRecoveryRepository = votingRecoveryRepository,
                    votingApiRepository = votingApiRepository,
                    votingSessionStore = mockk(relaxed = true),
                    getSelectedWalletAccount = getSelectedWalletAccount,
                    refreshActiveVotingSession = refreshActiveVotingSession,
                    votingShareTrackingScheduler = mockk(relaxed = true),
                    navigationRouter = mockk(relaxed = true)
                )

            val result = hooks.recoverPendingRouteIfNeeded()

            assertFalse(result)
            coVerify(exactly = 1) { refreshActiveVotingSession() }
        }

    private fun keystoneAccount(): KeystoneAccount =
        KeystoneAccount(
            sdkAccount = AccountFixture.new(),
            unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
            orchardBalance = WalletBalanceFixture.newLong(),
            ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
            transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
            transparentBalance = Zatoshi(0),
            isSelected = true
        )

    private fun votingSessionFixture(roundIdHex: String): VotingSession =
        VotingSession(
            voteRoundId = roundIdHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            snapshotHeight = 1L,
            snapshotBlockhash = ByteArray(32) { 0x01 },
            proposalsHash = ByteArray(32) { 0x02 },
            voteEndTime = Instant.ofEpochSecond(3),
            ceremonyStart = Instant.ofEpochSecond(2),
            eaPK = ByteArray(32) { 0x03 },
            vkZkp1 = ByteArray(32) { 0x04 },
            vkZkp2 = ByteArray(32) { 0x05 },
            vkZkp3 = ByteArray(32) { 0x06 },
            ncRoot = ByteArray(32) { 0x07 },
            nullifierIMTRoot = ByteArray(32) { 0x08 },
            creator = "creator",
            title = "Round",
            description = "Round description",
            discussionUrl = null,
            proposals = emptyList(),
            status = SessionStatus.ACTIVE,
            createdAtHeight = 0
        )
}
