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
import cash.z.ecc.android.sdk.model.SubmitRejection
import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.TransactionSizeBound
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.lightwallet.client.health.OhttpHealthCenter
import co.electriccoin.lightwallet.client.health.runOhttpOperation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.NetworkDimension
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.requireSpendable
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.zecdev.zip321.ZIP321
import org.zecdev.zip321.parser.ParserContext
import java.math.BigDecimal

interface ProposalDataSource {
    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createProposal(account: WalletAccount, send: ZecSend): RegularTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createZip321Proposal(account: WalletAccount, zip321Uri: String): Zip321TransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createExactInputProposal(
        account: WalletAccount,
        send: ZecSend,
        quote: SwapQuote,
    ): ExactInputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createExactOutputProposal(
        account: WalletAccount,
        send: ZecSend,
        quote: SwapQuote,
    ): ExactOutputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createShieldProposal(account: WalletAccount): ShieldTransactionProposal

    /**
     * Propagates the SDK's [PcztException.MultiStepProposalUnsupportedException] untouched: whether a
     * multi-step rejection means "TEX is unsupported on Keystone" depends on what the proposal was
     * for, which only the caller knows (see
     * [co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository.createPCZTFromProposal]).
     */
    @Throws(
        PcztException.CreatePcztFromProposalException::class,
        PcztException.MultiStepProposalUnsupportedException::class
    )
    suspend fun createPcztFromProposal(
        account: WalletAccount,
        proposal: Proposal,
        sizeBound: TransactionSizeBound
    ): Pczt

    @Throws(PcztException.AddProofsToPcztException::class)
    suspend fun addProofsToPczt(pczt: Pczt): Pczt

    suspend fun submitTransaction(pcztWithProofs: Pczt, pcztWithSignatures: Pczt): SubmitResult

    suspend fun submitTransaction(
        proposal: Proposal,
        usk: UnifiedSpendingKey,
        sizeBound: TransactionSizeBound
    ): SubmitResult

    @Throws(PcztException.RedactPcztForSignerException::class)
    suspend fun redactPcztForSigner(pczt: Pczt): Pczt
}

class TransactionProposalNotCreatedException(
    cause: Exception
) : Exception(cause)

class InsufficientFundsException(
    cause: Throwable? = null
) : Exception(cause)

class TexUnsupportedOnKSException : Exception("TEX addresses are unsupported on Keystone")

/**
 * Every proposal it creates is for an account that can spend: a view-only account is refused with
 * [co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException] before the SDK is
 * asked, whichever flow got this far.
 */
@Suppress("TooManyFunctions")
class ProposalDataSourceImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val signerCapabilitiesProvider: SignerCapabilitiesProvider,
) : ProposalDataSource {
    override suspend fun createProposal(account: WalletAccount, send: ZecSend): RegularTransactionProposal =
        withContext(Dispatchers.IO) {
            account.requireSpendable()
            getOrThrow { synchronizer ->
                RegularTransactionProposal(
                    destination = send.destination,
                    amount = send.amount,
                    memo = send.memo,
                    proposal = synchronizer.proposeSend(account, send),
                )
            }
        }

    override suspend fun createZip321Proposal(account: WalletAccount, zip321Uri: String): Zip321TransactionProposal =
        withContext(Dispatchers.IO) {
            account.requireSpendable()
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
                    proposal =
                        synchronizer.proposeFulfillingPaymentUri(
                            account = account.sdkAccount,
                            uri = zip321Uri,
                            sizeBound = sizeBoundOf(account)
                        ),
                )
            }
        }

    override suspend fun createExactInputProposal(
        account: WalletAccount,
        send: ZecSend,
        quote: SwapQuote,
    ): ExactInputSwapTransactionProposal =
        withContext(Dispatchers.IO) {
            account.requireSpendable()
            getOrThrow { synchronizer ->
                ExactInputSwapTransactionProposal(
                    destination = send.destination,
                    amount = send.amount,
                    memo = send.memo,
                    proposal = synchronizer.proposeSend(account, send),
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
            account.requireSpendable()
            getOrThrow { synchronizer ->
                ExactOutputSwapTransactionProposal(
                    destination = send.destination,
                    amount = send.amount,
                    memo = send.memo,
                    proposal = synchronizer.proposeSend(account, send),
                    quote = quote,
                )
            }
        }

    override suspend fun createShieldProposal(account: WalletAccount): ShieldTransactionProposal =
        withContext(Dispatchers.IO) {
            account.requireSpendable()
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

    override suspend fun createPcztFromProposal(
        account: WalletAccount,
        proposal: Proposal,
        sizeBound: TransactionSizeBound
    ): Pczt =
        withContext(Dispatchers.IO) {
            account.requireSpendable()
            synchronizerProvider.getSynchronizer().createPcztFromProposal(
                accountUuid = account.sdkAccount.accountUuid,
                proposal = proposal,
                sizeBound = sizeBound
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

    override suspend fun submitTransaction(
        proposal: Proposal,
        usk: UnifiedSpendingKey,
        sizeBound: TransactionSizeBound
    ): SubmitResult =
        submitTransactionInternal(MULTI_SUBMIT_TAG) {
            it.broadcaster.createProposedTransactions(
                proposal = proposal,
                usk = usk,
                sizeBound = sizeBound,
            )
        }

    override suspend fun redactPcztForSigner(pczt: Pczt): Pczt =
        withContext(Dispatchers.IO) {
            synchronizerProvider
                .getSynchronizer()
                .redactPcztForSigner(pczt)
        }

    /**
     * One explicit user send: its OHTTP requests are attributed to it, and when it ends the health center is told
     * whether it failed and which call decided that, so the private-connection outage detection can tell a send that
     * failed because the private route is down from one that failed for any other reason.
     */
    private suspend fun submitTransactionInternal(
        logTag: String,
        block: suspend (Synchronizer) -> List<CreatedTransaction>
    ): SubmitResult =
        OhttpHealthCenter.shared.runOhttpOperation(isFailure = { it.isOperationFailure() }) {
            submitTransactionAttributed(logTag, block)
        }

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    private suspend fun submitTransactionAttributed(
        logTag: String,
        block: suspend (Synchronizer) -> List<CreatedTransaction>
    ): SubmitResult =
        withContext(Dispatchers.IO) {
            val synchronizer = synchronizerProvider.getSynchronizer()
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

            // Whether a transaction failed is the SDK's verdict over every endpoint it was submitted to, not the
            // refusal one endpoint returned: read it once every submission has settled.
            val result = submitResults.toSubmitResult(rejections = synchronizer.rejectedTransactions.first())

            if (synchronizer is SdkSynchronizer) {
                synchronizer.refreshTransactions()
                synchronizer.refreshAllBalances()
            }

            Twig.debug { "Transaction submit result: $result" }
            result
        }

    private suspend fun submitCreatedTransactions(
        synchronizer: Synchronizer,
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

    /**
     * Resolves which endpoint a created transaction is broadcast to. Returns only the current
     * endpoint - the one the wallet is actively using for sync and queries. In automatic mode this
     * is the OHTTP gateway when available (chosen by ServerFallbackPolicy), or a manually-selected
     * fallback when OHTTP is down.
     *
     * In automatic mode, submit ONLY to the current endpoint (OHTTP gateway when
     * available). Never broadcast to multiple servers - that would expose the tx to all operators
     * and defeat OHTTP's IP hiding. The current endpoint already provides the right privacy/Tor
     * routing, as determined by ServerFallbackPolicy.
     */
    internal suspend fun getSubmissionEndpoints(): List<LightWalletEndpoint> {
        val currentEndpoint = persistableWalletProvider.requirePersistableWallet().endpoint
        return listOf(currentEndpoint)
    }

    private suspend fun sizeBoundOf(account: WalletAccount): TransactionSizeBound =
        signerCapabilitiesProvider.forAccount(account).sizeBound

    /** Proposes [send] from [account] within the size bound its signer can handle. */
    private suspend fun Synchronizer.proposeSend(account: WalletAccount, send: ZecSend): Proposal =
        proposeTransfer(
            account = account.sdkAccount,
            recipient = send.destination.address,
            amount = send.amount,
            memo = send.memo.value,
            sizeBound = sizeBoundOf(account)
        )

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <T : Any> getOrThrow(block: (Synchronizer) -> T): T =
        try {
            val synchronizer = synchronizerProvider.getSynchronizer()
            block(synchronizer)
        } catch (e: TransactionEncoderException.InsufficientFundsException) {
            throw InsufficientFundsException(e)
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

/**
 * The [SubmitResult] of a proposal's transactions, from what the multi-endpoint submission returned for each and the
 * SDK's verdict ([Synchronizer.rejectedTransactions], given as [rejections], or a [TransactionSubmitResult.Failure]
 * carrying that verdict in its `rejection`). A `Success` from any endpoint is an acceptance; transport failures are
 * inconclusive and stay pending.
 *
 * A transaction failed only when the SDK recorded it as rejected: every endpoint it was submitted to answered, none
 * accepted it and one refused it for good. A refusal for good returned here is one endpoint's answer, so without that
 * verdict the transaction stays pending (the SDK keeps resubmitting it). Orchard to Ironwood migration transactions
 * are never in [rejections].
 */
@Suppress("CyclomaticComplexMethod")
internal fun List<TransactionSubmitResult>.toSubmitResult(
    rejections: Map<TransactionId, SubmitRejection>
): SubmitResult {
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
    // The transactions the SDK rejected, each with the refusal it recorded.
    val rejected =
        failures.mapNotNull { failure ->
            // Failure.rejection is the SDK's stored verdict (set only when every endpoint answered and none
            // accepted); a transport failure or a refusal beside a pending endpoint carries none.
            (rejections[TransactionId.new(failure.txId)] ?: failure.rejection)?.let { rejection ->
                failure to rejection
            }
        }

    val firstNonGrpcFailure = failures.firstOrNull { !it.grpcError }

    return when {
        successCount == txIds.size -> {
            SubmitResult.Success(txIds = txIds)
        }

        hasNotAttempted -> {
            // The SDK can only retry a transaction after submit() registered an endpoint for it.
            // Keeping an untouched leg pending would promise a retry that cannot happen.
            SubmitResult.Partial(txIds = txIds, statuses = map { it.statusDescription() })
        }

        successCount == 0 && rejected.isNotEmpty() -> {
            // No endpoint accepted and the SDK rejected it: it no longer retries it, so it is not pending.
            val (failure, rejection) = rejected.first()
            SubmitResult.Failure(
                txIds = txIds,
                code = failure.code,
                description = failure.description,
                rejection = rejection
            )
        }

        rejected.isNotEmpty() -> {
            // Some transactions of the proposal were accepted and the SDK rejected another: the existing
            // multi-transaction semantics, funds possibly stuck, which only a Partial result conveys.
            Twig.warn { "Transaction submission was rejected for good: ${rejected.first().second}" }
            SubmitResult.Partial(txIds = txIds, statuses = map { it.statusDescription() })
        }

        else -> {
            // Every non-accepted transaction was attempted and the SDK has not rejected it, so its retry plan
            // remains active. This includes one endpoint's refusal for good while another has not answered:
            // reporting a terminal failure would invite a manual retry while background resubmission can still
            // broadcast the same transaction.
            firstNonGrpcFailure?.let { failure ->
                Twig.warn { "Transaction submission remains pending after rejected code: ${failure.code}" }
            }
            SubmitResult.GrpcFailure(
                txIds = txIds,
                description = grpcFailureDescription,
                reason = grpcFailureReason
            )
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
                // Code only: the server-provided description must not flow into the partial-failure
                // support email. Matches the iOS Broadcaster integration, which redacts the rejection
                // message to "rejected code: <code>" so a privately configured server cannot leak.
                "$MULTI_SUBMIT_REJECTED_STATUS_PREFIX$code"
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

/**
 * IMMEDIATE-mode migration send-max: an ordinary send proposal (see
 * [cash.z.ecc.android.sdk.OrchardMigrationSdk.proposeImmediateMigration]) sweeping all spendable
 * Orchard funds into this account's own Ironwood receiver. There is no destination/memo to
 * validate, only an already-built [proposal].
 *
 * Both account types adopt it into the same shared submit pipeline every other send uses (see
 * `MigrationReviewVM.onConfirmImmediate`): a Keystone account routes it through the external-signer
 * QR flow, a software-key (Zashi) account through [ZashiProposalRepository.submit]. Either way the
 * generic Transaction Progress screen renders its sending/success states.
 */
data class MigrationSweepTransactionProposal(
    val amount: Zatoshi,
    override val proposal: Proposal,
) : TransactionProposal

private const val DEFAULT_SHIELDING_THRESHOLD = 100000L
private const val MULTI_SUBMIT_TAG = "[MultiSubmit]"
private const val MULTI_SUBMIT_PCZT_TAG = "[MultiSubmit/PCZT]"
private const val MULTI_SUBMIT_EMPTY_TRANSACTION_CODE = -1
private const val MULTI_SUBMIT_EMPTY_TRANSACTION_DESCRIPTION = "No transactions created"
private const val MULTI_SUBMIT_GRPC_FAILURE_STATUS = "grpcFailure"
private const val MULTI_SUBMIT_REJECTED_STATUS_PREFIX = "rejected code: "

/**
 * Whether a send's result is a failed operation for the outage detection: anything but a full success. A relay that
 * is down surfaces as a gRPC failure, or as a partial result across several transactions, not as a rejection; the
 * policy still requires the deciding call's evidence to be a transport failure, so a rejection stays harmless.
 */
internal fun SubmitResult.isOperationFailure(): Boolean = this !is SubmitResult.Success
