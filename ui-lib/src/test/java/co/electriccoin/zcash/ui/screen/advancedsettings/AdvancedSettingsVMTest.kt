package co.electriccoin.zcash.ui.screen.advancedsettings

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.migration.MigrationGate
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletRestoringStateUseCase
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.VersionInfoFixture
import co.electriccoin.zcash.ui.screen.exportvk.ExportVKArgs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Export Viewing Key entry point in Advanced Settings (MOB-1883): the row carries its own icon, sits
 * between Export private data and the tax file, opens the chooser, and - unlike the tax file - stays usable
 * while the wallet is restoring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedSettingsVMTest {
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exportViewingKeyRowSitsBetweenExportPrivateDataAndTaxFile() =
        runTest(dispatcher) {
            val vm = startedVm()

            val items = vm.state.value.items
            val index = items.indexOf(vm.exportViewingKeyRow())
            assertEquals(imageRes(R.drawable.ic_advanced_settings_viewing_key), items[index].bigIcon)
            assertEquals(stringRes(R.string.settings_exportPrivateData), items[index - 1].title)
            assertEquals(stringRes(R.string.taxExport_taxFile), items[index + 1].title)
        }

    @Test
    fun exportViewingKeyRowForwardsToTheChooser() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router = router)

            vm.exportViewingKeyRow().onClick?.invoke()
            advanceUntilIdle()

            assertEquals(listOf<Any>(ExportVKArgs), router.forwardedRoutes)
        }

    @Test
    fun exportViewingKeyRowStaysEnabledWhileRestoring() =
        runTest(dispatcher) {
            val vm = startedVm(walletRestoringState = WalletRestoringState.RESTORING)

            assertTrue(vm.exportViewingKeyRow().isEnabled)
            assertFalse(vm.taxFileRow().isEnabled)
        }

    private fun AdvancedSettingsVM.exportViewingKeyRow() =
        state.value.items.single { it.title == stringRes(R.string.exportViewingKey_settingsItem) }

    private fun AdvancedSettingsVM.taxFileRow() =
        state.value.items.single { it.title == stringRes(R.string.taxExport_taxFile) }

    private fun TestScope.startedVm(
        walletRestoringState: WalletRestoringState = WalletRestoringState.SYNCING,
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): AdvancedSettingsVM {
        val getWalletRestoringState =
            mockk<GetWalletRestoringStateUseCase> {
                every { observe() } returns MutableStateFlow(walletRestoringState)
            }
        val getWalletAccounts =
            mockk<GetWalletAccountsUseCase> {
                every { observe() } returns MutableStateFlow<List<WalletAccount>?>(emptyList())
            }
        val getVersionInfo =
            mockk<GetVersionInfoProvider> {
                every { this@mockk() } returns VersionInfoFixture.new()
            }
        val vm =
            AdvancedSettingsVM(
                getWalletRestoringState = getWalletRestoringState,
                getWalletAccounts = getWalletAccounts,
                navigationRouter = router,
                navigateToTaxExport = mockk(relaxed = true),
                navigateToWalletBackup = mockk(relaxed = true),
                getVersionInfo = getVersionInfo,
                navigateToResetWallet = mockk(relaxed = true),
                navigateToExportPrivateData = mockk(relaxed = true),
                migrationGate = mockk<MigrationGate> { coEvery { isRestartAvailable() } returns false },
                migrationNavigator = mockk(relaxed = true),
            )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private class FakeNavigationRouter : NavigationRouter {
    var backCount = 0
        private set
    val forwardedRoutes = mutableListOf<Any>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun replaceFrom(route: KClass<*>, vararg routes: Any) = Unit

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
