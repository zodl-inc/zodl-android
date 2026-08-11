package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.scan.ScanGenericAddressArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [NavigateToScanGenericAddressUseCase] navigates to the scanner and suspends until that screen
 * returns a scan result (address + optional amount) or is dismissed (resolving to null). Results
 * tagged with a different requestId are ignored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToScanGenericAddressUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }
    private val useCase = NavigateToScanGenericAddressUseCase(router)

    @Test
    fun scanResolvesToAddressAndAmount() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as ScanGenericAddressArgs

            useCase.onScanned(address = "zs1address", amount = BigDecimal("1.5"), args = args)

            val scanResult = result.await()
            assertEquals("zs1address", scanResult?.address)
            assertEquals(0, BigDecimal("1.5").compareTo(scanResult?.amount))
        }

    @Test
    fun scanWithoutAmountResolvesToNullAmount() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as ScanGenericAddressArgs

            useCase.onScanned(address = "zs1address", amount = null, args = args)

            val scanResult = result.await()
            assertEquals("zs1address", scanResult?.address)
            assertNull(scanResult?.amount)
        }

    @Test
    fun cancellationResolvesToNullAndPopsBack() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as ScanGenericAddressArgs

            useCase.onScanCancelled(args)

            assertNull(result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun ignoresResultFromADifferentRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as ScanGenericAddressArgs

            useCase.onScanned(address = "stale", amount = null, args = args.copy(requestId = "stale"))
            assertTrue(result.isActive)

            useCase.onScanned(address = "fresh", amount = null, args = args)
            assertEquals("fresh", result.await()?.address)
        }
}
