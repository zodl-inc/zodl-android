package co.electriccoin.zcash.ui.common.usecase

import android.app.Application
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsPublicConnectionProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionMutex
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Persisting the user's server selection (MOB-1144): a manual selection is validated before it is
 * stored (invalid/running validation aborts without persisting); a valid manual selection stores the
 * flag as manual and pins the endpoint; automatic stores the flag as automatic and the chosen
 * fastest endpoint.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersistServerSelectionUseCaseTest {
    private val endpoint = LightWalletEndpoint(host = "manual.example.com", port = 443, isSecure = true)
    private val fastest = LightWalletEndpoint(host = "fastest.example.com", port = 443, isSecure = true)
    private val gateway = LightWalletEndpoint(host = "ohttp-gateway.example", port = 443, isSecure = true)

    private val synchronizer = mockk<Synchronizer>()
    private val synchronizerProvider = mockk<SynchronizerProvider>()
    private val walletRepository = mockk<WalletRepository>(relaxed = true)
    private val isAutomaticProvider = mockk<IsServerSelectionAutomaticProvider>(relaxed = true)
    private val isOhttpFallbackActiveProvider = mockk<IsOhttpFallbackActiveProvider>(relaxed = true)
    private val getAutomaticEndpoint = mockk<GetAutomaticEndpointUseCase>()
    private val isPublicProvider = mockk<IsPublicConnectionProvider>(relaxed = true)
    private val lightWalletEndpointProvider = mockk<LightWalletEndpointProvider>()
    private val serverSelectionMutex = ServerSelectionMutex()
    private val useCase =
        PersistServerSelectionUseCase(
            application = mockk<Application>(),
            walletRepository = walletRepository,
            synchronizerProvider = synchronizerProvider,
            isServerSelectionAutomaticProvider = isAutomaticProvider,
            isOhttpFallbackActiveProvider = isOhttpFallbackActiveProvider,
            getAutomaticEndpoint = getAutomaticEndpoint,
            isPublicConnectionProvider = isPublicProvider,
            lightWalletEndpointProvider = lightWalletEndpointProvider,
            serverSelectionMutex = serverSelectionMutex,
        )

    @BeforeTest
    fun setUp() {
        coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
        every { lightWalletEndpointProvider.isOhttpGateway(any()) } answers {
            firstArg<LightWalletEndpoint>() == gateway
        }
    }

    @Test
    fun manualValidEndpointPersistsManualSelection() =
        runTest {
            coEvery { synchronizer.validateServerEndpoint(any(), endpoint) } returns ServerValidation.Valid

            useCase.persistManual(endpoint)

            coVerify(exactly = 1) { isAutomaticProvider.store(false) }
            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(endpoint) }
        }

    @Test
    fun manualInvalidEndpointThrowsWithReasonAndDoesNotPersist() =
        runTest {
            coEvery { synchronizer.validateServerEndpoint(any(), endpoint) } returns
                ServerValidation.InValid(IllegalArgumentException("bad host"))

            val exception = assertFailsWith<PersistEndpointException> { useCase.persistManual(endpoint) }

            assertEquals("bad host", exception.message)
            coVerify(exactly = 0) { isAutomaticProvider.store(any()) }
            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }
        }

    @Test
    fun manualWhileValidationRunningThrowsAndDoesNotPersist() =
        runTest {
            coEvery { synchronizer.validateServerEndpoint(any(), endpoint) } returns ServerValidation.Running

            assertFailsWith<PersistEndpointException> { useCase.persistManual(endpoint) }

            coVerify(exactly = 0) { isAutomaticProvider.store(any()) }
            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }
        }

    @Test
    fun automaticPersistsAutomaticFlagAndChosenEndpoint() =
        runTest {
            coEvery { getAutomaticEndpoint() } returns fastest

            useCase.persistPrivate()

            coVerify(exactly = 1) { isAutomaticProvider.store(true) }
            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(fastest) }
        }

    @Test
    fun privateClearsPublicFlag() =
        runTest {
            coEvery { getAutomaticEndpoint() } returns fastest

            useCase.persistPrivate()

            coVerify(exactly = 1) { isPublicProvider.store(false) }
        }

    @Test
    fun publicPersistsPublicFlagAutomaticFlagAndFirstClearnetEndpoint() =
        runTest {
            io.mockk.every { lightWalletEndpointProvider.getFallbackEndpoints() } returns listOf(endpoint, fastest)

            useCase.persistPublic()

            coVerify(exactly = 1) { isPublicProvider.store(true) }
            coVerify(exactly = 1) { isAutomaticProvider.store(true) }
            coVerify(exactly = 1) { isOhttpFallbackActiveProvider.store(false) }
            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(endpoint) }
        }

    @Test
    fun manualClearsPublicFlag() =
        runTest {
            coEvery { synchronizer.validateServerEndpoint(any(), endpoint) } returns ServerValidation.Valid

            useCase.persistManual(endpoint)

            coVerify(exactly = 1) { isPublicProvider.store(false) }
        }

    @Test
    fun savingWaitsForAnAutomaticSwitchThatIsWritingTheSelection() =
        runTest {
            every { lightWalletEndpointProvider.getFallbackEndpoints() } returns listOf(endpoint)
            val switchFinishes = CompletableDeferred<Unit>()
            backgroundScope.launch { serverSelectionMutex.withLock { switchFinishes.await() } }
            runCurrent()

            val save = backgroundScope.launch { useCase.persistPublic() }
            runCurrent()
            coVerify(exactly = 0) { isPublicProvider.store(any()) }
            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }

            switchFinishes.complete(Unit)
            runCurrent()

            coVerify(exactly = 1) { isPublicProvider.store(true) }
            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(endpoint) }
            assertEquals(true, save.isCompleted)
        }

    /**
     * The caller (a sheet's ViewModel) can be cancelled while the selection is being written. The flags
     * must not stay switched with the endpoint left behind.
     */
    @Test
    fun aCancelledSwitchToPublicStillWritesTheEndpoint() =
        runTest {
            every { lightWalletEndpointProvider.getFallbackEndpoints() } returns listOf(endpoint)
            val flagWriteRunning = CompletableDeferred<Unit>()
            val flagWriteReleased = CompletableDeferred<Unit>()
            coEvery { isPublicProvider.store(true) } coAnswers {
                flagWriteRunning.complete(Unit)
                flagWriteReleased.await()
            }

            val switch = launch { useCase.persistPublic(afterPrivateUnavailable = true) }
            flagWriteRunning.await()
            switch.cancel()
            flagWriteReleased.complete(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(endpoint) }
        }

    /** Leaving the Server screen while a validated manual pin is being written must not strand the old endpoint. */
    @Test
    fun aCancelledManualSaveStillWritesTheEndpoint() =
        runTest {
            coEvery { synchronizer.validateServerEndpoint(any(), endpoint) } returns ServerValidation.Valid
            val flagWriteRunning = CompletableDeferred<Unit>()
            val flagWriteReleased = CompletableDeferred<Unit>()
            coEvery { isPublicProvider.store(false) } coAnswers {
                flagWriteRunning.complete(Unit)
                flagWriteReleased.await()
            }

            val save = launch { useCase.persistManual(endpoint) }
            flagWriteRunning.await()
            save.cancel()
            flagWriteReleased.complete(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(endpoint) }
        }

    @Test
    fun manualPinOfTheGatewayIsPrivate() =
        runTest {
            coEvery { getAutomaticEndpoint() } returns gateway

            useCase.persistManual(gateway)

            coVerify(exactly = 1) { isAutomaticProvider.store(true) }
            coVerify(exactly = 0) { isAutomaticProvider.store(false) }
            coVerify(exactly = 1) { isPublicProvider.store(false) }
            coVerify(exactly = 0) { synchronizer.validateServerEndpoint(any(), any()) }
        }

    @Test
    fun publicAfterAnOutageMarksTheFallbackAndADeliberatePublicDoesNot() =
        runTest {
            every { lightWalletEndpointProvider.getFallbackEndpoints() } returns listOf(endpoint, fastest)

            useCase.persistPublic(afterPrivateUnavailable = true)
            coVerify(exactly = 1) { isOhttpFallbackActiveProvider.store(true) }

            useCase.persistPublic()
            coVerify(exactly = 1) { isOhttpFallbackActiveProvider.store(false) }
        }

    @Test
    fun aPublicSwitchAbortedUnderTheLockStoresNothing() =
        runTest {
            every { lightWalletEndpointProvider.getFallbackEndpoints() } returns listOf(endpoint, fastest)

            assertFalse(useCase.persistPublic(afterPrivateUnavailable = true, abortIf = { true }))
            coVerify(exactly = 0) { isOhttpFallbackActiveProvider.store(any()) }
            coVerify(exactly = 0) { isPublicProvider.store(any()) }
            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }

            assertTrue(useCase.persistPublic(afterPrivateUnavailable = true, abortIf = { false }))
            coVerify(exactly = 1) { isOhttpFallbackActiveProvider.store(true) }
        }

    @Test
    fun publicStartsFromTheFastestServerOfTheSpeedTest() =
        runTest {
            every { lightWalletEndpointProvider.getFallbackEndpoints() } returns listOf(endpoint, fastest)

            useCase.persistPublic(fastest = fastest)

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(fastest) }
        }

    @Test
    fun publicIgnoresAFastestServerThatIsNotAPublicServer() =
        runTest {
            every { lightWalletEndpointProvider.getFallbackEndpoints() } returns listOf(endpoint)

            useCase.persistPublic(fastest = gateway)

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(endpoint) }
        }
}
