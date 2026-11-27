package co.electriccoin.zcash.ui.screen.accountlist.options

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.disconnect.DisconnectArgs
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The "Wallet Options" sheet of one hardware wallet: it names the vendor to unpair, hides itself
 * before it hands over to the disconnect screen for that very account, ignores a second tap or a
 * back press during that hand-over, and goes back when the account is not a hardware wallet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletOptionsVMTest {
    private val zodl = zodl()
    private val keystone = keystone()
    private val ledger = ledger()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theDisconnectOptionNamesTheVendorOfTheAccount() =
        runTest {
            val keystoneVm = vm(keystone)
            collect(keystoneVm)
            val keystoneOption = assertNotNull(keystoneVm.state.value).options.single()
            assertEquals(stringRes(R.string.hwWallet_options_disconnect_title), keystoneOption.title)
            assertEquals(stringRes(R.string.hwWallet_options_disconnect_keystone), keystoneOption.subtitle)

            val ledgerVm = vm(ledger)
            collect(ledgerVm)
            val ledgerOption = assertNotNull(ledgerVm.state.value).options.single()
            assertEquals(stringRes(R.string.hwWallet_options_disconnect_ledger), ledgerOption.subtitle)
        }

    @Test
    fun disconnectHidesTheSheetThenReplacesItWithTheDisconnectScreenForThatAccount() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(ledger, navigationRouter = navigationRouter)
            collect(vm)

            assertNotNull(vm.state.value).options.single().onClick()
            runCurrent()
            assertNull(vm.state.value)
            verify(exactly = 0) { navigationRouter.replace(any()) }

            advanceTimeBy(SHEET_HIDE_DELAY_MS + 1)

            verify(exactly = 1) {
                navigationRouter.replace(DisconnectArgs(accountKeyId = ledger.sdkAccount.accountUuid.toStorageKeyId()))
            }
        }

    @Test
    fun aSecondTapOrBackDuringTheHandOverDoesNothing() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(keystone, navigationRouter = navigationRouter)
            collect(vm)

            val state = assertNotNull(vm.state.value)
            state.options.single().onClick()
            state.options.single().onClick()
            state.onBack()
            advanceTimeBy(SHEET_HIDE_DELAY_MS + 1)

            verify(exactly = 1) { navigationRouter.replace(any()) }
            verify(exactly = 0) { navigationRouter.back() }
        }

    @Test
    fun dismissGoesBack() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(keystone, navigationRouter = navigationRouter)
            collect(vm)

            assertNotNull(vm.state.value).dismissButton.onClick()

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun anAccountThatIsNotAHardwareWalletGoesBack() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(zodl, navigationRouter = navigationRouter)
            collect(vm)
            runCurrent()

            assertNull(vm.state.value)
            verify(exactly = 1) { navigationRouter.back() }
        }

    private fun vm(
        target: WalletAccount,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ): WalletOptionsVM {
        val accounts = MutableStateFlow<List<WalletAccount>?>(listOf(zodl, keystone, ledger))
        return WalletOptionsVM(
            args = WalletOptionsArgs(accountKeyId = target.sdkAccount.accountUuid.toStorageKeyId()),
            getWalletAccounts =
                mockk<GetWalletAccountsUseCase> {
                    every { observe() } returns accounts
                    every { require() } returns accounts.filterNotNull()
                },
            navigationRouter = navigationRouter,
        )
    }

    private fun sdkAccount(id: Int) = Account.new(AccountUuid.new(ByteArray(16) { (it + id * 16).toByte() }))

    private fun zodl() =
        ZodlAccount(
            sdkAccount = sdkAccount(1),
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun keystone() =
        KeystoneAccount(
            sdkAccount = sdkAccount(2),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
        )

    private fun ledger() =
        LedgerAccount(
            sdkAccount = sdkAccount(3),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            deviceIdentity = null,
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    private fun TestScope.collect(vm: WalletOptionsVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private companion object {
        const val SHEET_HIDE_DELAY_MS = 350L
    }
}
