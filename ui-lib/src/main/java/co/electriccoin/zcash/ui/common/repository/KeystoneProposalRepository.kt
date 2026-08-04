package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.model.Pczt
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
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposalNotCreatedException
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.model.KeystoneFirmwarePolicy
import co.electriccoin.zcash.ui.common.model.KeystoneFirmwareVersion
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.readKeystoneFwStamp
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKException
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
interface KeystoneProposalRepository {
    val transactionProposal: StateFlow<TransactionProposal?>

    val submitState: StateFlow<SubmitProposalState?>

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

    /**
     * Adopts an already-built migration send-max [Proposal] (see
     * [cash.z.ecc.android.sdk.OrchardMigrationSdk.proposeImmediateMigration]) so [createPCZTFromProposal]
     * can build a Keystone PCZT from it exactly as it would from any other proposal.
     */
    fun setMigrationSweepProposal(proposal: Proposal, amount: Zatoshi)

    @Throws(PcztException.CreatePcztFromProposalException::class)
    suspend fun createPCZTFromProposal()

    @Throws(IllegalStateException::class)
    suspend fun createPCZTEncoder(): UREncoder

    /**
     * Parses a Keystone-signed PCZT and enforces the firmware minimum-version gate (MOB-1510).
     * Firmware >= 2.4.6 stamps its raw internal version into the signed PCZT's proprietary
     * fields; this normalizes that stamp to display numbering and refuses signatures from
     * firmware below [KeystoneFirmwareVersion.MINIMUM_SUPPORTED] (or too old to stamp a version
     * at all) before they can reach submission.
     */
    @Throws(ParsePCZTException::class)
    suspend fun parsePCZT(ur: UR)

    suspend fun submit(): SubmitResult

    fun clear()

    suspend fun getTransactionProposal(): TransactionProposal

    fun getProposalPCZT(): Pczt?
}

class ParsePCZTException : Exception()

/**
 * The scanned signed PCZT came from Keystone firmware below
 * [KeystoneFirmwareVersion.MINIMUM_SUPPORTED]. [detected] is `null` when the firmware is too old
 * to stamp its version at all.
 */
class KeystoneFirmwareBelowMinimumException(
    val detected: KeystoneFirmwareVersion?
) : Exception(
        "Keystone firmware ${detected ?: "unstamped"} is below minimum supported " +
            "${KeystoneFirmwareVersion.MINIMUM_SUPPORTED}"
    )

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
    private val keystoneSDKProvider: KeystoneSDKProvider,
) : KeystoneProposalRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val transactionProposal = MutableStateFlow<TransactionProposal?>(null)

    override val submitState = MutableStateFlow<SubmitProposalState?>(null)

    private val pcztWithProofs = MutableStateFlow(PcztState(isLoading = false, pczt = null))
    private var proposalPczt: Pczt? = null
    private var pcztWithSignatures: Pczt? = null

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

    override fun setMigrationSweepProposal(proposal: Proposal, amount: Zatoshi) {
        transactionProposal.update { MigrationSweepTransactionProposal(amount, proposal) }
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
            try {
                keystoneSDKProvider.generatePczt(pczt = redactedPczt.toByteArray())
            } catch (e: KeystoneSDKException) {
                throw IllegalStateException("Failed to generate PCZT encoder", e)
            }
        }

    override suspend fun parsePCZT(ur: UR) =
        withContext(Dispatchers.IO) {
            val parsed =
                try {
                    keystoneSDKProvider.parsePczt(ur)
                } catch (_: Exception) {
                    throw ParsePCZTException()
                }

            val stamp = parsed.readKeystoneFwStamp()
            val detected = stamp?.let(KeystoneFirmwareVersion::fromStamp)
            val outcome = KeystoneFirmwarePolicy.evaluate(detected, KeystoneFirmwareVersion.MINIMUM_SUPPORTED)
            val logMessage = {
                "Keystone firmware on signed PCZT: raw stamp ${stamp ?: "absent"}, normalized " +
                    "${detected ?: "unknown"} (required ${KeystoneFirmwareVersion.MINIMUM_SUPPORTED}) -> $outcome"
            }
            if (outcome != KeystoneFirmwarePolicy.Outcome.OK) {
                Twig.warn(logMessage)
                throw KeystoneFirmwareBelowMinimumException(detected)
            } else {
                Twig.info(logMessage)
            }

            pcztWithSignatures = Pczt(parsed)
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
