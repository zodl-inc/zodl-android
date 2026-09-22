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
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * The disconnect screen serves either hardware vendor: it names the one being removed, and the
 * confirmed disconnect deletes that account — which is also what drops a Ledger account's stored
 * binding — before selecting the software wallet again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectVMTest {
    private val sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() }))

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
            val keystone = vm(keystone())
            collect(keystone)
            val keystoneState = assertNotNull(keystone.state.value.content)
            assertEquals(R.string.deleteKeystoneTitle, keystoneState.title.resourceId())
            assertEquals(R.string.keystoneHW, keystoneState.connectedTitle.resourceId())

            val ledger = vm(ledger())
            collect(ledger)
            val ledgerState = assertNotNull(ledger.state.value.content)
            assertEquals(R.string.ledger_disconnect_title, ledgerState.title.resourceId())
            assertEquals(R.string.ledgerHW, ledgerState.connectedTitle.resourceId())
        }

    @Test
    fun confirmingDisconnectDeletesTheLedgerAccountAndUnwinds() =
        runTest {
            val account = ledger()
            val disconnect = useCase(account)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(account, disconnect, navigationRouter)
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

    @Test
    fun theSelectedHardwareAccountIsTheOneOffered() =
        runTest {
            val keystone = keystone()
            val ledger = ledger(isSelected = true)
            val useCase =
                DisconnectUseCase(
                    accountDataSource =
                        mockk<AccountDataSource>(relaxed = true) {
                            coEvery { getAllAccounts() } returns listOf(zashi(), keystone, ledger)
                        },
                    biometricRepository = mockk(relaxed = true),
                    migrationAppHooks = mockk(relaxed = true),
                )

            assertEquals(ledger, useCase.getHardwareWalletAccount())
        }

    @Test
    fun aLoneKeystoneIsStillOfferedWhenTheSoftwareWalletIsSelected() =
        runTest {
            val keystone = keystone()
            val useCase =
                DisconnectUseCase(
                    accountDataSource =
                        mockk<AccountDataSource>(relaxed = true) {
                            coEvery { getAllAccounts() } returns listOf(zashi(isSelected = true), keystone)
                        },
                    biometricRepository = mockk(relaxed = true),
                    migrationAppHooks = mockk(relaxed = true),
                )

            assertEquals(keystone, useCase.getHardwareWalletAccount())
        }

    private fun useCase(account: WalletAccount) =
        mockk<DisconnectUseCase>(relaxed = true) {
            coEvery { getHardwareWalletAccount() } returns account
        }

    private fun vm(
        account: WalletAccount,
        disconnect: DisconnectUseCase = useCase(account),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = DisconnectVM(
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

    private fun TestScope.collect(vm: DisconnectVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource
}
