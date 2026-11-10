package co.electriccoin.zcash.ui.common.zns

import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.provider.IsZnsNamesEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.ValidateAddressUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LookUpZnsRecipientUseCaseTest {
    private val alice = checkNotNull(ZnsName.fromRegistryName("alice"))

    private val resolver = mockk<ZnsNameResolver>()
    private val enabled = mockk<IsZnsNamesEnabledStorageProvider> { coEvery { get() } returns true }
    private val validateAddress =
        mockk<ValidateAddressUseCase> {
            coEvery { this@mockk(any()) } returns AddressType.Unified
            coEvery { this@mockk("t1bad") } returns AddressType.Invalid("bad")
        }

    private val useCase = LookUpZnsRecipientUseCase(resolver, enabled, validateAddress)

    @Test
    fun textThatIsNotANameIsLeftToAddressValidation() =
        runTest {
            assertEquals(ZnsLookupResult.NotAName, useCase("u1abc"))
            assertEquals(ZnsLookupResult.NotAName, useCase("alice"))
            coVerify(exactly = 0) { resolver.resolve(any()) }
        }

    @Test
    fun withNamesOffNoRequestIsMade() =
        runTest {
            coEvery { enabled.get() } returns false

            assertEquals(ZnsLookupResult.NamesOff(alice), useCase("alice.zcash"))
            coVerify(exactly = 0) { resolver.resolve(any()) }
        }

    @Test
    fun aRegisteredNameResolvesToAValidatedAddress() =
        runTest {
            coEvery { resolver.resolve(alice) } returns ZnsRegistration(alice, "u1alice")

            assertEquals(ZnsLookupResult.Resolved(alice, "u1alice", AddressType.Unified), useCase("Alice.ZEC"))
            coVerify { validateAddress("u1alice") }
        }

    @Test
    fun eachFailureHasItsOwnResult() =
        runTest {
            coEvery { resolver.resolve(alice) } returns null
            assertEquals(ZnsLookupResult.NotRegistered(alice), useCase("alice.zcash"))

            coEvery { resolver.resolve(alice) } returns ZnsRegistration(alice, "t1bad")
            assertEquals(ZnsLookupResult.InvalidAddress(alice), useCase("alice.zcash"))

            coEvery { resolver.resolve(alice) } throws ZnsLookupException("down")
            assertEquals(ZnsLookupResult.Unreachable(alice), useCase("alice.zcash"))
        }

    @Test
    fun confirmationComparesTheNameWithThePaidAddress() =
        runTest {
            coEvery { resolver.resolve(alice) } returns ZnsRegistration(alice, "u1alice")
            assertEquals(ZnsConfirmation.UNCHANGED, useCase.confirm(alice, "u1alice"))
            assertEquals(ZnsConfirmation.CHANGED, useCase.confirm(alice, "u1other"))

            coEvery { resolver.resolve(alice) } returns null
            assertEquals(ZnsConfirmation.CHANGED, useCase.confirm(alice, "u1alice"))

            coEvery { resolver.resolve(alice) } throws ZnsLookupException("down")
            assertEquals(ZnsConfirmation.UNCONFIRMED, useCase.confirm(alice, "u1alice"))

            coEvery { enabled.get() } returns false
            assertEquals(ZnsConfirmation.UNCONFIRMED, useCase.confirm(alice, "u1alice"))
        }
}
