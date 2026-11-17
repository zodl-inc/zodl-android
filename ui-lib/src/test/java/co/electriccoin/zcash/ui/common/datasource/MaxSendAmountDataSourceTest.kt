package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.model.MemoContent
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.TransactionSizeBound
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MaxSendAmountDataSourceTest {
    private val account =
        mockk<ZodlAccount> {
            every { sdkAccount } returns mockk(relaxed = true)
        }

    private val recipient = mockk<RecipientAddress>()

    private val bound = TransactionSizeBound(maxShieldedActions = 30)

    @Test
    fun maxSendReportsTheProposalPaymentAmountAndFee() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val memo = MemoContent.fromString("hi")
            val proposal =
                mockk<Proposal> {
                    every { totalPaymentAmount() } returns Zatoshi(90_000)
                    every { totalFeeRequired() } returns Zatoshi(10_000)
                }
            coEvery { synchronizer.proposeSendMax(any(), recipient, memo, bound) } returns proposal

            val result = dataSource(synchronizer).proposeMaxSendAmount(account, recipient, memo, bound)

            assertEquals(MaxSendAmount(Zatoshi(90_000), Zatoshi(10_000)), result)
        }

    @Test
    fun maxSendInsufficientFundsBecomesTheAppInsufficientFundsException() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val sdkException = TransactionEncoderException.InsufficientFundsException(RuntimeException("raw"))
            coEvery { synchronizer.proposeSendMax(any(), any(), any(), any()) } throws sdkException

            val thrown =
                assertFailsWith<InsufficientFundsException> {
                    dataSource(synchronizer).proposeMaxSendAmount(account, recipient, null, bound)
                }

            assertTrue(thrown.causeChain().any { it === sdkException })
        }

    @Test
    fun maxSendFailuresMapToTheirReason() =
        runBlocking {
            val raw = RuntimeException("raw")
            val cases =
                mapOf(
                    TransactionEncoderException.ProposalTooLargeException(raw) to
                        MaxSendAmountFailure.TOO_MANY_NOTES,
                    // The size gate is found wherever it is wrapped
                    IllegalStateException(TransactionEncoderException.ProposalTooLargeException(raw)) to
                        MaxSendAmountFailure.TOO_MANY_NOTES,
                    TransactionEncoderException.ProposalRecipientCannotReceiveMemoException(raw) to
                        MaxSendAmountFailure.INVALID_RECIPIENT,
                    TransactionEncoderException.ProposalUnsupportedRecipientException(raw) to
                        MaxSendAmountFailure.INVALID_RECIPIENT,
                    TransactionEncoderException.ProposalSyncRequiredException(raw) to
                        MaxSendAmountFailure.SYNC_REQUIRED,
                    TransactionEncoderException.ProposalFromParametersException(raw) to
                        MaxSendAmountFailure.OTHER,
                    // Raised only when creating transactions from a proposal, never by proposeSendMax
                    TransactionEncoderException.AnchorNotFoundException(mockk(relaxed = true), raw) to
                        MaxSendAmountFailure.OTHER,
                    IllegalStateException("anything") to MaxSendAmountFailure.OTHER,
                )
            cases.forEach { (failure, reason) ->
                val synchronizer = mockk<Synchronizer>()
                coEvery { synchronizer.proposeSendMax(any(), any(), any(), any()) } throws failure

                val thrown =
                    assertFailsWith<MaxSendAmountUnavailableException> {
                        dataSource(synchronizer).proposeMaxSendAmount(account, recipient, null, bound)
                    }

                assertEquals(reason, thrown.reason, failure::class.simpleName)
                assertTrue(thrown.causeChain().any { it === failure })
            }
        }

    @Test
    fun maxSendCancellationPropagatesUntouched() {
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            coEvery {
                synchronizer.proposeSendMax(any(), any(), any(), any())
            } throws CancellationException("cancelled")

            assertFailsWith<CancellationException> {
                dataSource(synchronizer).proposeMaxSendAmount(account, recipient, null, bound)
            }
        }
    }

    /**
     * The exception a coroutine hands back can be a stack-trace-recovered copy rather than the very
     * instance that was thrown, so identity is asserted against the whole cause chain.
     */
    private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private fun dataSource(synchronizer: Synchronizer): MaxSendAmountDataSource =
        MaxSendAmountDataSourceImpl(
            synchronizerProvider = mockk { coEvery { getSynchronizer() } returns synchronizer }
        )
}
