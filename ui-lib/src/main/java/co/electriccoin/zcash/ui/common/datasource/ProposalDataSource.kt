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
import co.electriccoin.zcash.ui.common.model.NetworkDimension
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.Dispatchers
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

class InsufficientFundsException(
    cause: Throwable? = null
) : Exception(cause)

class TexUnsupportedOnKSException : Exception("TEX addresses are unsupported on Keystone")

@Suppress("TooManyFunctions")
class ProposalDataSourceImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
) : ProposalDataSource {
    override suspend fun createProposal(account: WalletAccount, send: ZecSend): RegularTransactionProposal =
        withContext(Dispatchers.IO) {
            getOrThrow { synchronizer ->
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
            synchronizerProvider.getSynchronizer().createPcztFromProposal(
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

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    private suspend fun submitTransactionInternal(
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

            val result = submitResults.toSubmitResult()

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
     * Resolves which endpoints a created transaction is broadcast to. In automatic server-selection
     * mode this returns every bundled endpoint: broadcasting to all of them trades a small amount of
     * privacy (the transaction is sent to each bundled server, not just one) for higher reliability
     * and faster confirmation. Manual mode honors the user's explicit choice and submits only through
     * the selected endpoint.
     */
    private suspend fun getSubmissionEndpoints(): List<LightWalletEndpoint> {
        val knownEndpoints = lightWalletEndpointProvider.getEndpoints()
        val currentEndpoint = persistableWalletProvider.requirePersistableWallet().endpoint
        val isAutomatic =
            resolveIsServerSelectionAutomatic(
                isAutomaticPreference = isServerSelectionAutomaticProvider.get(),
                currentEndpoint = currentEndpoint,
                knownEndpoints = knownEndpoints
            )
        return if (isAutomatic) knownEndpoints else listOf(currentEndpoint)
    }

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
