package co.electriccoin.zcash.ui.screen.scan

import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.OnAddressScannedUseCase
import co.electriccoin.zcash.ui.common.usecase.OnZip321ScannedUseCase
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase
import co.electriccoin.zcash.ui.screen.redeemgift.RecordingNavigationRouter
import co.electriccoin.zcash.ui.screen.redeemgift.RedeemGiftArgs
import co.electriccoin.zcash.ui.screen.redeemgift.navigateToRedeemGiftCard
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The address scanners hand a scanned gift card link to the redeem flow by id only: the link never reaches the
 * ZIP-321 or address parsers, nor the caller as an address.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanGiftCardLinkTest {
    private lateinit var dispatcher: TestDispatcher

    private val store = GiftCardLinkStoreImpl()

    private val router = RecordingNavigationRouter()

    private val navigateToRedeemGiftCard = navigateToRedeemGiftCard(store, router)

    private val zip321Parser = mockk<Zip321ParseUriValidationUseCase>()

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
    fun theZashiScannerOpensTheRedeemFlowWithoutParsingTheLink() =
        runTest(dispatcher) {
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val onAddressScanned = mockk<OnAddressScannedUseCase>()
            val onZip321Scanned = mockk<OnZip321ScannedUseCase>()
            val vm =
                ScanZashiAddressVM(
                    args = ScanArgs(flow = ScanFlow.HOMEPAGE),
                    synchronizerProvider = synchronizerProvider,
                    zip321ParseUriValidationUseCase = zip321Parser,
                    onAddressScanned = onAddressScanned,
                    zip321Scanned = onZip321Scanned,
                    navigateToRedeemGiftCard = navigateToRedeemGiftCard,
                )

            vm.onScanned(" $LINK\n")
            advanceUntilIdle()

            assertEquals(ScanValidationState.VALID, vm.state.value)
            val args = assertIs<RedeemGiftArgs>(router.replacedRoutes.single())
            assertEquals(LINK, store.take(args.linkId))
            confirmVerified(zip321Parser, synchronizerProvider, onAddressScanned, onZip321Scanned)
        }

    @Test
    fun theGenericScannerResolvesAsCancelledAndOpensTheRedeemFlow() =
        runTest(dispatcher) {
            val navigateToScanAddress = NavigateToScanGenericAddressUseCase(router)
            val scan = async { navigateToScanAddress() }
            runCurrent()
            val scanArgs = assertIs<ScanGenericAddressArgs>(router.forwardedRoutes.single())
            val vm =
                ScanGenericAddressVM(
                    args = scanArgs,
                    parseZip321 = zip321Parser,
                    navigateToScanAddress = navigateToScanAddress,
                    navigateToRedeemGiftCard = navigateToRedeemGiftCard,
                )

            vm.onScanned(LINK)
            advanceUntilIdle()

            assertNull(scan.await())
            assertEquals(ScanValidationState.VALID, vm.state.value)
            val args = assertIs<RedeemGiftArgs>(router.replacedRoutes.single())
            assertEquals(LINK, store.take(args.linkId))
            assertEquals(0, router.backCount)
            confirmVerified(zip321Parser)
        }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
    }
}
