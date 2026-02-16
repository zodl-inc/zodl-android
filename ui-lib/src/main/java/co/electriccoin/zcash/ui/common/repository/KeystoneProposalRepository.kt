package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.ExactInputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ExactOutputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposalNotCreatedException
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import com.keystone.sdk.KeystoneSDK
import com.keystone.sdk.KeystoneZcashSDK
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
interface KeystoneProposalRepository {
    val transactionProposal: Flow<TransactionProposal?>

    val submitState: Flow<SubmitProposalState?>

    @Throws(
        TransactionProposalNotCreatedException::class,
        InsufficientFundsException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createProposal(zecSend: ZecSend)

    @Throws(
        TransactionProposalNotCreatedException::class,
        InsufficientFundsException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createExactInputSwapProposal(zecSend: ZecSend, quote: SwapQuote): ExactInputSwapTransactionProposal

    @Throws(
        TransactionProposalNotCreatedException::class,
        InsufficientFundsException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createExactOutputSwapProposal(zecSend: ZecSend, quote: SwapQuote): ExactOutputSwapTransactionProposal

    @Throws(
        TransactionProposalNotCreatedException::class,
        InsufficientFundsException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createZip321Proposal(zip321Uri: String): Zip321TransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createShieldProposal()

    @Throws(PcztException.CreatePcztFromProposalException::class)
    suspend fun createPCZTFromProposal()

    @Throws(IllegalStateException::class)
    suspend fun createPCZTEncoder(): UREncoder

    @Throws(ParsePCZTException::class)
    suspend fun parsePCZT(ur: UR)

    suspend fun submit(): SubmitResult

    fun clear()

    suspend fun getTransactionProposal(): TransactionProposal

    fun getProposalPCZT(): Pczt?
}

class ParsePCZTException : Exception()

sealed interface SubmitProposalState {
    data object Submitting : SubmitProposalState

    data class Result(
        val submitResult: SubmitResult
    ) : SubmitProposalState
}

@Suppress("TooManyFunctions")
class KeystoneProposalRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val proposalDataSource: ProposalDataSource,
) : KeystoneProposalRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val transactionProposal = MutableStateFlow<TransactionProposal?>(null)

    override val submitState = MutableStateFlow<SubmitProposalState?>(null)

    private val pcztWithProofs = MutableStateFlow(PcztState(isLoading = false, pczt = null))
    private var proposalPczt: Pczt? = null
    private var pcztWithSignatures: Pczt? = null

    private val keystoneSDK: KeystoneSDK by lazy { KeystoneSDK() }
    private val keystoneZcashSDK: KeystoneZcashSDK
        get() = keystoneSDK.zcash
    private var pcztWithProofsJob: Job? = null

    override suspend fun createProposal(zecSend: ZecSend) {
        createProposalInternal {
            proposalDataSource.createProposal(
                account = accountDataSource.getSelectedAccount(),
                send = zecSend
            )
        }
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

    override suspend fun createZip321Proposal(zip321Uri: String): Zip321TransactionProposal =
        createProposalInternal {
            proposalDataSource.createZip321Proposal(
                account = accountDataSource.getSelectedAccount(),
                zip321Uri = zip321Uri
            )
        }

    override suspend fun createShieldProposal() {
        createProposalInternal {
            proposalDataSource.createShieldProposal(
                account = accountDataSource.getSelectedAccount(),
            )
        }
    }

    override suspend fun createPCZTFromProposal() {
        val result =
            proposalDataSource.createPcztFromProposal(
                account = accountDataSource.getSelectedAccount(),
                proposal = getTransactionProposal().proposal
            )
        proposalPczt = result
        addProofsToPczt(result)
    }

    private fun addProofsToPczt(proposalPczt: Pczt) {
        pcztWithProofsJob?.cancel()
        pcztWithProofsJob =
            scope.launch {
                pcztWithProofs.update { PcztState(isLoading = true, pczt = null) }
                try {
                    val result = proposalDataSource.addProofsToPczt(proposalPczt.clonePczt())
                    pcztWithProofs.update { PcztState(isLoading = false, pczt = result) }
                } catch (_: PcztException.AddProofsToPcztException) {
                    pcztWithProofs.update { PcztState(isLoading = false, pczt = null) }
                }
            }
    }

    @Suppress("UseCheckOrError")
    override suspend fun createPCZTEncoder(): UREncoder =
        withContext(Dispatchers.IO) {
            val pczt = proposalPczt ?: throw IllegalStateException("Proposal not created")
            val redactedPczt = proposalDataSource.redactPcztForSigner(pczt.clonePczt())
            keystoneZcashSDK.generatePczt(pczt = redactedPczt.toByteArray())
        }

    override suspend fun parsePCZT(ur: UR) =
        withContext(Dispatchers.IO) {
            try {
                pcztWithSignatures = Pczt(keystoneZcashSDK.parsePczt(ur))
            } catch (_: Exception) {
                throw ParsePCZTException()
            }
        }

    @Suppress("UseCheckOrError", "ThrowingExceptionsWithoutMessageOrCause", "TooGenericExceptionCaught")
    override suspend fun submit(): SubmitResult =
        scope
            .async {
                val transactionProposal = transactionProposal.value
                val pcztWithSignatures = pcztWithSignatures

                if (transactionProposal == null || pcztWithSignatures == null) {
                    val cause = IllegalStateException("Transaction proposal is null")
                    submitState.update { SubmitProposalState.Result(SubmitResult.Error(cause)) }
                    throw cause
                } else {
                    submitState.update { SubmitProposalState.Submitting }
                    val pcztWithProofs = pcztWithProofs.filter { !it.isLoading }.first().pczt
                    if (pcztWithProofs == null) {
                        val cause = IllegalStateException("PCZT with proofs is null")
                        submitState.update { SubmitProposalState.Result(SubmitResult.Error(cause)) }
                        throw cause
                    } else {
                        try {
                            val result =
                                proposalDataSource.submitTransaction(
                                    pcztWithProofs = pcztWithProofs,
                                    pcztWithSignatures = pcztWithSignatures
                                )
                            submitState.update { SubmitProposalState.Result(result) }
                            result
                        } catch (e: Exception) {
                            val result = SubmitResult.Error(e)
                            submitState.update { SubmitProposalState.Result(result) }
                            throw e
                        }
                    }
                }
            }.await()

    override suspend fun getTransactionProposal(): TransactionProposal = transactionProposal.filterNotNull().first()

    override fun getProposalPCZT(): Pczt? = proposalPczt

    override fun clear() {
        pcztWithProofsJob?.cancel()
        pcztWithProofsJob = null
        pcztWithProofs.update { PcztState(isLoading = false, pczt = null) }

        transactionProposal.update { null }
        submitState.update { null }
        proposalPczt = null
        pcztWithSignatures = null
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

private data class PcztState(
    val isLoading: Boolean,
    val pczt: Pczt?
)
