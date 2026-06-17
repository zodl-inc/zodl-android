package co.electriccoin.zcash.ui.common.usecase

import android.app.Application
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Persisting the user's server selection (MOB-1144): a manual selection is validated before it is
 * stored (invalid/running validation aborts without persisting); a valid manual selection stores the
 * flag as manual and pins the endpoint; automatic stores the flag as automatic and the chosen
 * fastest endpoint.
 */
class PersistServerSelectionUseCaseTest {
    private val endpoint = LightWalletEndpoint(host = "manual.example.com", port = 443, isSecure = true)
    private val fastest = LightWalletEndpoint(host = "fastest.example.com", port = 443, isSecure = true)

    private val synchronizer = mockk<Synchronizer>()
    private val synchronizerProvider = mockk<SynchronizerProvider>()
    private val walletRepository = mockk<WalletRepository>(relaxed = true)
    private val isAutomaticProvider = mockk<IsServerSelectionAutomaticProvider>(relaxed = true)
    private val getAutomaticEndpoint = mockk<GetAutomaticEndpointUseCase>()
    private val useCase =
        PersistServerSelectionUseCase(
            application = mockk<Application>(),
            walletRepository = walletRepository,
            synchronizerProvider = synchronizerProvider,
            isServerSelectionAutomaticProvider = isAutomaticProvider,
            getAutomaticEndpoint = getAutomaticEndpoint
        )

    @BeforeTest
    fun setUp() {
        coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
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

            useCase.persistAutomatic()

            coVerify(exactly = 1) { isAutomaticProvider.store(true) }
            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(fastest) }
        }
}
