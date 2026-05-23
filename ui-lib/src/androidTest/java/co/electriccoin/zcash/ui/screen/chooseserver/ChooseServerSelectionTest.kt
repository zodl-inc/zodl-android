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
import co.electriccoin.zcash.ui.common.component.EndpointTextFieldInnerState
import co.electriccoin.zcash.ui.common.component.ZashiEndpointTextFieldParser
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.usecase.GetAutomaticEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshFastestServersUseCase
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.design.component.InnerTextFieldState
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.MockSynchronizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        runBlocking {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val lightWalletEndpointProvider = LightWalletEndpointProvider(application)
            val currentEndpoint = lightWalletEndpointProvider.getEndpoints().last()
            val persistableWalletProvider = FakePersistableWalletProvider(currentEndpoint)
            val isServerSelectionAutomaticProvider = FakeIsServerSelectionAutomaticProvider(initial = null)
            val walletRepository = FakeWalletRepository(currentEndpoint)
            val viewModel =
                createViewModel(
                    application,
                    lightWalletEndpointProvider,
                    persistableWalletProvider,
                    isServerSelectionAutomaticProvider,
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

            withTimeout(STATE_TIMEOUT_MILLIS) {
                isServerSelectionAutomaticProvider.observe().first { it == false }
            }
            assertEquals(false, isServerSelectionAutomaticProvider.get())
            assertEquals(currentEndpoint, walletRepository.updatedEndpoint)
        }

    @Test
    @SmallTest
    fun savingServerSelectionDisablesAndIgnoresSelectionChanges() =
        runBlocking {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val lightWalletEndpointProvider = LightWalletEndpointProvider(application)
            val currentEndpoint = lightWalletEndpointProvider.getEndpoints().last()
            val persistableWalletProvider = FakePersistableWalletProvider(currentEndpoint)
            val isServerSelectionAutomaticProvider = FakeIsServerSelectionAutomaticProvider(initial = null)
            val continueEndpointUpdate = CompletableDeferred<Unit>()
            val walletRepository = FakeWalletRepository(currentEndpoint, continueEndpointUpdate)
            val viewModel =
                createViewModel(
                    application,
                    lightWalletEndpointProvider,
                    persistableWalletProvider,
                    isServerSelectionAutomaticProvider,
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

                assertFalse(savingState.fastest.retryButton.isEnabled)
                val customServer = savingState.customServer()
                assertFalse(customServer.newServerTextFieldState.isEnabled)
                assertFalse(customServer.isExpanded)

                savingState.connectionMode.automatic.onClick()
                savingState.fastest.retryButton.onClick()
                savingState.defaultServer().radioButtonState.onClick()
                customServer.radioButtonState.onClick()
                customServer.newServerTextFieldState.onValueChange(
                    EndpointTextFieldInnerState(
                        innerTextFieldState = InnerTextFieldState(value = stringRes("custom.example.com:443")),
                        endpoint = ZashiEndpointTextFieldParser.toEndpointOrNull("custom.example.com:443"),
                        lastValidEndpoint = ZashiEndpointTextFieldParser.toEndpointOrNull("custom.example.com:443"),
                    )
                )

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
                assertEquals(
                    "",
                    stillCustomServer.newServerTextFieldState.innerState.innerTextFieldState.value
                        .getString(application)
                )
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

    companion object {
        private const val STATE_TIMEOUT_MILLIS = 2_000L
    }
}

private fun createViewModel(
    application: Application,
    lightWalletEndpointProvider: LightWalletEndpointProvider,
    persistableWalletProvider: PersistableWalletProvider,
    isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    walletRepository: WalletRepository,
): ChooseServerVM {
    val getSelectedEndpoint = GetSelectedEndpointUseCase(persistableWalletProvider)
    val getAutomaticEndpoint =
        GetAutomaticEndpointUseCase(
            walletRepository = walletRepository,
            lightWalletEndpointProvider = lightWalletEndpointProvider,
            getSelectedEndpoint = getSelectedEndpoint,
        )
    return ChooseServerVM(
        application = application,
        observeFastestServers = ObserveFastestServersUseCase(walletRepository),
        getSelectedEndpoint = getSelectedEndpoint,
        isServerSelectionAutomaticProvider = isServerSelectionAutomaticProvider,
        lightWalletEndpointProvider = lightWalletEndpointProvider,
        refreshFastestServersUseCase = RefreshFastestServersUseCase(walletRepository),
        persistServerSelection =
            PersistServerSelectionUseCase(
                application = application,
                walletRepository = walletRepository,
                synchronizerProvider = FakeSynchronizerProvider(),
                isServerSelectionAutomaticProvider = isServerSelectionAutomaticProvider,
                getAutomaticEndpoint = getAutomaticEndpoint,
            ),
        navigationRouter = FakeNavigationRouter,
        getAutomaticEndpoint = getAutomaticEndpoint
    )
}

private fun ChooseServerState.customServer() = other.servers.filterIsInstance<ServerState.Custom>().first()

private fun ChooseServerState.defaultServer() = other.servers.filterIsInstance<ServerState.Default>().first()

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

private class FakeIsServerSelectionAutomaticProvider(
    initial: Boolean?
) : IsServerSelectionAutomaticProvider {
    private val state = MutableStateFlow(initial)

    override suspend fun get(): Boolean? = state.value

    override suspend fun store(amount: Boolean) {
        state.value = amount
    }

    override fun observe(): Flow<Boolean?> = state

    override suspend fun clear() {
        state.value = null
    }
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

    override suspend fun getVotingWalletDbPath(): String = ""

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
