package co.electriccoin.zcash.ui.screen.home

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.WalletSnapshotDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.provider.ShieldFundsInfoProvider
import co.electriccoin.zcash.ui.common.usecase.GetHomeMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToNearPayUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSendUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapUseCase
import co.electriccoin.zcash.ui.design.component.BigIconButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

/**
 * Home offers Receive, Send, Pay and Swap for an account that can spend, and only Receive and Swap
 * for a view-only account: Send and Pay are left out, not disabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeVMButtonsTest {
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
    fun spendableAccountsGetAllFourButtons() =
        runTest {
            listOf(zodl(), keystone(), ledger()).forEach { account ->
                val vm = vm(MutableStateFlow(account))
                collect(vm)

                val state = assertNotNull(vm.state.value)
                assertEquals(
                    listOf(R.string.tabs_receive, R.string.tabs_send, R.string.crosspay_pay, R.string.swapAndPay_swap),
                    state.buttons().map { it.text.resourceId() }
                )
            }
        }

    @Test
    fun aViewOnlyAccountGetsOnlyReceiveAndSwap() =
        runTest {
            val vm = vm(MutableStateFlow(viewOnly()))
            collect(vm)

            val state = assertNotNull(vm.state.value)
            assertNull(state.secondButton)
            assertNull(state.thirdButton)
            assertEquals(
                listOf(R.string.tabs_receive, R.string.swapAndPay_swap),
                state.buttons().map { it.text.resourceId() }
            )
        }

    @Test
    fun switchingAccountsAddsAndRemovesSendAndPay() =
        runTest {
            val selected = MutableStateFlow<WalletAccount?>(zodl())
            val vm = vm(selected)
            collect(vm)
            assertEquals(4, assertNotNull(vm.state.value).buttons().size)

            selected.value = viewOnly()
            runCurrent()
            assertEquals(2, assertNotNull(vm.state.value).buttons().size)

            selected.value = zodl()
            runCurrent()
            assertEquals(4, assertNotNull(vm.state.value).buttons().size)
        }

    @Test
    fun theSwapButtonStillOpensSwapForAViewOnlyAccount() =
        runTest {
            val navigateToSwap = mockk<NavigateToSwapUseCase>(relaxed = true)
            val vm = vm(MutableStateFlow(viewOnly()), navigateToSwap = navigateToSwap)
            collect(vm)

            assertNotNull(vm.state.value).fourthButton.onClick()
            runCurrent()

            verify(exactly = 1) { navigateToSwap() }
        }

    @Test
    fun aSpendableAccountIsAssumedUntilTheSelectedAccountLoads() =
        runTest {
            val vm = vm(MutableStateFlow(null))
            collect(vm)

            assertEquals(4, assertNotNull(vm.state.value).buttons().size)
        }

    private fun HomeState.buttons(): List<BigIconButtonState> =
        listOfNotNull(firstButton, secondButton, thirdButton, fourthButton)

    private fun StringResource.resourceId(): Int? = (this as? StringResource.ByResource)?.resource

    private fun vm(
        selectedAccount: MutableStateFlow<WalletAccount?>,
        navigateToSwap: NavigateToSwapUseCase = mockk(relaxed = true),
    ) = HomeVM(
        getHomeMessage =
            mockk<GetHomeMessageUseCase> {
                every { observe() } returns flowOf(null)
            },
        shieldFundsInfoProvider =
            mockk<ShieldFundsInfoProvider> {
                every { observe() } returns flowOf(false)
            },
        isRestoreSuccessDialogVisible = mockk(relaxed = true),
        navigationRouter = mockk(relaxed = true),
        shieldFundsFromMessage = mockk(relaxed = true),
        navigateToError = mockk(relaxed = true),
        navigateToReceive = mockk(relaxed = true),
        navigateToSend = mockk<NavigateToSendUseCase>(relaxed = true),
        navigateToNearPay = mockk<NavigateToNearPayUseCase>(relaxed = true),
        navigateToSwap = navigateToSwap,
        navigateToVoting = mockk(relaxed = true),
        votingHomeHooks = mockk(relaxed = true),
        migrationHomeMessageSource = mockk(relaxed = true),
        homeMessageMapper = mockk(relaxed = true),
        walletSnapshotDataSource =
            mockk<WalletSnapshotDataSource> {
                every { observe() } returns MutableStateFlow(null)
            },
        getSelectedWalletAccount =
            mockk<GetSelectedWalletAccountUseCase> {
                every { observe() } returns selectedAccount
            },
    )

    private fun TestScope.collect(vm: HomeVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun zodl() =
        ZodlAccount(
            sdkAccount = sdkAccount,
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
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun ledger() =
        LedgerAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
            deviceIdentity = null,
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    private fun viewOnly() =
        ViewOnlyAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = 0,
        )
}
