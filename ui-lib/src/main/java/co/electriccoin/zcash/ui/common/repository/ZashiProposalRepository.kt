package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.ExactInputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ExactOutputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.MigrationSweepTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposalNotCreatedException
import co.electriccoin.zcash.ui.common.datasource.ZashiSpendingKeyDataSource
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.datasource.sizeBoundFor
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

interface ZashiProposalRepository {
    val transactionProposal: StateFlow<TransactionProposal?>

    val submitState: StateFlow<SubmitProposalState?>

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createProposal(zecSend: ZecSend): RegularTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createZip321Proposal(zip321Uri: String): Zip321TransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createExactInputSwapProposal(zecSend: ZecSend, quote: SwapQuote): ExactInputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createExactOutputSwapProposal(zecSend: ZecSend, quote: SwapQuote): ExactOutputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createShieldProposal()

    /**
     * Adopt an already-built IMMEDIATE send-max migration [proposal] as the current transaction, so
     * the migration sweep goes through the shared submit + Transaction Progress pipeline exactly
     * like a regular send (mirrors [KeystoneProposalRepository.setMigrationSweepProposal]).
     */
    fun setMigrationSweepProposal(proposal: Proposal, amount: Zatoshi)

    suspend fun submit(): SubmitResult

    suspend fun getTransactionProposal(): TransactionProposal

    fun clear()
}

@Suppress("TooManyFunctions")
class ZashiProposalRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val proposalDataSource: ProposalDataSource,
    private val zashiSpendingKeyDataSource: ZashiSpendingKeyDataSource,
    private val signerCapabilitiesProvider: SignerCapabilitiesProvider,
) : ZashiProposalRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val transactionProposal = MutableStateFlow<TransactionProposal?>(null)

    override val submitState = MutableStateFlow<SubmitProposalState?>(null)

    override suspend fun createProposal(zecSend: ZecSend): RegularTransactionProposal =
        createProposalInternal {
            proposalDataSource.createProposal(
                account = accountDataSource.getSelectedAccount(),
                send = zecSend
            )
        }

    override suspend fun createZip321Proposal(zip321Uri: String): Zip321TransactionProposal =
        createProposalInternal {
            proposalDataSource.createZip321Proposal(
                account = accountDataSource.getSelectedAccount(),
                zip321Uri = zip321Uri
            )
        }

    override suspend fun createExactInputSwapProposal(
        zecSend: ZecSend,
        quote: SwapQuote,
    ): ExactInputSwapTransactionProposal =
        createProposalInternal {
            proposalDataSource.createExactInputProposal(
                account = accountDataSource.getSelectedAccount(),
                send = zecSend,
                quote = quote
            )
        }

    override suspend fun createExactOutputSwapProposal(
        zecSend: ZecSend,
        quote: SwapQuote,
    ): ExactOutputSwapTransactionProposal =
        createProposalInternal {
            proposalDataSource.createExactOutputProposal(
                account = accountDataSource.getSelectedAccount(),
                send = zecSend,
                quote = quote
            )
        }

    override suspend fun createShieldProposal() {
        createProposalInternal {
            proposalDataSource.createShieldProposal(
                account = accountDataSource.getSelectedAccount(),
            )
        }
    }

    override fun setMigrationSweepProposal(proposal: Proposal, amount: Zatoshi) {
        transactionProposal.update { MigrationSweepTransactionProposal(amount, proposal) }
    }

    @Suppress("TooGenericExceptionCaught", "UseCheckOrError")
    override suspend fun submit(): SubmitResult =
        scope
            .async {
                val transactionProposal = transactionProposal.value
                if (transactionProposal == null) {
                    val cause = IllegalStateException("Transaction proposal is null")
                    submitState.update { SubmitProposalState.Result(SubmitResult.Error(cause)) }
                    throw cause
                } else {
                    submitState.update { SubmitProposalState.Submitting }
                    try {
                        val result =
                            proposalDataSource.submitTransaction(
                                proposal = transactionProposal.proposal,
                                usk = zashiSpendingKeyDataSource.getZashiSpendingKey(),
                                sizeBound =
                                    signerCapabilitiesProvider.sizeBoundFor(
                                        account = accountDataSource.getSelectedAccount(),
                                        proposal = transactionProposal
                                    )
                            )
                        submitState.update { SubmitProposalState.Result(result) }
                        if (result is SubmitResult.Success) signerCapabilitiesProvider.endFlow()
                        result
                    } catch (e: Exception) {
                        submitState.update { SubmitProposalState.Result(SubmitResult.Error(e)) }
                        throw e
                    }
                }
            }.await()

    override suspend fun getTransactionProposal(): TransactionProposal = transactionProposal.filterNotNull().first()

    override fun clear() {
        transactionProposal.update { null }
        submitState.update { null }
    }

    private inline fun <T : TransactionProposal> createProposalInternal(block: () -> T): T {
        val proposal =
            try {
                block()
            } catch (e: TransactionProposalNotCreatedException) {
                Twig.error(e) { "Unable to create proposal" }
                transactionProposal.update { null }
                throw e
            } catch (e: InsufficientFundsException) {
                Twig.error(e) { "Insufficient funds" }
                transactionProposal.update { null }
                throw e
            }
        transactionProposal.update { proposal }
        return proposal
    }
}
