package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.model.proposeSend
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.ConnectionMode
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.NetworkDimension
import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zecdev.zip321.ZIP321
import org.zecdev.zip321.parser.ParserContext
import java.math.BigDecimal

interface ProposalDataSource {
    @Throws(
        TransactionProposalNotCreatedException::class,
        InsufficientFundsException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createProposal(account: WalletAccount, send: ZecSend): RegularTransactionProposal

    @Throws(
        TransactionProposalNotCreatedException::class,
        InsufficientFundsException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createZip321Proposal(account: WalletAccount, zip321Uri: String): Zip321TransactionProposal

    @Throws(
        TransactionProposalNotCreatedException::class,
        InsufficientFundsException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createExactInputProposal(
        account: WalletAccount,
        send: ZecSend,
        quote: SwapQuote,
    ): ExactInputSwapTransactionProposal

    @Throws(
        TransactionProposalNotCreatedException::class,
        InsufficientFundsException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createExactOutputProposal(
        account: WalletAccount,
        send: ZecSend,
        quote: SwapQuote,
    ): ExactOutputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createShieldProposal(account: WalletAccount): ShieldTransactionProposal

    @Throws(PcztException.CreatePcztFromProposalException::class)
    suspend fun createPcztFromProposal(account: WalletAccount, proposal: Proposal): Pczt

    @Throws(PcztException.AddProofsToPcztException::class)
    suspend fun addProofsToPczt(pczt: Pczt): Pczt

    suspend fun submitTransaction(pcztWithProofs: Pczt, pcztWithSignatures: Pczt): SubmitResult

    suspend fun submitTransaction(proposal: Proposal, usk: UnifiedSpendingKey): SubmitResult

    @Throws(PcztException.RedactPcztForSignerException::class)
    suspend fun redactPcztForSigner(pczt: Pczt): Pczt
}

class TransactionProposalNotCreatedException(
    cause: Exception
) : Exception(cause)

class InsufficientFundsException : Exception()

class TexUnsupportedOnKSException : Exception("TEX addresses are unsupported on Keystone")

@Suppress("TooManyFunctions")
class ProposalDataSourceImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val serverSelectionProvider: ServerSelectionProvider,
) : ProposalDataSource {
    override suspend fun createProposal(account: WalletAccount, send: ZecSend): RegularTransactionProposal =
        withContext(Dispatchers.IO) {
            getOrThrow { synchronizer ->
                validate(account, synchronizer, send.destination.address)
                RegularTransactionProposal(
                    destination = send.destination,
                    amount = send.amount,
                    memo = send.memo,
                    proposal = synchronizer.proposeSend(account = account.sdkAccount, send = send),
                )
            }
        }

    override suspend fun createZip321Proposal(account: WalletAccount, zip321Uri: String): Zip321TransactionProposal =
        withContext(Dispatchers.IO) {
            getOrThrow { synchronizer ->
                val payment =
                    when (
                        val request =
                            ZIP321
                                .request(
                                    uriString = zip321Uri,
                                    context =
                                        when (VersionInfo.NETWORK_DIMENSION) {
                                            NetworkDimension.MAINNET -> ParserContext.MAINNET
                                            NetworkDimension.TESTNET -> ParserContext.TESTNET
                                        },
                                    validatingRecipients = null
                                )
                    ) {
                        is ZIP321.ParserResult.Request -> request.paymentRequest.payments[0]

                        else -> throw TransactionProposalNotCreatedException(
                            IllegalArgumentException("Invalid ZIP321 URI"),
                        )
                    }

                validate(account, synchronizer, payment.recipientAddress.value)

                if (payment.nonNegativeAmount == null) {
                    throw TransactionProposalNotCreatedException(IllegalArgumentException("Null amount"))
                }

                val destination =
                    synchronizer
                        .validateAddress(payment.recipientAddress.value)
                        .toWalletAddress(payment.recipientAddress.value)

                Zip321TransactionProposal(
                    destination = destination,
                    amount =
                        payment.nonNegativeAmount
                            ?.toZecValueString()
                            ?.toBigDecimal()
                            .convertZecToZatoshi(),
                    memo = Memo(payment.memo?.data?.decodeToString() ?: ""),
                    proposal = synchronizer.proposeFulfillingPaymentUri(account = account.sdkAccount, uri = zip321Uri),
                )
            }
        }

    override suspend fun createExactInputProposal(
        account: WalletAccount,
        send: ZecSend,
        quote: SwapQuote,
    ): ExactInputSwapTransactionProposal =
        withContext(Dispatchers.IO) {
            getOrThrow { synchronizer ->
                validate(account, synchronizer, send.destination.address)
                ExactInputSwapTransactionProposal(
                    destination = send.destination,
                    amount = send.amount,
                    memo = send.memo,
                    proposal = synchronizer.proposeSend(account.sdkAccount, send),
                    quote = quote,
                )
            }
        }

    override suspend fun createExactOutputProposal(
        account: WalletAccount,
        send: ZecSend,
        quote: SwapQuote,
    ): ExactOutputSwapTransactionProposal =
        withContext(Dispatchers.IO) {
            getOrThrow { synchronizer ->
                validate(account, synchronizer, send.destination.address)
                ExactOutputSwapTransactionProposal(
                    destination = send.destination,
                    amount = send.amount,
                    memo = send.memo,
                    proposal = synchronizer.proposeSend(account.sdkAccount, send),
                    quote = quote,
                )
            }
        }

    override suspend fun createShieldProposal(account: WalletAccount): ShieldTransactionProposal =
        withContext(Dispatchers.IO) {
            getOrThrow { synchronizer ->
                val newProposal =
                    synchronizer.proposeShielding(
                        account = account.sdkAccount,
                        shieldingThreshold = Zatoshi(DEFAULT_SHIELDING_THRESHOLD),
                        // Using empty string for memo to clear the default memo prefix value defined in the SDK
                        memo = "",
                        // Using null will select whichever of the account's trans. receivers has funds to shield
                        transparentReceiver = null
                    )

                newProposal
                    ?.let { ShieldTransactionProposal(proposal = it) }
                    ?: throw NullPointerException("transparent balance is zero or below `shieldingThreshold`")
            }
        }

    override suspend fun createPcztFromProposal(account: WalletAccount, proposal: Proposal): Pczt =
        withContext(Dispatchers.IO) {
            val synchronizer = synchronizerProvider.getSynchronizer()
            synchronizer.createPcztFromProposal(
                accountUuid = account.sdkAccount.accountUuid,
                proposal = proposal
            )
        }

    override suspend fun addProofsToPczt(pczt: Pczt): Pczt =
        withContext(Dispatchers.IO) {
            synchronizerProvider
                .getSynchronizer()
                .addProofsToPczt(pczt)
        }

    override suspend fun submitTransaction(pcztWithProofs: Pczt, pcztWithSignatures: Pczt): SubmitResult =
        submitTransactionInternal(MULTI_SUBMIT_PCZT_TAG) {
            it.broadcaster.createTransactionFromPczt(
                pcztWithProofs = pcztWithProofs,
                pcztWithSignatures = pcztWithSignatures,
            )
        }

    override suspend fun submitTransaction(proposal: Proposal, usk: UnifiedSpendingKey): SubmitResult =
        submitTransactionInternal(MULTI_SUBMIT_TAG) {
            it.broadcaster.createProposedTransactions(
                proposal = proposal,
                usk = usk,
            )
        }

    override suspend fun redactPcztForSigner(pczt: Pczt): Pczt =
        withContext(Dispatchers.IO) {
            synchronizerProvider
                .getSynchronizer()
                .redactPcztForSigner(pczt)
        }

    /**
     * @throws TexUnsupportedOnKSException if the address is a TEX address and the account is a Keystone account
     */
    @Throws(TexUnsupportedOnKSException::class)
    private suspend fun validate(
        account: WalletAccount,
        synchronizer: Synchronizer,
        address: String
    ) {
        if (account is KeystoneAccount && synchronizer.validateAddress(address) == AddressType.Tex) {
            throw TexUnsupportedOnKSException()
        }
    }

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    private suspend fun submitTransactionInternal(
        logTag: String,
        block: suspend (SdkSynchronizer) -> List<CreatedTransaction>
    ): SubmitResult =
        withContext(Dispatchers.IO) {
            val synchronizer = synchronizerProvider.getSynchronizer() as SdkSynchronizer
            val transactions = block(synchronizer)

            if (transactions.isEmpty()) {
                return@withContext SubmitResult.Failure(
                    txIds = emptyList(),
                    code = MULTI_SUBMIT_EMPTY_TRANSACTION_CODE,
                    description = MULTI_SUBMIT_EMPTY_TRANSACTION_DESCRIPTION
                )
            }

            val endpoints = getSubmissionEndpoints()
            Twig.info {
                "$logTag Created ${transactions.size} transaction(s); submitting to ${endpoints.size} endpoint(s)."
            }

            val submitResults =
                submitCreatedTransactions(
                    synchronizer = synchronizer,
                    transactions = transactions,
                    endpoints = endpoints,
                    logTag = logTag
                )

            Twig.debug { "Internal transaction submit results: $submitResults" }

            val result = submitResults.toSubmitResult()
            synchronizer.refreshTransactions()
            synchronizer.refreshAllBalances()
            Twig.debug { "Transaction submit result: $result" }
            result
        }

    private suspend fun submitCreatedTransactions(
        synchronizer: SdkSynchronizer,
        transactions: List<CreatedTransaction>,
        endpoints: List<LightWalletEndpoint>,
        logTag: String
    ): List<TransactionSubmitResult> =
        MultiEndpointTransactionSubmitter(
            submit = { transaction, endpoint ->
                synchronizer.broadcaster.submit(transaction, endpoint)
            }
        ).submitTransactions(
            transactions = transactions,
            endpoints = endpoints,
            logTag = logTag
        )

    private suspend fun getSubmissionEndpoints(): List<LightWalletEndpoint> {
        val selection = serverSelectionProvider.getServerSelection() ?: ServerSelection.automatic()

        return when (selection.mode) {
            ConnectionMode.AUTOMATIC -> lightWalletEndpointProvider.getEndpoints()
            ConnectionMode.MANUAL -> listOf(checkNotNull(selection.endpoint))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <T : Any> getOrThrow(block: (Synchronizer) -> T): T =
        try {
            val synchronizer = synchronizerProvider.getSynchronizer()
            block(synchronizer)
        } catch (e: TransactionEncoderException.ProposalFromParametersException) {
            val message = e.rootCause.message ?: ""
            if (message.contains("Insufficient balance", true) ||
                message.contains("The transaction requires an additional change output of", true)
            ) {
                throw InsufficientFundsException()
            } else {
                throw TransactionProposalNotCreatedException(e)
            }
        } catch (e: TexUnsupportedOnKSException) {
            throw e
        } catch (e: TransactionProposalNotCreatedException) {
            throw e
        } catch (e: Exception) {
            throw TransactionProposalNotCreatedException(e)
        }

    private suspend fun AddressType.toWalletAddress(value: String) =
        when (this) {
            AddressType.Unified -> WalletAddress.Unified.new(value)
            AddressType.Shielded -> WalletAddress.Sapling.new(value)
            AddressType.Transparent -> WalletAddress.Transparent.new(value)
            AddressType.Tex -> WalletAddress.Tex.new(value)
            is AddressType.Invalid -> error("Invalid address type")
        }
}

internal fun List<TransactionSubmitResult>.toSubmitResult(): SubmitResult {
    if (isEmpty()) {
        return SubmitResult.Failure(
            txIds = emptyList(),
            code = MULTI_SUBMIT_EMPTY_TRANSACTION_CODE,
            description = MULTI_SUBMIT_EMPTY_TRANSACTION_DESCRIPTION
        )
    }

    val successCount = count { it is TransactionSubmitResult.Success }
    val txIds = map { it.txIdString() }
    val failures = filterIsInstance<TransactionSubmitResult.Failure>()
    val hasNotAttempted = any { it is TransactionSubmitResult.NotAttempted }
    val hasTimeoutFailure =
        failures.any { it.grpcError && it.description == MULTI_SUBMIT_TIMEOUT_DESCRIPTION }
    val grpcFailureReason =
        if (hasTimeoutFailure) {
            SubmitResult.GrpcFailure.Reason.TIMEOUT
        } else {
            null
        }
    val grpcFailureDescription =
        if (hasTimeoutFailure) {
            MULTI_SUBMIT_TIMEOUT_DESCRIPTION
        } else {
            null
        }

    val (errCode, errDesc) =
        failures
            .firstOrNull { !it.grpcError }
            ?.let { it.code to it.description } ?: (0 to "")

    return when (successCount) {
        0 -> {
            if (failures.size == size && failures.all { it.grpcError }) {
                SubmitResult.GrpcFailure(
                    txIds = txIds,
                    description = grpcFailureDescription,
                    reason = grpcFailureReason
                )
            } else if (hasNotAttempted && failures.none { !it.grpcError }) {
                SubmitResult.Partial(txIds = txIds, statuses = map { it.statusDescription() })
            } else {
                SubmitResult.Failure(txIds = txIds, code = errCode, description = errDesc)
            }
        }

        txIds.size -> {
            SubmitResult.Success(txIds = txIds)
        }

        else -> {
            SubmitResult.Partial(txIds = txIds, statuses = map { it.statusDescription() })
        }
    }
}

private fun TransactionSubmitResult.statusDescription() =
    when (this) {
        is TransactionSubmitResult.Success -> {
            "success"
        }

        is TransactionSubmitResult.Failure -> {
            if (grpcError) {
                if (description == MULTI_SUBMIT_TIMEOUT_DESCRIPTION) {
                    MULTI_SUBMIT_TIMEOUT_DESCRIPTION
                } else {
                    MULTI_SUBMIT_GRPC_FAILURE_STATUS
                }
            } else {
                "code: $code desc: $description"
            }
        }

        is TransactionSubmitResult.NotAttempted -> {
            "notAttempted"
        }
    }

sealed interface TransactionProposal {
    val proposal: Proposal
}

sealed interface SendTransactionProposal : TransactionProposal {
    val destination: WalletAddress
    val amount: Zatoshi
    val memo: Memo
}

sealed interface SwapTransactionProposal : SendTransactionProposal {
    val quote: SwapQuote

    val totalFees: Zatoshi
        get() = quote.getTotalFeesZatoshi(proposal)

    val totalFeesUsd: BigDecimal
        get() = quote.getTotalFeesUsd(proposal)
}

data class ShieldTransactionProposal(
    override val proposal: Proposal,
) : TransactionProposal

data class RegularTransactionProposal(
    override val destination: WalletAddress,
    override val amount: Zatoshi,
    override val memo: Memo,
    override val proposal: Proposal
) : SendTransactionProposal

data class Zip321TransactionProposal(
    override val destination: WalletAddress,
    override val amount: Zatoshi,
    override val memo: Memo,
    override val proposal: Proposal
) : SendTransactionProposal

data class ExactInputSwapTransactionProposal(
    override val destination: WalletAddress,
    override val amount: Zatoshi,
    override val memo: Memo,
    override val proposal: Proposal,
    override val quote: SwapQuote,
) : SwapTransactionProposal

data class ExactOutputSwapTransactionProposal(
    override val destination: WalletAddress,
    override val amount: Zatoshi,
    override val memo: Memo,
    override val proposal: Proposal,
    override val quote: SwapQuote,
) : SwapTransactionProposal

private const val DEFAULT_SHIELDING_THRESHOLD = 100000L
private const val MULTI_SUBMIT_TAG = "[MultiSubmit]"
private const val MULTI_SUBMIT_PCZT_TAG = "[MultiSubmit/PCZT]"
private const val MULTI_SUBMIT_EMPTY_TRANSACTION_CODE = -1
private const val MULTI_SUBMIT_EMPTY_TRANSACTION_DESCRIPTION = "No transactions created"
private const val MULTI_SUBMIT_GRPC_FAILURE_STATUS = "grpcFailure"
