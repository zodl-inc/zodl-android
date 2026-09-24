package co.electriccoin.zcash.ui.common.repository

import androidx.annotation.VisibleForTesting
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerSigningProgress
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.ExactInputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ExactOutputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.LedgerBindingUnusableException
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerLinkMissingException
import co.electriccoin.zcash.ui.common.datasource.LedgerSigningDataSource
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.SendTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposalNotCreatedException
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerSigningDevice
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.toLedgerIssue
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * The proposal of a Ledger account and the session that signs it on the device.
 *
 * It mirrors [KeystoneProposalRepository] without the QR-code transport: the PCZT is created with
 * the proposal, its proofs start computing straight away, and the device signs the same unredacted
 * PCZT concurrently. Cancelling a session keeps the proposal, the PCZT and the running proofs, so
 * confirming again reuses them.
 */
@Suppress("TooManyFunctions")
interface LedgerProposalRepository {
    val transactionProposal: StateFlow<TransactionProposal?>

    val submitState: StateFlow<SubmitProposalState?>

    /**
     * Null while no session runs.
     */
    val signingState: StateFlow<LedgerSigningState?>

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createProposal(zecSend: ZecSend)

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createExactInputSwapProposal(zecSend: ZecSend, quote: SwapQuote): ExactInputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createExactOutputSwapProposal(zecSend: ZecSend, quote: SwapQuote): ExactOutputSwapTransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createZip321Proposal(zip321Uri: String): Zip321TransactionProposal

    @Throws(TransactionProposalNotCreatedException::class, InsufficientFundsException::class)
    suspend fun createShieldProposal()

    /**
     * Creates the PCZT the device signs and starts adding proofs to it. A multi-step proposal is
     * reported as [TexUnsupportedOnKSException] only when it pays a TEX address, as for Keystone.
     */
    @Throws(
        PcztException.CreatePcztFromProposalException::class,
        PcztException.MultiStepProposalUnsupportedException::class,
        TexUnsupportedOnKSException::class
    )
    suspend fun createPCZTFromProposal()

    /**
     * Starts a signing session unless one is running: over the link a previous attempt kept open,
     * or by looking for the device.
     */
    fun startSigning()

    /**
     * Picks the device to sign with while the session is [LedgerSigningState.Selecting].
     */
    fun selectDevice(identifier: String)

    /**
     * Starts the session again after [LedgerSigningState.Failed].
     */
    fun retry()

    /**
     * Stops the session and closes the link; the proposal, the PCZT and the proofs stay.
     */
    fun cancelSigning()

    suspend fun submit(): SubmitResult

    fun clear()

    suspend fun getTransactionProposal(): TransactionProposal

    fun getProposalPCZT(): Pczt?
}

@Suppress("TooManyFunctions")
class LedgerProposalRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val proposalDataSource: ProposalDataSource,
    private val ledgerDeviceDataSource: LedgerDeviceDataSource,
    private val ledgerSigningDataSource: LedgerSigningDataSource,
) : LedgerProposalRepository {
    @VisibleForTesting
    internal var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val transactionProposal = MutableStateFlow<TransactionProposal?>(null)

    override val submitState = MutableStateFlow<SubmitProposalState?>(null)

    override val signingState = MutableStateFlow<LedgerSigningState?>(null)

    private val pcztWithProofs = MutableStateFlow(LedgerPcztState(isLoading = false, pczt = null))

    @Volatile
    private var proposalPczt: Pczt? = null

    @Volatile
    private var pcztWithSignatures: Pczt? = null

    private var pcztWithProofsJob: Job? = null

    @Volatile
    private var sessionJob: Job? = null

    @Volatile
    private var closeJob: Job? = null

    @Volatile
    private var selection: CompletableDeferred<String>? = null

    /**
     * Guards [generation] together with every state write that depends on it.
     */
    private val lock = Any()

    /**
     * Bumped whenever a session starts or is cancelled; a session only publishes state while its
     * own generation is current, so one that is still unwinding cannot overwrite its successor.
     */
    @Volatile
    private var generation = 0

    @Volatile
    private var lastSelectedIdentifier: String? = null

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
        val transactionProposal = getTransactionProposal()
        val result =
            try {
                proposalDataSource.createPcztFromProposal(
                    account = accountDataSource.getSelectedAccount(),
                    proposal = transactionProposal.proposal
                )
            } catch (e: PcztException.MultiStepProposalUnsupportedException) {
                if (transactionProposal.paysTexAddress()) throw TexUnsupportedOnKSException() else throw e
            }
        cancelSigning()
        submitState.update { null }
        pcztWithSignatures = null
        proposalPczt = result
        addProofsToPczt(result)
    }

    private fun TransactionProposal.paysTexAddress(): Boolean =
        this is SendTransactionProposal && destination is WalletAddress.Tex

    private fun addProofsToPczt(proposalPczt: Pczt) {
        pcztWithProofsJob?.cancel()
        pcztWithProofsJob =
            scope.launch {
                pcztWithProofs.update { LedgerPcztState(isLoading = true, pczt = null) }
                try {
                    val result = proposalDataSource.addProofsToPczt(proposalPczt.clonePczt())
                    pcztWithProofs.update { LedgerPcztState(isLoading = false, pczt = result) }
                } catch (e: PcztException.AddProofsToPcztException) {
                    Twig.error(e) { "Failed to add proofs to PCZT" }
                    pcztWithProofs.update { LedgerPcztState(isLoading = false, pczt = null, error = e) }
                }
            }
    }

    override fun startSigning() {
        if (sessionJob?.isActive == true) return
        val pczt = proposalPczt
        if (pczt == null) {
            Twig.warn { "Ledger signing: no PCZT to sign" }
            signingState.update { LedgerSigningState.Failed(unknownIssue) }
            return
        }
        val pendingClose = closeJob
        val session = synchronized(lock) { ++generation }
        sessionJob =
            scope.launch {
                pendingClose?.join()
                runSession(session, pczt)
            }
    }

    private fun publish(
        session: Int,
        state: LedgerSigningState
    ) {
        synchronized(lock) {
            if (generation == session) signingState.value = state
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSession(
        session: Int,
        pczt: Pczt
    ) {
        try {
            val account = accountDataSource.getSelectedAccount()
            if (account !is LedgerAccount || !account.isBound) {
                Twig.warn { "Ledger signing: the selected account has no Ledger binding" }
                publish(session, LedgerSigningState.Failed(unboundIssue))
                return
            }
            if (!ledgerSigningDataSource.isLinked) {
                val device = scan(session) ?: return
                Twig.info { "Ledger signing: stage Connecting" }
                publish(session, LedgerSigningState.Connecting)
                ledgerSigningDataSource.connect(device)
            }
            sign(session, pczt, account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerException) {
            Twig.warn { "Ledger signing: session failed with ${e.javaClass.simpleName}" }
            publish(session, LedgerSigningState.Failed(e.toLedgerIssue(LedgerIssueContext.SIGNING)))
        } catch (e: LedgerLinkMissingException) {
            Twig.warn { "Ledger signing: session failed with ${e.javaClass.simpleName}" }
            publish(session, LedgerSigningState.Failed(disconnectedIssue))
        } catch (e: LedgerBindingUnusableException) {
            Twig.warn { "Ledger signing: session failed with ${e.javaClass.simpleName}" }
            publish(session, LedgerSigningState.Failed(unboundIssue))
        } catch (e: Exception) {
            Twig.warn { "Ledger signing: session failed with ${e.javaClass.simpleName}" }
            publish(session, LedgerSigningState.Failed(unknownIssue))
        }
    }

    /**
     * Looks for the device; a lone device is connected once the list has settled, several are
     * offered for selection. Returns null when nothing was found in time.
     */
    private suspend fun scan(session: Int): LedgerBluetoothDevice? {
        Twig.info { "Ledger signing: stage Scanning" }
        publish(session, LedgerSigningState.Scanning)
        val found = MutableStateFlow<List<LedgerBluetoothDevice>>(emptyList())
        val timedOut = MutableStateFlow(false)
        return coroutineScope {
            val collector =
                launch {
                    ledgerDeviceDataSource.observeDevices().collect { devices ->
                        found.update { devices }
                        updateSelecting(session, devices)
                    }
                }
            val timer =
                launch {
                    delay(SCAN_TIMEOUT)
                    timedOut.update { true }
                }
            val settled = awaitSettledDevices(found, timedOut)
            timer.cancel()
            if (settled == null) {
                collector.cancel()
                Twig.info { "Ledger signing: no device found" }
                publish(session, LedgerSigningState.Failed(LedgerIssue.noDevices))
                return@coroutineScope null
            }
            val device =
                if (settled.size == 1) {
                    settled.single()
                } else {
                    awaitSelection(session, found)
                }
            collector.cancel()
            device
        }
    }

    private fun updateSelecting(
        session: Int,
        devices: List<LedgerBluetoothDevice>
    ) {
        synchronized(lock) {
            val current = signingState.value
            if (current is LedgerSigningState.Selecting && generation == session) {
                signingState.value =
                    current.copy(
                        devices = devices.map { it.toSigningDevice() },
                        selectedIdentifier =
                            current.selectedIdentifier?.takeIf { selected ->
                                devices.any { it.identifier == selected }
                            }
                    )
            }
        }
    }

    /**
     * Waits for a device and then for the list to settle. A lone device that vanishes while the
     * list settles is waited for again, so the picker never opens empty. Returns null once
     * [timedOut] is set without a device in range.
     */
    private suspend fun awaitSettledDevices(
        found: StateFlow<List<LedgerBluetoothDevice>>,
        timedOut: StateFlow<Boolean>
    ): List<LedgerBluetoothDevice>? {
        while (true) {
            combine(found, timedOut) { devices, isTimedOut -> devices.isNotEmpty() || isTimedOut }
                .first { it }
            if (found.value.isEmpty()) return null
            delay(SETTLE_DELAY)
            val settled = found.value
            if (settled.isNotEmpty()) return settled
        }
    }

    private suspend fun awaitSelection(
        session: Int,
        found: StateFlow<List<LedgerBluetoothDevice>>
    ): LedgerBluetoothDevice {
        val devices = found.value
        Twig.info { "Ledger signing: stage Selecting" }
        publish(
            session,
            LedgerSigningState.Selecting(
                devices = devices.map { it.toSigningDevice() },
                selectedIdentifier =
                    lastSelectedIdentifier?.takeIf { selected ->
                        devices.any { it.identifier == selected }
                    }
            )
        )
        var device: LedgerBluetoothDevice? = null
        while (device == null) {
            val picked = CompletableDeferred<String>()
            selection = picked
            val identifier = picked.await()
            device = found.value.firstOrNull { it.identifier == identifier }
        }
        selection = null
        lastSelectedIdentifier = device.identifier
        return device
    }

    private suspend fun sign(
        session: Int,
        pczt: Pczt,
        account: LedgerAccount
    ) {
        Twig.info { "Ledger signing: stage Preparing" }
        publish(session, LedgerSigningState.Preparing)
        val signed =
            ledgerSigningDataSource.sign(pczt.clonePczt(), account) { progress ->
                publish(session, progress.toSigningState())
            }
        synchronized(lock) {
            if (generation != session) return
            pcztWithSignatures = signed
        }
        Twig.info { "Ledger signing: stage Signed" }
        publish(session, LedgerSigningState.Signed)
    }

    override fun selectDevice(identifier: String) {
        if (signingState.value !is LedgerSigningState.Selecting) return
        selection?.complete(identifier)
    }

    override fun retry() {
        if (signingState.value !is LedgerSigningState.Failed) return
        sessionJob?.cancel()
        sessionJob = null
        signingState.update { null }
        startSigning()
    }

    /**
     * The link is closed after the session has unwound; the next session waits for that close, so
     * it can never close the link the next session opens.
     */
    override fun cancelSigning() {
        synchronized(lock) { generation++ }
        sessionJob?.cancel()
        sessionJob = null
        selection = null
        pcztWithSignatures = null
        signingState.update { null }
        val previousClose = closeJob
        closeJob =
            scope.launch {
                previousClose?.join()
                ledgerSigningDataSource.close()
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
                    val pcztWithProofsState = pcztWithProofs.filter { !it.isLoading }.first()
                    val pcztWithProofs = pcztWithProofsState.pczt
                    if (pcztWithProofs == null) {
                        val cause =
                            pcztWithProofsState.error
                                ?: IllegalStateException("PCZT with proofs is null")
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
        cancelSigning()
        lastSelectedIdentifier = null

        pcztWithProofsJob?.cancel()
        pcztWithProofsJob = null
        pcztWithProofs.update { LedgerPcztState(isLoading = false, pczt = null) }

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

    private companion object {
        val SCAN_TIMEOUT = 20.seconds
        val SETTLE_DELAY = 1.seconds

        val unknownIssue =
            LedgerIssue(
                kind = LedgerIssueKind.UNKNOWN,
                retry = LedgerIssueRetry.RECONNECT,
                title = stringRes(R.string.ledger_error_unknown_title),
                message = stringRes(R.string.ledger_error_unknown_message),
            )

        val unboundIssue =
            LedgerIssue(
                kind = LedgerIssueKind.UNBOUND,
                retry = LedgerIssueRetry.NONE,
                title = stringRes(R.string.ledger_sign_error_unbound_title),
                message = stringRes(R.string.ledger_sign_error_unbound_message),
            )

        val disconnectedIssue =
            LedgerIssue(
                kind = LedgerIssueKind.DISCONNECTED,
                retry = LedgerIssueRetry.RECONNECT,
                title = stringRes(R.string.ledger_error_disconnected_title),
                message = stringRes(R.string.ledger_sign_error_disconnected_message),
            )
    }
}

private fun LedgerBluetoothDevice.toSigningDevice() =
    LedgerSigningDevice(
        identifier = identifier,
        name = name ?: model.productName,
    )

private fun LedgerSigningProgress.toSigningState(): LedgerSigningState =
    when (this) {
        LedgerSigningProgress.IdentifyingDevice -> LedgerSigningState.Streaming(sent = 0, total = 0)

        is LedgerSigningProgress.Streaming -> LedgerSigningState.Streaming(sent = sent, total = total)

        LedgerSigningProgress.AwaitingReviewOnDevice -> LedgerSigningState.AwaitingReview

        LedgerSigningProgress.Signing,
        LedgerSigningProgress.Complete -> LedgerSigningState.Signing
    }

private data class LedgerPcztState(
    val isLoading: Boolean,
    val pczt: Pczt?,
    val error: Exception? = null
)
