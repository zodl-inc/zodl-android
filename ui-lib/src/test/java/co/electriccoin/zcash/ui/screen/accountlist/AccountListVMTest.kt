package co.electriccoin.zcash.ui.screen.accountlist

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.screen.choosehardwarewallet.ChooseHardwareWalletArgs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The "Wallets & Hardware" sheet: one row per wallet with its vendor icon and the selected badge,
 * and a call to action that only appears while a hardware vendor is still unconnected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountListVMTest {
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
    fun eachVendorGetsItsOwnIconAndOnlyTheSelectedRowIsMarked() =
        runTest {
            val vm = vm(listOf(zashi(isSelected = false), keystone(isSelected = false), ledger(isSelected = true)))
            collect(vm)

            val items = assertNotNull(vm.state.value?.items)
            assertEquals(
                listOf(R.drawable.ic_item_zashi, R.drawable.ic_item_keystone, R.drawable.ic_item_ledger),
                items.map { it.icon }
            )
            assertEquals(listOf(false, false, true), items.map { it.isSelected })
        }

    @Test
    fun theCallToActionShowsWhileAVendorIsStillUnconnected() =
        runTest {
            listOf(
                listOf(zashi(isSelected = true)),
                listOf(zashi(isSelected = true), keystone(isSelected = false)),
                listOf(zashi(isSelected = true), ledger(isSelected = false)),
            ).forEach { accounts ->
                val vm = vm(accounts)
                collect(vm)

                assertNotNull(vm.state.value?.addWalletButton)
            }
        }

    @Test
    fun theCallToActionDisappearsOnceBothVendorsAreConnected() =
        runTest {
            val vm = vm(listOf(zashi(isSelected = true), keystone(isSelected = false), ledger(isSelected = false)))
            collect(vm)

            assertNull(vm.state.value?.addWalletButton)
        }

    @Test
    fun theCallToActionStaysHiddenWhileTheAccountsAreStillLoading() =
        runTest {
            val vm = vm(accounts = null)
            collect(vm)

            val state = assertNotNull(vm.state.value)
            assertNull(state.items)
            assertTrue(state.isLoading)
            assertNull(state.addWalletButton)
        }

    @Test
    fun theCallToActionOpensTheVendorPicker() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(listOf(zashi(isSelected = true)), navigationRouter = navigationRouter)
            collect(vm)

            assertNotNull(vm.state.value?.addWalletButton).onClick()

            verify(exactly = 1) { navigationRouter.forward(ChooseHardwareWalletArgs) }
        }

    @Test
    fun tappingARowSelectsThatAccount() =
        runTest {
            val selectWalletAccount = mockk<SelectWalletAccountUseCase>(relaxed = true)
            val ledger = ledger(isSelected = false)
            val vm = vm(listOf(zashi(isSelected = true), ledger), selectWalletAccount = selectWalletAccount)
            collect(vm)

            assertNotNull(vm.state.value?.items)[1].onClick()
            runCurrent()

            coVerify(exactly = 1) { selectWalletAccount.invoke(ledger) }
        }

    private fun vm(
        accounts: List<WalletAccount>?,
        selectWalletAccount: SelectWalletAccountUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = AccountListVM(
        getWalletAccounts =
            mockk<GetWalletAccountsUseCase> {
                every { observe() } returns MutableStateFlow(accounts)
            },
        selectWalletAccount = selectWalletAccount,
        navigationRouter = navigationRouter,
    )

    private fun zashi(isSelected: Boolean) =
        ZashiAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u-zashi",
            transparentAddress = "t-zashi",
            saplingAddress = "s-zashi",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun keystone(isSelected: Boolean) =
        KeystoneAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u-keystone",
            transparentAddress = "t-keystone",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun ledger(isSelected: Boolean) =
        LedgerAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u-ledger",
            transparentAddress = "t-ledger",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
            deviceIdentity = null,
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    private fun TestScope.collect(vm: AccountListVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }
}
