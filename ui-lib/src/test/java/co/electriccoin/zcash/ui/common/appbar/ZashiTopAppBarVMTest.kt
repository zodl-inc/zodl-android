package co.electriccoin.zcash.ui.common.appbar

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.screen.integrations.IntegrationsArgs
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

/**
 * The more button opens Integrations in every distribution, also with a hardware wallet and no Flexa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZashiTopAppBarVMTest {
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
    fun aHardwareWalletOpensIntegrations() {
        val router = mockk<NavigationRouter>(relaxed = true)
        val vm = vm(listOf(zashi(isSelected = false), keystone()), router)

        vm.state.value.moreButton.onClick()

        verify(exactly = 1) { router.forward(IntegrationsArgs) }
    }

    @Test
    fun theSoftwareWalletOpensIntegrations() {
        val router = mockk<NavigationRouter>(relaxed = true)
        val vm = vm(listOf(zashi(isSelected = true)), router)

        vm.state.value.moreButton.onClick()

        verify(exactly = 1) { router.forward(IntegrationsArgs) }
    }

    private fun vm(
        accounts: List<WalletAccount>,
        router: NavigationRouter
    ) =
        ZashiTopAppBarVM(
            getWalletAccountsUseCase =
                mockk<GetWalletAccountsUseCase> {
                    every { observe() } returns MutableStateFlow(accounts)
                },
            standardPreferenceProvider = mockk<StandardPreferenceProvider>(relaxed = true),
            navigationRouter = router,
        )

    private fun zashi(isSelected: Boolean) =
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
}
