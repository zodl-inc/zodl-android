package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.screen.swap.ab.SelectABSwapRecipientArgs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [NavigateToSelectABSwapRecipientUseCase] gates the address-book picker behind a biometric prompt:
 * only on success does it navigate and suspend for a result. A failed/cancelled biometric, and a
 * dismissed picker, all resolve to null — and a biometric failure must never navigate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToSelectABSwapRecipientUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }
    private val biometricRepository = mockk<BiometricRepository>(relaxed = true)
    private val useCase = NavigateToSelectABSwapRecipientUseCase(router, biometricRepository)

    @Test
    fun selectionResolvesToContactAndPopsBack() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as SelectABSwapRecipientArgs

            val contact = contact("Alice")
            useCase.onSelected(contact, args)

            assertEquals(contact, result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun cancellationResolvesToNullAndPopsBack() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as SelectABSwapRecipientArgs

            useCase.onSelectionCancelled(args)

            assertNull(result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun biometricsFailureResolvesToNullWithoutNavigating() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { biometricRepository.requestBiometrics(any()) } throws BiometricsFailureException()

            assertNull(useCase())
            assertTrue(forwarded.isEmpty())
            verify(exactly = 0) { router.forward(*anyVararg()) }
        }

    @Test
    fun biometricsCancellationResolvesToNullWithoutNavigating() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { biometricRepository.requestBiometrics(any()) } throws BiometricsCancelledException()

            assertNull(useCase())
            assertTrue(forwarded.isEmpty())
            verify(exactly = 0) { router.forward(*anyVararg()) }
        }

    @Test
    fun ignoresResultFromADifferentRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val result = async { useCase() }
            val args = forwarded.single() as SelectABSwapRecipientArgs

            useCase.onSelected(contact("Stale"), args.copy(requestId = "stale"))
            assertTrue(result.isActive)

            val fresh = contact("Fresh")
            useCase.onSelected(fresh, args)
            assertEquals(fresh, result.await())
        }

    private fun contact(name: String) =
        EnhancedABContact(
            contact =
                AddressBookContact(
                    name = name,
                    address = "address-$name",
                    lastUpdated = Instant.fromEpochMilliseconds(0),
                    chain = null
                ),
            blockchain = null
        )
}
