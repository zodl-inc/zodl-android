package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.MemoContent
import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.TransactionSizeBound
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountDataSource
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountUnavailableException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.SignerCapabilities
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Runs under Robolectric because failures are logged via `Twig` (android.util.Log), which the tests read back
 * to prove only class names (never throwable messages or addresses) are logged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GetMaxSendAmountUseCaseTest {
    private val recipient = mockk<RecipientAddress>()
    private val ownRecipient = mockk<RecipientAddress>()
    private val account =
        mockk<WalletAccount> {
            every { transparentAddress } returns OWN_T_ADDRESS
            every { unifiedAddress } returns OWN_U_ADDRESS
        }
    private val accountDataSource =
        mockk<AccountDataSource> { coEvery { getSelectedAccount() } returns account }
    private val maxSendAmountDataSource = mockk<MaxSendAmountDataSource>()
    private val bound = TransactionSizeBound(maxShieldedActions = 30)
    private val signerCapabilitiesProvider =
        mockk<SignerCapabilitiesProvider> {
            coEvery { forAccount(account) } returns SignerCapabilities(bound)
        }
    private val synchronizerProvider =
        mockk<SynchronizerProvider> {
            coEvery { getSynchronizer() } returns mockk { every { network } returns ZcashNetwork.Mainnet }
        }
    private val logged: List<String>
        get() = ShadowLog.getLogs().filter { it.type == android.util.Log.ERROR }.map { it.msg }

    private val useCase =
        GetMaxSendAmountUseCase(
            accountDataSource,
            maxSendAmountDataSource,
            synchronizerProvider,
            marginalFeeProvider = { Zatoshi(5_000) },
            signerCapabilitiesProvider = signerCapabilitiesProvider
        )

    @BeforeTest
    fun setUp() {
        ShadowLog.clear()
        mockkObject(RecipientAddress.Companion)
        coEvery { RecipientAddress.new(ADDRESS, any()) } returns recipient
        coEvery { RecipientAddress.new(OWN_T_ADDRESS, any()) } returns ownRecipient
        coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } returns
            MaxSendAmount(amount = Zatoshi(100_000), fee = Zatoshi(10_000))
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    private suspend fun memoSentFor(type: AddressType, text: String): MemoContent? {
        var sent: MemoContent? = null
        coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } answers {
            sent = thirdArg()
            MaxSendAmount(Zatoshi(1), Zatoshi(1))
        }
        useCase(MaxSendTarget.Recipient(ADDRESS, type, text))
        return sent
    }

    @Test
    fun recipientTargetProposesToTheValidatedAddressWithoutBuffer() =
        runTest {
            val result = useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))

            assertEquals(MaxSendAmount(Zatoshi(100_000), Zatoshi(10_000)), result)
            coVerify { maxSendAmountDataSource.proposeMaxSendAmount(account, recipient, null, bound) }
        }

    @Test
    fun theBoundIsTheSelectedAccountsAtTheTimeOfTheRequest() =
        runTest {
            val scarce = TransactionSizeBound(maxShieldedActions = TransactionSizeBound.MIN_PROVING_ACTIONS)
            useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
            coEvery { signerCapabilitiesProvider.forAccount(account) } returns SignerCapabilities(scarce)
            useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))

            coVerifyOrder {
                maxSendAmountDataSource.proposeMaxSendAmount(account, recipient, null, bound)
                maxSendAmountDataSource.proposeMaxSendAmount(account, recipient, null, scarce)
            }
        }

    @Test
    fun aMaxOverTheBoundSurfacesAsTooManyNotes() =
        runTest {
            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), eq(bound)) } throws
                MaxSendAmountUnavailableException(MaxSendAmountFailure.TOO_MANY_NOTES)

            val thrown =
                assertFailsWith<MaxSendAmountUnavailableException> {
                    useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
                }

            assertEquals(MaxSendAmountFailure.TOO_MANY_NOTES, thrown.reason)
        }

    @Test
    fun shieldedCapableRecipientsCarryTheMemoText() =
        runTest {
            assertEquals(MemoContent.fromString("hi"), memoSentFor(AddressType.Unified, "hi"))
            assertEquals(MemoContent.fromString("hi"), memoSentFor(AddressType.Shielded, "hi"))
        }

    @Test
    fun transparentAndTexRecipientsCarryNoMemo() =
        runTest {
            assertNull(memoSentFor(AddressType.Transparent, "hi"))
            assertNull(memoSentFor(AddressType.Tex, "hi"))
        }

    @Test
    fun ownTransparentTargetUsesTheAccountTransparentAddressAndKeepsTwoMarginalFeesBack() =
        runTest {
            val result = useCase(MaxSendTarget.OwnTransparentReceiver)

            assertEquals(MaxSendAmount(Zatoshi(90_000), Zatoshi(10_000)), result)
            coVerify { maxSendAmountDataSource.proposeMaxSendAmount(account, ownRecipient, null, bound) }
        }

    @Test
    fun noRecipientYetIsPricedForTheOwnUnifiedAddressWithNoMemoAndNoBuffer() =
        runTest {
            val ownUnified = mockk<RecipientAddress>()
            coEvery { RecipientAddress.new(OWN_U_ADDRESS, any()) } returns ownUnified
            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(account, ownUnified, null, bound) } returns
                MaxSendAmount(Zatoshi(100_000), Zatoshi(10_000))

            val result = useCase(MaxSendTarget.NoRecipientYet)

            assertEquals(MaxSendAmount(Zatoshi(100_000), Zatoshi(10_000)), result)
        }

    @Test
    fun ownTransparentTargetWhoseMaxDoesNotExceedTheBufferIsInsufficient() =
        runTest {
            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } returns
                MaxSendAmount(Zatoshi(5_000), Zatoshi(10_000))

            assertFailsWith<InsufficientFundsException> { useCase(MaxSendTarget.OwnTransparentReceiver) }
        }

    @Test
    fun theOwnReceiverBufferIsTwoFeesAtTheBoundary() =
        runTest {
            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } returns
                MaxSendAmount(Zatoshi(10_000), Zatoshi(10_000))
            assertFailsWith<InsufficientFundsException> { useCase(MaxSendTarget.OwnTransparentReceiver) }

            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } returns
                MaxSendAmount(Zatoshi(10_001), Zatoshi(10_000))
            assertEquals(Zatoshi(1), useCase(MaxSendTarget.OwnTransparentReceiver).amount)
        }

    @Test
    fun aZeroAmountIsInsufficientForARecipientToo() =
        runTest {
            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } returns
                MaxSendAmount(Zatoshi(0), Zatoshi(10_000))

            assertFailsWith<InsufficientFundsException> {
                useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
            }
        }

    @Test
    fun anInvalidAddressIsAnInvalidRecipientFailure() =
        runTest {
            coEvery { RecipientAddress.new(ADDRESS, any()) } throws IllegalArgumentException("secret detail")

            val e =
                assertFailsWith<MaxSendAmountUnavailableException> {
                    useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
                }

            assertEquals(MaxSendAmountFailure.INVALID_RECIPIENT, e.reason)
        }

    @Test
    fun everyDataSourceFailureReasonPassesThrough() =
        runTest {
            MaxSendAmountFailure.entries.forEach { reason ->
                val failure = MaxSendAmountUnavailableException(reason)
                coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } throws failure

                val e =
                    assertFailsWith<MaxSendAmountUnavailableException> {
                        useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
                    }

                assertSame(failure, e)
            }
            assertEquals(MaxSendAmountFailure.entries.size, logged.size)
            MaxSendAmountFailure.entries.forEach { reason ->
                val expected = "Max amount unavailable: MaxSendAmountUnavailableException ($reason)"
                assertTrue(logged.any { it.endsWith(expected) })
            }
        }

    @Test
    fun anUnexpectedFailureBecomesTheOtherReason() =
        runTest {
            val raw = IllegalStateException("raw backend message")
            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } throws raw

            val e =
                assertFailsWith<MaxSendAmountUnavailableException> {
                    useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
                }

            assertEquals(MaxSendAmountFailure.OTHER, e.reason)
            assertSame(raw, e.cause)
        }

    @Test
    fun cancellationIsRethrownAndNotLogged() =
        runTest {
            coEvery {
                maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any())
            } throws CancellationException("c")

            assertFailsWith<CancellationException> {
                useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
            }

            assertTrue(logged.isEmpty())
        }

    @Test
    fun failuresAreLoggedByClassNameOnly() =
        runTest {
            val secret = "secret raw message with $ADDRESS"
            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } throws
                InsufficientFundsException(
                    RuntimeException(secret)
                )

            assertFailsWith<InsufficientFundsException> {
                useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
            }

            assertEquals(1, logged.size)
            assertTrue(logged.single().endsWith("Max amount unavailable: InsufficientFundsException"))
            assertFalse(logged.any { it.contains("secret") || it.contains(ADDRESS) })
            assertTrue(ShadowLog.getLogs().all { it.throwable == null })
        }

    @Test
    fun anUnexpectedFailureIsLoggedByClassNameOnly() =
        runTest {
            coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } throws
                IllegalStateException("secret raw message")

            assertFailsWith<MaxSendAmountUnavailableException> {
                useCase(MaxSendTarget.Recipient(ADDRESS, AddressType.Unified, ""))
            }

            assertEquals(1, logged.size)
            assertTrue(logged.single().endsWith("Max amount unavailable: IllegalStateException"))
            assertTrue(ShadowLog.getLogs().all { it.throwable == null })
        }

    private companion object {
        const val ADDRESS = "u1recipient"
        const val OWN_T_ADDRESS = "t1own"
        const val OWN_U_ADDRESS = "u1own"
    }
}
