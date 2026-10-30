package co.electriccoin.zcash.ui.screen.scan

import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ScanResult
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import co.electriccoin.zcash.ui.screen.redeemgift.RecordingNavigationRouter
import co.electriccoin.zcash.ui.screen.redeemgift.navigateToRedeemGiftCard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanGenericAddressVMTest {
    private lateinit var dispatcher: TestDispatcher

    private val router = RecordingNavigationRouter()

    private val navigateToRedeemGiftCard =
        navigateToRedeemGiftCard(GiftCardLinkStoreImpl(), router)

    private val zip321Parser = mockk<Zip321ParseUriValidationUseCase>()

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        coEvery { zip321Parser(any()) } returns Zip321ParseUriValidation.Invalid
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bitcoinUriResolvesToBareAddressWithoutAmount() =
        runTest(dispatcher) {
            val harness = createHarness()

            harness.vm.onScanned(BITCOIN_URI)
            advanceUntilIdle()

            val result = assertNotNull(harness.scan.await())
            assertEquals(ADDRESS, result.address)
            assertNull(result.amount)
            assertEquals(ScanValidationState.VALID, harness.vm.state.value)
            coVerify(exactly = 0) { zip321Parser(any()) }
        }

    @Test
    fun bitcoinUriWithoutAmountResolvesToBareAddress() =
        runTest(dispatcher) {
            val harness = createHarness()

            harness.vm.onScanned("bitcoin:$ADDRESS")
            advanceUntilIdle()

            val result = assertNotNull(harness.scan.await())
            assertEquals(ADDRESS, result.address)
            assertNull(result.amount)
            coVerify(exactly = 0) { zip321Parser(any()) }
        }

    @Test
    fun uppercaseBitcoinUriResolvesToBareAddress() =
        runTest(dispatcher) {
            val harness = createHarness()

            harness.vm.onScanned("BITCOIN:$ADDRESS?amount=1")
            advanceUntilIdle()

            val result = assertNotNull(harness.scan.await())
            assertEquals(ADDRESS, result.address)
            coVerify(exactly = 0) { zip321Parser(any()) }
        }

    @Test
    fun malformedBitcoinUriIsRejectedWithoutResolving() =
        runTest(dispatcher) {
            val harness = createHarness()

            harness.vm.onScanned("bitcoin:$ADDRESS?amount=0.123456789")
            advanceUntilIdle()

            assertEquals(ScanValidationState.INVALID, harness.vm.state.value)
            assertTrue(harness.scan.isActive)
            coVerify(exactly = 0) { zip321Parser(any()) }
            harness.scan.cancel()
        }

    @Test
    fun plainAddressStillResolves() =
        runTest(dispatcher) {
            val harness = createHarness()

            harness.vm.onScanned(ADDRESS)
            advanceUntilIdle()

            val result = assertNotNull(harness.scan.await())
            assertEquals(ADDRESS, result.address)
            assertNull(result.amount)
            coVerify(exactly = 1) { zip321Parser(ADDRESS) }
        }

    @Test
    fun zip321SingleAddressStillResolves() =
        runTest(dispatcher) {
            val zip321Uri = "zcash:$ZCASH_ADDRESS"
            coEvery { zip321Parser(zip321Uri) } returns
                Zip321ParseUriValidation.SingleAddress(ZCASH_ADDRESS)
            val harness = createHarness()

            harness.vm.onScanned(zip321Uri)
            advanceUntilIdle()

            val result = assertNotNull(harness.scan.await())
            assertEquals(ZCASH_ADDRESS, result.address)
            assertNull(result.amount)
            coVerify(exactly = 1) { zip321Parser(zip321Uri) }
        }

    private fun TestScope.createHarness(): Harness {
        val navigateToScanAddress = NavigateToScanGenericAddressUseCase(router)
        val scan = async { navigateToScanAddress() }
        runCurrent()
        val args = assertIs<ScanGenericAddressArgs>(router.forwardedRoutes.single())
        val vm =
            ScanGenericAddressVM(
                args = args,
                parseZip321 = zip321Parser,
                navigateToScanAddress = navigateToScanAddress,
                navigateToRedeemGiftCard = navigateToRedeemGiftCard,
            )
        return Harness(vm = vm, scan = scan)
    }

    private class Harness(
        val vm: ScanGenericAddressVM,
        val scan: Deferred<ScanResult?>,
    )

    private companion object {
        const val ADDRESS = "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"
        const val ZCASH_ADDRESS = "zs1testaddress"
        const val BITCOIN_URI = "bitcoin:$ADDRESS?amount=0.001&label=Shop"
    }
}
