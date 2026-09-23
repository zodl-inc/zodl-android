package co.electriccoin.zcash.ui.screen.choosehwwallet

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.screen.connectkeystone.connect.ConnectKeystoneArgs
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The picker offers a vendor's card only while that vendor is unconnected, and each card routes to
 * that vendor's own connect flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChooseHWWalletVMTest {
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
    fun showsBothCardsWhenNoHWWalletIsConnected() {
        val vm = vm(accounts = emptyList())

        assertEquals(
            listOf(ChooseHWWalletTag.KEYSTONE_CARD, ChooseHWWalletTag.LEDGER_CARD),
            vm.state.value.cards
                .map { it.testTag }
        )
    }

    @Test
    fun hidesTheKeystoneCardOnceAKeystoneAccountExists() {
        val vm = vm(accounts = listOf(keystoneAccount()))

        assertEquals(
            listOf(ChooseHWWalletTag.LEDGER_CARD),
            vm.state.value.cards
                .map { it.testTag }
        )
    }

    @Test
    fun hidesTheLedgerCardOnceALedgerAccountExists() {
        val vm = vm(accounts = listOf(ledgerAccount()))

        assertEquals(
            listOf(ChooseHWWalletTag.KEYSTONE_CARD),
            vm.state.value.cards
                .map { it.testTag }
        )
    }

    @Test
    fun showsNoCardsOnceBothVendorsAreConnected() {
        val vm = vm(accounts = listOf(keystoneAccount(), ledgerAccount()))

        assertEquals(emptyList(), vm.state.value.cards)
    }

    @Test
    fun eachCardRoutesToItsVendorConnectFlow() {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)
        val vm = vm(accounts = emptyList(), navigationRouter = navigationRouter)

        vm.state.value.cards
            .first { it.testTag == ChooseHWWalletTag.KEYSTONE_CARD }
            .onClick()
        vm.state.value.cards
            .first { it.testTag == ChooseHWWalletTag.LEDGER_CARD }
            .onClick()

        verify(exactly = 1) { navigationRouter.forward(ConnectKeystoneArgs) }
        verify(exactly = 1) { navigationRouter.forward(LedgerConnectArgs) }
    }

    private fun vm(
        accounts: List<WalletAccount>?,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ): ChooseHWWalletVM {
        val getWalletAccounts =
            mockk<GetWalletAccountsUseCase> {
                every { observe() } returns MutableStateFlow(accounts)
            }
        return ChooseHWWalletVM(
            getWalletAccounts = getWalletAccounts,
            navigationRouter = navigationRouter,
        )
    }

    private fun keystoneAccount() =
        KeystoneAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
        )

    private fun ledgerAccount() =
        LedgerAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            deviceIdentity = null,
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )
}
