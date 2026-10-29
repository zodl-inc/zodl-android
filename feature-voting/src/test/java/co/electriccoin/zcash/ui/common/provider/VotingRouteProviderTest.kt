package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.model.voting.VotingTorLease
import co.electriccoin.zcash.ui.common.model.voting.VotingRouteDecision
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * ZIP #10 "Voting routing": one route decision per voting operation. Tor on takes a lease and fails
 * closed without one; Tor off never asks for a lease; the custom-server flag is captured once.
 */
class VotingRouteProviderTest {
    private val synchronizer = mockk<Synchronizer>()
    private val synchronizerProvider =
        mockk<SynchronizerProvider> { coEvery { getSynchronizer() } returns synchronizer }
    private val httpClientProvider = mockk<HttpClientProvider>()
    private val chainConfig = mockk<VotingChainConfigRepository>()

    private val provider = VotingRouteProviderImpl(synchronizerProvider, httpClientProvider, chainConfig)

    private val defaultConfig = VotingChainConfigState()
    private val customConfig =
        VotingChainConfigState(
            selected = VotingChainConfigSelection.Custom("custom"),
            customChains = listOf(VotingCustomChainConfig("custom", "Custom", "https://custom.example/config.json"))
        )

    @Test
    fun torOffNeverAsksForALease() =
        runTest {
            coEvery { httpClientProvider.isTorEnabled() } returns false
            coEvery { chainConfig.get() } returns defaultConfig

            val route = provider.acquire()

            assertEquals(VotingRouteDecision(isTor = false, isCustomServer = false), route.decision)
            assertNull(route.torLease)
            coVerify(exactly = 0) { synchronizer.acquireVotingTorLease() }
        }

    @Test
    fun torOnCarriesTheLease() =
        runTest {
            val lease = mockk<VotingTorLease>(relaxed = true)
            coEvery { httpClientProvider.isTorEnabled() } returns true
            coEvery { chainConfig.get() } returns defaultConfig
            coEvery { synchronizer.acquireVotingTorLease() } returns lease

            val route = provider.acquire()

            assertEquals(VotingRouteDecision(isTor = true, isCustomServer = false), route.decision)
            assertSame(lease, route.torLease)
        }

    @Test
    fun torOnWithoutALeaseFailsClosed() =
        runTest {
            coEvery { httpClientProvider.isTorEnabled() } returns true
            coEvery { chainConfig.get() } returns defaultConfig
            coEvery { synchronizer.acquireVotingTorLease() } throws TorUnavailableException()

            assertFailsWith<TorUnavailableException> { provider.acquire() }
        }

    @Test
    fun theCustomFlagIsCapturedAtAcquisition() =
        runTest {
            coEvery { httpClientProvider.isTorEnabled() } returns false
            coEvery { chainConfig.get() } returns customConfig

            val route = provider.acquire()
            coEvery { chainConfig.get() } returns defaultConfig
            coEvery { httpClientProvider.isTorEnabled() } returns true

            assertEquals(VotingRouteDecision(isTor = false, isCustomServer = true), route.decision)
            assertNull(route.torLease)
        }

    @Test
    fun releaseReleasesTheLeaseOnce() =
        runTest {
            val lease = mockk<VotingTorLease>(relaxed = true)
            coEvery { httpClientProvider.isTorEnabled() } returns true
            coEvery { chainConfig.get() } returns defaultConfig
            coEvery { synchronizer.acquireVotingTorLease() } returns lease

            provider.acquire().release()

            coVerify(exactly = 1) { lease.release() }
        }
}
