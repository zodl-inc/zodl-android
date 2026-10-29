package co.electriccoin.zcash.ui.screen.balances.spendable

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.LoadedAccountBalances
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.usecase.GetTransactionsUseCase
import co.electriccoin.zcash.ui.common.usecase.ListTransactionData
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Shielding is a spend, so the spendable-balance sheet offers it only to an account with spend authority: a view-only
 * account with transparent funds sees neither the shield button nor the shielding paragraph, and gets the plain "OK".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpendableBalanceVMTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun spendableAccountIsOfferedShielding() =
        runTest {
            val state = stateFor(account(isSpendable = true))

            val shieldButton = assertNotNull(state.shieldButton)
            assertEquals(TRANSPARENT, shieldButton.amount)
            assertNotEquals(stringRes(""), state.message)
            assertEquals(stringRes(co.electriccoin.zcash.ui.design.R.string.balances_dismiss), state.positive.text)
        }

    @Test
    fun viewOnlyAccountIsNotOfferedShielding() =
        runTest {
            val state = stateFor(account(isSpendable = false))

            assertNull(state.shieldButton)
            assertEquals(stringRes(""), state.message)
            assertEquals(stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok), state.positive.text)
        }

    private fun TestScope.stateFor(account: WalletAccount): SpendableBalanceState {
        val accountDataSource =
            mockk<AccountDataSource> {
                every { selectedAccount } returns MutableStateFlow(account)
                every { allAccounts } returns MutableStateFlow(listOf(account))
            }
        val getTransactions =
            mockk<GetTransactionsUseCase> {
                every { observe() } returns MutableStateFlow<List<ListTransactionData>?>(emptyList())
            }
        val vm =
            SpendableBalanceVM(
                accountDataSource = accountDataSource,
                getTransactions = getTransactions,
                navigationRouter = mockk<NavigationRouter>(relaxed = true),
                shieldFunds = mockk<ShieldFundsUseCase>(relaxed = true)
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return checkNotNull(vm.state.value)
    }

    private fun account(isSpendable: Boolean): WalletAccount =
        mockk(relaxed = true) {
            every { isSelected } returns true
            every { this@mockk.isSpendable } returns isSpendable
            every { loadedBalances } returns BALANCES
        }

    private companion object {
        val TRANSPARENT = Zatoshi(500_000)
        val SHIELDED = Zatoshi(1_000_000)

        /** Fully synced shielded funds plus shieldable transparent funds, so only the account decides the offer. */
        val BALANCES =
            LoadedAccountBalances(
                isAllShielded = false,
                totalBalance = SHIELDED,
                totalShieldedBalance = SHIELDED,
                totalTransparentBalance = TRANSPARENT,
                spendableShieldedBalance = SHIELDED,
                pendingShieldedBalance = Zatoshi(0),
                isShieldedPending = false,
                isShieldingAvailable = true,
                transparentBalance = TRANSPARENT,
            )
    }
}
