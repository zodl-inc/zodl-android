package co.electriccoin.zcash.ui.screen.chooseserver

import android.app.Application
import androidx.navigation.NavBackStackEntry
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.ConnectionMode
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.GetServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateEndpointUseCase
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.fixture.MockSynchronizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChooseServerSelectionTest {
    @Test
    @SmallTest
    fun automaticToManualSavePinsCurrentEndpointWithoutEndpointTap() =
        runTest {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val lightWalletEndpointProvider = LightWalletEndpointProvider(application)
            val currentEndpoint = lightWalletEndpointProvider.getEndpoints().last()
            val persistableWalletProvider = FakePersistableWalletProvider(currentEndpoint)
            val serverSelectionProvider = FakeServerSelectionProvider(ServerSelection.automatic())
            val walletRepository = FakeWalletRepository(currentEndpoint)
            val viewModel =
                createViewModel(
                    application,
                    lightWalletEndpointProvider,
                    persistableWalletProvider,
                    serverSelectionProvider,
                    walletRepository
                )

            val initialState =
                withTimeout(STATE_TIMEOUT_MILLIS) {
                    viewModel.state.filterNotNull().first()
                }
            assertFalse(initialState.connectionMode.isManualSelected)

            initialState.connectionMode.manual.onClick()

            val manualState =
                withTimeout(STATE_TIMEOUT_MILLIS) {
                    viewModel.state
                        .filterNotNull()
                        .first { it.connectionMode.isManualSelected && it.saveButton.isEnabled }
                }
            assertTrue(
                manualState.fastest.servers.any {
                    it.radioButtonState.isChecked && it.badge != null
                }
            )

            manualState.saveButton.onClick()

            val persistedSelection =
                withTimeout(STATE_TIMEOUT_MILLIS) {
                    serverSelectionProvider.serverSelection
                        .filterNotNull()
                        .first { it.mode == ConnectionMode.MANUAL }
                }
            assertEquals(ConnectionMode.MANUAL, persistedSelection.mode)
            assertEquals(currentEndpoint, persistedSelection.endpoint)
            assertFalse(persistedSelection.isCustom)
            assertEquals(currentEndpoint, walletRepository.updatedEndpoint)
        }

    @Test
    @SmallTest
    fun savingServerSelectionDisablesAndIgnoresSelectionChanges() =
        runTest {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val lightWalletEndpointProvider = LightWalletEndpointProvider(application)
            val currentEndpoint = lightWalletEndpointProvider.getEndpoints().last()
            val persistableWalletProvider = FakePersistableWalletProvider(currentEndpoint)
            val serverSelectionProvider = FakeServerSelectionProvider(ServerSelection.automatic())
            val continueEndpointUpdate = CompletableDeferred<Unit>()
            val walletRepository = FakeWalletRepository(currentEndpoint, continueEndpointUpdate)
            val viewModel =
                createViewModel(
                    application,
                    lightWalletEndpointProvider,
                    persistableWalletProvider,
                    serverSelectionProvider,
                    walletRepository
                )

            try {
                val initialState =
                    withTimeout(STATE_TIMEOUT_MILLIS) {
                        viewModel.state.filterNotNull().first()
                    }
                assertFalse(initialState.connectionMode.isManualSelected)

                initialState.connectionMode.manual.onClick()

                val manualState =
                    withTimeout(STATE_TIMEOUT_MILLIS) {
                        viewModel.state
                            .filterNotNull()
                            .first { it.connectionMode.isManualSelected && it.saveButton.isEnabled }
                    }

                manualState.saveButton.onClick()
                withTimeout(STATE_TIMEOUT_MILLIS) {
                    walletRepository.endpointUpdateStarted.await()
                }

                val savingState =
                    withTimeout(STATE_TIMEOUT_MILLIS) {
                        viewModel.state
                            .filterNotNull()
                            .first { it.saveButton.isLoading }
                    }

                assertFalse(savingState.connectionMode.automatic.isEnabled)
                assertFalse(savingState.connectionMode.manual.isEnabled)
                assertFalse(savingState.fastest.retryButton.isEnabled)
                assertTrue(savingState.fastest.servers.all { !it.radioButtonState.isEnabled })
                assertTrue(savingState.other.servers.all { !it.isEnabled })

                val customServer = savingState.customServer()
                assertFalse(customServer.isExpanded)

                savingState.connectionMode.automatic.onClick()
                savingState.fastest.retryButton.onClick()
                savingState.defaultServer().radioButtonState.onClick()
                customServer.radioButtonState.onClick()
                customServer.newServerTextFieldState.onValueChange("custom.example.com:443")

                val stillSavingState =
                    withTimeout(STATE_TIMEOUT_MILLIS) {
                        viewModel.state
                            .filterNotNull()
                            .first { it.saveButton.isLoading }
                    }

                assertTrue(stillSavingState.connectionMode.isManualSelected)
                assertEquals(0, walletRepository.refreshCount)

                val stillCustomServer = stillSavingState.customServer()
                assertFalse(stillCustomServer.isExpanded)
                assertEquals("", stillCustomServer.newServerTextFieldState.value.getString(application))
            } finally {
                continueEndpointUpdate.complete(Unit)
            }

            val completedState =
                withTimeout(STATE_TIMEOUT_MILLIS) {
                    viewModel.state
                        .filterNotNull()
                        .first { it.connectionMode.isManualSelected && !it.saveButton.isLoading }
                }
            assertTrue(completedState.connectionMode.isManualSelected)
            assertEquals(currentEndpoint, walletRepository.updatedEndpoint)
        }

    @Test
    @SmallTest
    fun currentKnownEndpointPinsAsManualWhenNoEndpointWasTapped() {
        val endpoint = knownEndpoints[1]

        val selection =
            endpoint.toCurrentManualServerSelection(
                persistedSelection = ServerSelection.automatic(),
                availableServers = knownEndpoints
            )

        assertEquals(ConnectionMode.MANUAL, selection.mode)
        assertEquals(endpoint, selection.endpoint)
        assertFalse(selection.isCustom)
    }

    @Test
    @SmallTest
    fun currentUnknownEndpointPinsAsCustomWhenNoEndpointWasTapped() {
        val endpoint = LightWalletEndpoint(host = "custom.example.com", port = 9067, isSecure = true)

        val selection =
            endpoint.toCurrentManualServerSelection(
                persistedSelection = ServerSelection.automatic(),
                availableServers = knownEndpoints
            )

        assertEquals(ConnectionMode.MANUAL, selection.mode)
        assertEquals(endpoint, selection.endpoint)
        assertTrue(selection.isCustom)
    }

    companion object {
        private const val STATE_TIMEOUT_MILLIS = 2_000L

        private val knownEndpoints =
            listOf(
                LightWalletEndpoint(host = "zec.rocks", port = 443, isSecure = true),
                LightWalletEndpoint(host = "eu.zec.rocks", port = 443, isSecure = true)
            )
    }
}

private fun createViewModel(
    application: Application,
    lightWalletEndpointProvider: LightWalletEndpointProvider,
    persistableWalletProvider: PersistableWalletProvider,
    serverSelectionProvider: ServerSelectionProvider,
    walletRepository: WalletRepository,
): ChooseServerVM {
    val getSelectedEndpoint = GetSelectedEndpointUseCase(persistableWalletProvider)
    return ChooseServerVM(
        application = application,
        observeFastestServers = ObserveFastestServersUseCase(walletRepository),
        getSelectedEndpoint = getSelectedEndpoint,
        getServerSelection = GetServerSelectionUseCase(serverSelectionProvider),
        lightWalletEndpointProvider = lightWalletEndpointProvider,
        refreshFastestServersUseCase = RefreshFastestServersUseCase(walletRepository),
        persistServerSelection =
            PersistServerSelectionUseCase(
                application = application,
                walletRepository = walletRepository,
                synchronizerProvider = FakeSynchronizerProvider(),
                lightWalletEndpointProvider = lightWalletEndpointProvider,
                serverSelectionProvider = serverSelectionProvider,
                getSelectedEndpoint = getSelectedEndpoint
            ),
        validateEndpoint = ValidateEndpointUseCase(),
        navigationRouter = FakeNavigationRouter
    )
}

private fun ChooseServerState.customServer() = other.servers.filterIsInstance<ServerState.Custom>().first()

private fun ChooseServerState.defaultServer() = other.servers.filterIsInstance<ServerState.Default>().first()

private val ServerState.isEnabled: Boolean
    get() =
        when (this) {
            is ServerState.Custom -> radioButtonState.isEnabled && newServerTextFieldState.isEnabled
            is ServerState.Default -> radioButtonState.isEnabled
        }

private class FakePersistableWalletProvider(
    endpoint: LightWalletEndpoint
) : PersistableWalletProvider {
    private val mutablePersistableWallet =
        MutableStateFlow(
            PersistableWallet(
                network = ZcashNetwork.Mainnet,
                endpoint = endpoint,
                birthday = null,
                seedPhrase = SeedPhrase(List(SeedPhrase.SEED_PHRASE_SIZE) { "abandon" }),
                walletInitMode = WalletInitMode.ExistingWallet
            )
        )

    override val persistableWallet: Flow<PersistableWallet?> = mutablePersistableWallet

    override suspend fun store(persistableWallet: PersistableWallet) {
        mutablePersistableWallet.value = persistableWallet
    }

    override suspend fun getPersistableWallet() = mutablePersistableWallet.value

    override suspend fun requirePersistableWallet() = checkNotNull(mutablePersistableWallet.value)
}

private class FakeServerSelectionProvider(
    initialSelection: ServerSelection?
) : ServerSelectionProvider {
    private val mutableServerSelection = MutableStateFlow(initialSelection)

    override val serverSelection: Flow<ServerSelection?> = mutableServerSelection

    override suspend fun store(serverSelection: ServerSelection) {
        mutableServerSelection.value = serverSelection
    }

    override suspend fun getServerSelection() = mutableServerSelection.value
}

private class FakeWalletRepository(
    fastestEndpoint: LightWalletEndpoint,
    private val continueEndpointUpdate: CompletableDeferred<Unit>? = null,
) : WalletRepository {
    override val secretState = MutableStateFlow(SecretState.NONE)
    override val fastestEndpoints = MutableStateFlow(FastestServersState(listOf(fastestEndpoint), false))
    override val walletRestoringState = MutableStateFlow(WalletRestoringState.NONE)

    val endpointUpdateStarted = CompletableDeferred<Unit>()

    var updatedEndpoint: LightWalletEndpoint? = null
        private set

    var refreshCount = 0
        private set

    override fun createNewWallet() = Unit

    override fun restoreWallet(
        network: ZcashNetwork,
        seedPhrase: SeedPhrase,
        birthday: BlockHeight
    ) = Unit

    override suspend fun updateWalletEndpoint(endpoint: LightWalletEndpoint) {
        updatedEndpoint = endpoint
        endpointUpdateStarted.complete(Unit)
        continueEndpointUpdate?.await()
    }

    override fun refreshFastestServers() {
        refreshCount++
    }
}

private class FakeSynchronizerProvider : SynchronizerProvider {
    override val error = MutableStateFlow<SynchronizerError?>(null)
    override val synchronizer = MutableStateFlow<Synchronizer?>(MockSynchronizer(ServerValidation.Valid))

    override suspend fun getSynchronizer(): Synchronizer = checkNotNull(synchronizer.value)

    override fun resetSynchronizer() = Unit
}

private object FakeNavigationRouter : NavigationRouter {
    override fun forward(vararg routes: Any) = Unit

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun back() = Unit

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
