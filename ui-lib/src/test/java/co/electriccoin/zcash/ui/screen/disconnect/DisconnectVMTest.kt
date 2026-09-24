package co.electriccoin.zcash.ui.screen.disconnect

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.DisconnectUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The disconnect screen serves whichever hardware vendor is selected: it names and draws the one
 * being removed, and the confirmed disconnect deletes that account — which is also what drops a Ledger account's stored
 * binding — before selecting the software wallet again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectVMTest {
    private val defaultUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val sdkAccount = Account.new(defaultUuid)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theScreenNamesTheVendorItIsAboutToDisconnect() =
        runTest {
            val keystone = vm(MutableStateFlow(keystone(isSelected = true)))
            collect(keystone)
            assertShowsKeystone(assertNotNull(keystone.state.value.content))

            val ledger = vm(MutableStateFlow(ledger(isSelected = true)))
            collect(ledger)
            assertShowsLedger(assertNotNull(ledger.state.value.content))
        }

    @Test
    fun openingOnTheSoftwareWalletGoesBack() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            vm(MutableStateFlow(zashi(isSelected = true)), navigationRouter = navigationRouter)
            runCurrent()

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun switchingTheSelectedHardwareWalletUpdatesTheScreen() =
        runTest {
            val selected = MutableStateFlow<WalletAccount?>(keystone(isSelected = true))
            val vm = vm(selected)
            collect(vm)
            assertShowsKeystone(assertNotNull(vm.state.value.content))

            selected.value = ledger(isSelected = true)
            runCurrent()

            assertShowsLedger(assertNotNull(vm.state.value.content))
        }

    @Test
    fun confirmingDisconnectDeletesTheLedgerAccountAndUnwinds() =
        runTest {
            val account = ledger(isSelected = true)
            val disconnect = mockk<DisconnectUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(MutableStateFlow(account), disconnect, navigationRouter)
            collect(vm)

            assertNotNull(vm.state.value.content).disconnectButton.onClick()
            runCurrent()
            assertNotNull(
                assertNotNull(vm.state.value.content).confirmationDialog
            ).primaryAction.onClick()
            runCurrent()

            coVerify(exactly = 1) { disconnect.invoke(account) }
            verify(exactly = 1) { navigationRouter.backToRoot() }
        }

    @Test
    fun theSelectionMovingToZashiAfterDisconnectDoesNotBlankTheScreen() =
        runTest {
            val account = ledger(isSelected = true)
            val selected = MutableStateFlow<WalletAccount?>(account)
            val disconnect =
                mockk<DisconnectUseCase> {
                    coEvery { this@mockk(account) } coAnswers { selected.value = zashi(isSelected = true) }
                }
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(selected, disconnect, navigationRouter)
            collect(vm)

            assertNotNull(vm.state.value.content).disconnectButton.onClick()
            runCurrent()
            assertNotNull(
                assertNotNull(vm.state.value.content).confirmationDialog
            ).primaryAction.onClick()
            runCurrent()

            assertShowsLedger(assertNotNull(vm.state.value.content))
            verify(exactly = 1) { navigationRouter.backToRoot() }
            verify(exactly = 0) { navigationRouter.back() }
        }

    @Test
    fun theDisconnectUseCaseDeletesTheAccountAndSelectsTheSoftwareWallet() =
        runTest {
            val account = ledger()
            val zashi = zashi()
            val accountDataSource =
                mockk<AccountDataSource>(relaxed = true) {
                    coEvery { getAllAccounts() } returns listOf(zashi, account)
                    coEvery { getZashiAccount() } returns zashi
                }
            val useCase =
                DisconnectUseCase(
                    accountDataSource = accountDataSource,
                    biometricRepository = mockk(relaxed = true),
                    migrationAppHooks = mockk(relaxed = true),
                )

            useCase(account)

            coVerify(exactly = 1) { accountDataSource.deleteAccount(account) }
            coVerify(exactly = 1) { accountDataSource.selectAccount(zashi) }
        }

    private fun vm(
        selected: MutableStateFlow<WalletAccount?>,
        disconnect: DisconnectUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = DisconnectVM(
        observeSelectedWalletAccount =
            mockk {
                every { this@mockk() } returns selected
                every { require() } returns selected.filterNotNull()
            },
        disconnect = disconnect,
        navigationRouter = navigationRouter,
        errorStateMapper = ErrorMapperUseCase(sendEmail = mockk(relaxed = true)),
    )

    private fun zashi(isSelected: Boolean = false) =
        ZashiAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun keystone(isSelected: Boolean = false) =
        KeystoneAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun ledger(isSelected: Boolean = false) =
        LedgerAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
            deviceIdentity = "tpk0-deadbeef",
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    private fun assertShowsKeystone(state: DisconnectState) {
        assertEquals(R.string.deleteKeystoneTitle, state.title.resourceId())
        assertEquals(R.string.keystoneHW, state.connectedTitle.resourceId())
        assertEquals(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone, state.icon)
        assertEquals(R.string.connectedHWInfo, state.infoText.resourceId())
    }

    private fun assertShowsLedger(state: DisconnectState) {
        assertEquals(R.string.ledger_disconnect_title, state.title.resourceId())
        assertEquals(R.string.ledgerHW, state.connectedTitle.resourceId())
        assertEquals(co.electriccoin.zcash.ui.design.R.drawable.ic_item_ledger, state.icon)
        assertEquals(R.string.connectedHWInfo_ledger, state.infoText.resourceId())
    }

    private fun TestScope.collect(vm: DisconnectVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource
}
