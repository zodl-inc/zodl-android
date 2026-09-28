package co.electriccoin.zcash.ui.screen.qrcode

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.WalletAddress
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertIs

/**
 * The address QR screen: it shows the selected account's address of the requested type, shares that address
 * as a QR with the ZEC share texts and the centre icon matching the address kind, copies it to the clipboard,
 * marks a Keystone account's QR as Keystone, and stays loading for an address the account does not have.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QrCodeVMTest {
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
    fun unifiedAddressSharesAShieldedQrWithTheZecTexts() =
        runTest(dispatcher) {
            val shareQR = mockk<ShareQRUseCase>(relaxed = true)
            val vm = startedVm(addressType = ReceiveAddressType.Unified, shareQR = shareQR)

            val state = assertIs<QrCodeState.Prepared>(vm.state.value)
            assertIs<WalletAddress.Unified>(state.walletAddress)
            assertEquals(UNIFIED, state.walletAddress.address)

            state.onQrCodeShare(UNIFIED)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                shareQR(
                    qrData = UNIFIED,
                    shareText = stringRes(R.string.qr_code_share_chooser_text, CURRENCY_TICKER),
                    sharePickerText = stringRes(R.string.qr_code_share_chooser_title, CURRENCY_TICKER),
                    filenamePrefix = "zcash_address_qr_",
                    centerIcon = R.drawable.ic_zec_qr_shielded
                )
            }
        }

    @Test
    fun transparentAddressSharesWithTheTransparentIcon() =
        runTest(dispatcher) {
            val shareQR = mockk<ShareQRUseCase>(relaxed = true)
            val vm = startedVm(addressType = ReceiveAddressType.Transparent, shareQR = shareQR)

            val state = assertIs<QrCodeState.Prepared>(vm.state.value)
            assertIs<WalletAddress.Transparent>(state.walletAddress)

            state.onQrCodeShare(TRANSPARENT)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                shareQR(
                    qrData = TRANSPARENT,
                    shareText = any(),
                    sharePickerText = any(),
                    filenamePrefix = any(),
                    centerIcon = R.drawable.ic_zec_qr_transparent
                )
            }
        }

    @Test
    fun copyPutsTheAddressOnTheClipboard() =
        runTest(dispatcher) {
            val copyToClipboard = mockk<CopyToClipboardUseCase>(relaxed = true)
            val vm = startedVm(copyToClipboard = copyToClipboard)

            assertIs<QrCodeState.Prepared>(vm.state.value).onAddressCopy(UNIFIED)

            verify(exactly = 1) { copyToClipboard(UNIFIED) }
        }

    @Test
    fun keystoneAccountMarksTheQrAsKeystone() =
        runTest(dispatcher) {
            val zodl = startedVm(account = zashiAccount())
            val keystone = startedVm(account = keystoneAccount())

            assertEquals(QrCodeType.ZASHI, assertIs<QrCodeState.Prepared>(zodl.state.value).qrCodeType)
            assertEquals(QrCodeType.KEYSTONE, assertIs<QrCodeState.Prepared>(keystone.state.value).qrCodeType)
        }

    @Test
    fun missingSaplingAddressStaysLoading() =
        runTest(dispatcher) {
            val vm = startedVm(account = keystoneAccount(), addressType = ReceiveAddressType.Sapling)

            assertIs<QrCodeState.Loading>(vm.state.value)
        }

    @Test
    fun backNavigatesBack() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router = router)

            assertIs<QrCodeState.Prepared>(vm.state.value).onBack()

            assertEquals(1, router.backCount)
        }

    private fun zashiAccount() =
        ZashiAccount(
            sdkAccount = mockk<Account>(relaxed = true),
            unifiedAddress = UNIFIED,
            transparentAddress = TRANSPARENT,
            saplingAddress = "sapling",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun keystoneAccount() =
        KeystoneAccount(
            sdkAccount = mockk<Account>(relaxed = true),
            unifiedAddress = UNIFIED,
            transparentAddress = TRANSPARENT,
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun TestScope.startedVm(
        account: WalletAccount = zashiAccount(),
        addressType: ReceiveAddressType = ReceiveAddressType.Unified,
        copyToClipboard: CopyToClipboardUseCase = mockk(relaxed = true),
        shareQR: ShareQRUseCase = mockk(relaxed = true),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): QrCodeVM {
        val observeSelectedWalletAccount =
            mockk<ObserveSelectedWalletAccountUseCase> {
                every { require() } returns flowOf(account)
            }
        val vm =
            QrCodeVM(
                observeSelectedWalletAccount = observeSelectedWalletAccount,
                addressTypeOrdinal = addressType.ordinal,
                copyToClipboard = copyToClipboard,
                navigationRouter = router,
                shareQR = shareQR,
            )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private const val UNIFIED = "u1unifiedaddress"

private const val TRANSPARENT = "t1transparentaddress"

private class FakeNavigationRouter : NavigationRouter {
    var backCount = 0
        private set
    val forwardedRoutes = mutableListOf<Any>()
    val replacedRoutes = mutableListOf<Any>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) {
        replacedRoutes.addAll(routes)
    }

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
