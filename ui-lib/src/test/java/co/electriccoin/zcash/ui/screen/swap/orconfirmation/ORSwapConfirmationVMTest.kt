package co.electriccoin.zcash.ui.screen.swap.orconfirmation

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.FakeSwapQuote
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.SaveORSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The swap deposit confirmation screen: sharing the deposit QR hands the share use case the deposit address
 * together with the composed "send X TOKEN on CHAIN" text, and the copy actions put the address and the amount
 * on the clipboard. The fiat symbol comes from `android.icu`, which the JVM android stubs cannot answer, so
 * [FiatCurrency.USD] is stubbed for the state to build.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ORSwapConfirmationVMTest {
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        mockkObject(FiatCurrency.USD)
        every { FiatCurrency.USD.symbol } returns "$"
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(FiatCurrency.USD)
        Dispatchers.resetMain()
    }

    @Test
    fun shareSendsTheDepositQrWithTheComposedShareText() =
        runTest(dispatcher) {
            val shareQR = mockk<ShareQRUseCase>(relaxed = true)
            val vm = startedVm(shareQR = shareQR)

            requireNotNull(vm.state.value).shareButton.onClick()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                shareQR(
                    qrData = DEPOSIT_ADDRESS,
                    shareText =
                        stringRes(
                            R.string.swap_to_zec_share_text,
                            stringResByNumber(AMOUNT_IN),
                            "BTC",
                            stringRes("BTC"),
                            CURRENCY_TICKER
                        ),
                    sharePickerText = stringRes("Swap Deposit Address"),
                    filenamePrefix = "swap_deposit_address_",
                    centerIcon = null
                )
            }
        }

    @Test
    fun copyActionsPutTheAddressAndTheAmountOnTheClipboard() =
        runTest(dispatcher) {
            val copyToClipboard = mockk<CopyToClipboardUseCase>(relaxed = true)
            val vm = startedVm(copyToClipboard = copyToClipboard)
            val state = requireNotNull(vm.state.value)

            state.copyButton.onClick()
            state.onAmountClick()

            verify(exactly = 1) { copyToClipboard(DEPOSIT_ADDRESS) }
            verify(exactly = 1) { copyToClipboard(AMOUNT_IN.toPlainString()) }
        }

    private fun TestScope.startedVm(
        copyToClipboard: CopyToClipboardUseCase = mockk(relaxed = true),
        shareQR: ShareQRUseCase = mockk(relaxed = true),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): ORSwapConfirmationVM {
        val quote =
            FakeSwapQuote(
                originAsset = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
                destinationAsset = SwapAssetTestFixture.zecAsset(),
                mode = SwapMode.EXACT_INPUT,
                amountIn = AMOUNT_IN,
                amountInFormatted = AMOUNT_IN,
                amountOutFormatted = BigDecimal.ONE,
                depositAddress = DEPOSIT_ADDRESS,
                destinationAddress = "u1destination",
                refundAddress = "bc1refund"
            )
        val swapRepository =
            mockk<SwapRepository> {
                every { this@mockk.quote } returns MutableStateFlow(SwapQuoteData.Success(quote))
            }
        val vm =
            ORSwapConfirmationVM(
                swapRepository = swapRepository,
                cancelSwapQuote = mockk<CancelSwapQuoteUseCase>(relaxed = true),
                copyToClipboard = copyToClipboard,
                navigationRouter = router,
                saveORSwap = mockk<SaveORSwapUseCase>(relaxed = true),
                shareQR = shareQR,
            )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private const val DEPOSIT_ADDRESS = "bc1depositaddress"

private val AMOUNT_IN = BigDecimal("0.5")

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
