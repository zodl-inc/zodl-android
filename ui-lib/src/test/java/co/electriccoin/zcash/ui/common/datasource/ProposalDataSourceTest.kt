package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Broadcaster
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.TransactionSizeBound
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.isProposalTooLarge
import co.electriccoin.zcash.ui.common.provider.SignerCapabilities
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The proposal failure mapping the app relies on: since MOB-1723 the SDK reports an
 * insufficient-funds proposal failure and a PCZT-unsupported (TEX) proposal as dedicated types, so
 * this layer maps purely by type - no error-message matching anywhere.
 */
class ProposalDataSourceTest {
    private val account =
        mockk<ZodlAccount> {
            every { sdkAccount } returns mockk(relaxed = true)
            every { isSpendable } returns true
        }

    private val bound = TransactionSizeBound(maxShieldedActions = 42)
    private val signerCapabilities =
        mockk<SignerCapabilitiesProvider> {
            coEvery { forAccount(any()) } returns SignerCapabilities(bound)
        }

    @Test
    fun sdkInsufficientFundsBecomesTheAppInsufficientFundsException() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val sdkException = TransactionEncoderException.InsufficientFundsException(RuntimeException("raw"))
            coEvery { synchronizer.proposeTransfer(any(), any(), any(), any(), any()) } throws sdkException

            val thrown =
                assertFailsWith<InsufficientFundsException> {
                    dataSource(synchronizer).createProposal(account, send())
                }

            assertTrue(thrown.causeChain().any { it === sdkException })
        }

    @Test
    fun anUnmatchedProposalFailureBecomesTransactionProposalNotCreated() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val sdkException = TransactionEncoderException.ProposalFromParametersException(RuntimeException("raw"))
            coEvery { synchronizer.proposeTransfer(any(), any(), any(), any(), any()) } throws sdkException

            val thrown =
                assertFailsWith<TransactionProposalNotCreatedException> {
                    dataSource(synchronizer).createProposal(account, send())
                }

            assertSame(sdkException, thrown.cause)
        }

    @Test
    fun anArbitraryFailureBecomesTransactionProposalNotCreated() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val raw = RuntimeException("anything")
            coEvery { synchronizer.proposeTransfer(any(), any(), any(), any(), any()) } throws raw

            val thrown =
                assertFailsWith<TransactionProposalNotCreatedException> {
                    dataSource(synchronizer).createProposal(account, send())
                }

            assertSame(raw, thrown.cause)
        }

    @Test
    fun anAlreadyMappedFailureIsRethrownWithoutDoubleWrapping() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val alreadyMapped = TransactionProposalNotCreatedException(IllegalArgumentException("Invalid ZIP321 URI"))
            coEvery { synchronizer.proposeTransfer(any(), any(), any(), any(), any()) } throws alreadyMapped

            val thrown =
                assertFailsWith<TransactionProposalNotCreatedException> {
                    dataSource(synchronizer).createProposal(account, send())
                }

            assertSame(alreadyMapped, thrown)
        }

    @Test
    fun aRegularProposalIsProposedWithinTheSignersBound() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            coEvery { synchronizer.proposeTransfer(any(), any(), any(), any(), bound) } returns mockk()

            dataSource(synchronizer).createProposal(account, send())

            coVerify(exactly = 1) { synchronizer.proposeTransfer(any(), any(), any(), any(), bound) }
        }

    @Test
    fun aSwapProposalIsProposedWithinTheSignersBound() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            coEvery { synchronizer.proposeTransfer(any(), any(), any(), any(), bound) } returns mockk()

            dataSource(synchronizer).createExactInputProposal(account, send(), mockk())
            dataSource(synchronizer).createExactOutputProposal(account, send(), mockk())

            coVerify(exactly = 2) { synchronizer.proposeTransfer(any(), any(), any(), any(), bound) }
        }

    @Test
    fun aProposalOverTheBoundSurfacesTheSdkTooLargeFailure() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val tooLarge = TransactionEncoderException.ProposalTooLargeException(RuntimeException("raw"))
            coEvery { synchronizer.proposeTransfer(any(), any(), any(), any(), bound) } throws tooLarge

            val thrown =
                assertFailsWith<TransactionProposalNotCreatedException> {
                    dataSource(synchronizer).createProposal(account, send())
                }

            assertTrue(thrown.isProposalTooLarge())
        }

    @Test
    fun theBoundIsAskedForAtEveryProposal() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val scarce = TransactionSizeBound(maxShieldedActions = TransactionSizeBound.MIN_PROVING_ACTIONS)
            coEvery { synchronizer.proposeTransfer(any(), any(), any(), any(), any()) } returns mockk()
            coEvery { signerCapabilities.forAccount(any()) } returns SignerCapabilities(bound) andThen
                SignerCapabilities(scarce)

            dataSource(synchronizer).createProposal(account, send())
            dataSource(synchronizer).createProposal(account, send())

            coVerifyOrder {
                synchronizer.proposeTransfer(any(), any(), any(), any(), bound)
                synchronizer.proposeTransfer(any(), any(), any(), any(), scarce)
            }
        }

    @Test
    fun aPcztIsCreatedWithTheBoundTheCallerGives() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            coEvery { synchronizer.createPcztFromProposal(any(), any(), bound) } returns mockk()

            dataSource(synchronizer).createPcztFromProposal(account, mockk<Proposal>(), bound)

            coVerify(exactly = 1) { synchronizer.createPcztFromProposal(any(), any(), bound) }
        }

    @Test
    fun aPcztIsConsensusBoundedWhenTheCallerSaysSo() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            coEvery {
                synchronizer.createPcztFromProposal(any(), any(), TransactionSizeBound.CONSENSUS)
            } returns mockk()

            dataSource(synchronizer).createPcztFromProposal(account, mockk<Proposal>(), TransactionSizeBound.CONSENSUS)

            coVerify(exactly = 1) {
                synchronizer.createPcztFromProposal(any(), any(), TransactionSizeBound.CONSENSUS)
            }
        }

    @Test
    fun transactionsAreCreatedWithTheBoundTheCallerGives() =
        runBlocking {
            val broadcaster = mockk<Broadcaster>()
            val synchronizer = mockk<Synchronizer> { every { this@mockk.broadcaster } returns broadcaster }
            coEvery { broadcaster.createProposedTransactions(any(), any(), any(), bound) } returns emptyList()

            val result = dataSource(synchronizer).submitTransaction(mockk<Proposal>(), mockk(), bound)

            coVerify(exactly = 1) { broadcaster.createProposedTransactions(any(), any(), any(), bound) }
            assertTrue(result is SubmitResult.Failure)
        }

    /**
     * The multi-step rejection must reach the caller untouched: only
     * [co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository] knows whether the
     * proposal pays a TEX address and may therefore remap it to [TexUnsupportedOnKSException].
     */
    @Test
    fun aMultiStepProposalFailurePropagatesUntouched() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val sdkException = PcztException.MultiStepProposalUnsupportedException()
            coEvery { synchronizer.createPcztFromProposal(any(), any(), any()) } throws sdkException

            val thrown =
                assertFailsWith<PcztException.MultiStepProposalUnsupportedException> {
                    dataSource(synchronizer).createPcztFromProposal(account, mockk<Proposal>(), bound)
                }

            assertTrue(thrown.causeChain().any { it === sdkException })
        }

    @Test
    fun anyOtherPcztFailurePropagatesUntouched() =
        runBlocking {
            val synchronizer = mockk<Synchronizer>()
            val raw = RuntimeException("PCZT creation failed")
            coEvery { synchronizer.createPcztFromProposal(any(), any(), any()) } throws raw

            val thrown =
                assertFailsWith<RuntimeException> {
                    dataSource(synchronizer).createPcztFromProposal(account, mockk<Proposal>(), bound)
                }

            assertTrue(thrown.causeChain().any { it === raw })
        }

    /**
     * The exception a coroutine hands back can be a stack-trace-recovered copy rather than the very
     * instance that was thrown, so identity is asserted against the whole cause chain.
     */
    private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private fun dataSource(synchronizer: Synchronizer): ProposalDataSource =
        ProposalDataSourceImpl(
            synchronizerProvider = mockk { coEvery { getSynchronizer() } returns synchronizer },
            persistableWalletProvider = mockk(),
            signerCapabilitiesProvider = signerCapabilities
        )

    private suspend fun send(): ZecSend =
        ZecSend(
            destination = WalletAddress.Unified.new(RECIPIENT),
            amount = Zatoshi(AMOUNT),
            memo = Memo(""),
            proposal = null
        )

    private companion object {
        const val RECIPIENT = "recipient"
        const val AMOUNT = 100_000L
    }
}
