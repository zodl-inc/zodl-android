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
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerSigningDevice
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.toLedgerIssue
import co.electriccoin.zcash.ui.common.provider.LEDGER_SCAN_TIMEOUT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The proposal of a Ledger account and the session that signs it on the device.
 *
 * It mirrors [KeystoneProposalRepository] without the QR-code transport: the PCZT is created with
 * the proposal, its proofs start computing straight away, and the device signs the same unredacted
 * PCZT concurrently. Cancelling a session keeps the proposal, the PCZT and the running proofs, so
 * confirming again reuses them.
 *
 * ui-lib drives the proposal through [LedgerProposalPipeline]; the signing members below are
 * reached only by the Ledger screens and use cases.
 */
interface LedgerProposalRepository : LedgerProposalPipeline {
    /**
     * Null while no session runs.
     */
    val signingState: StateFlow<LedgerSigningState?>

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

    /**
     * Fails a session that reached [LedgerSigningState.Signed] but has no proposal left to submit,
     * with an issue that offers no retry, so the sheet leaves Cancel Transaction as the way out.
     */
    fun failSignedSessionWithoutProposal()

    suspend fun submit(): SubmitResult

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

    /**
     * Set when the last session connected to a Ledger other than the account's; the next scan then
     * offers the picker even for a lone device instead of reconnecting to the same wrong one.
     */
    @Volatile
    private var isPickerRequired = false

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
            signingState.update { LedgerSigningState.Failed(LedgerIssue.unknownWithoutRetry) }
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
                publish(session, LedgerSigningState.Failed(LedgerIssue.unbound))
                return
            }
            if (ledgerSigningDataSource.isLinked || scanAndConnect(session)) {
                sign(session, pczt, account)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerException.DeviceMismatch) {
            isPickerRequired = true
            lastSelectedIdentifier = null
            publishLedgerFailure(session, e)
        } catch (e: LedgerException) {
            publishLedgerFailure(session, e)
        } catch (e: LedgerLinkMissingException) {
            Twig.warn { "Ledger signing: session failed with ${e.javaClass.simpleName}" }
            publish(session, LedgerSigningState.Failed(LedgerIssue.disconnectedWhileSigning))
        } catch (e: LedgerBindingUnusableException) {
            Twig.warn { "Ledger signing: session failed with ${e.javaClass.simpleName}" }
            publish(session, LedgerSigningState.Failed(LedgerIssue.unbound))
        } catch (e: Exception) {
            Twig.warn { "Ledger signing: session failed with ${e.javaClass.simpleName}" }
            publish(session, LedgerSigningState.Failed(LedgerIssue.unknown))
        }
    }

    /**
     * Looks for the device and connects to it. Returns false once the session has failed on the way.
     */
    private suspend fun scanAndConnect(session: Int): Boolean {
        val device = scan(session) ?: return false
        return connect(session, device)
    }

    /**
     * Connects to [device] within [CONNECT_TIMEOUT], the cap enrollment puts on its pairing, so a
     * device that never finishes opening the Zcash app cannot hold the sheet on Connecting. Returns
     * false once that cap passed, with the link closed and the session failed as a disconnect.
     */
    private suspend fun connect(
        session: Int,
        device: LedgerBluetoothDevice
    ): Boolean {
        Twig.info { "Ledger signing: stage Connecting" }
        publish(session, LedgerSigningState.Connecting)
        return try {
            withTimeout(CONNECT_TIMEOUT) { ledgerSigningDataSource.connect(device) }
            true
        } catch (_: TimeoutCancellationException) {
            Twig.warn { "Ledger signing: connecting timed out" }
            ledgerSigningDataSource.close()
            publish(session, LedgerSigningState.Failed(LedgerIssue.disconnectedWhileSigning))
            false
        }
    }

    private fun publishLedgerFailure(
        session: Int,
        e: LedgerException
    ) {
        Twig.warn { "Ledger signing: session failed with ${e.javaClass.simpleName}" }
        publish(session, LedgerSigningState.Failed(e.toLedgerIssue(LedgerIssueContext.SIGNING)))
    }

    /**
     * Looks for the device; a lone device is connected once the list has settled, several are
     * offered for selection, and so is a lone one after the last session met the wrong Ledger.
     * Returns null when nothing was found in time, when the picker stayed empty that long, or when
     * location is off below API 31, where nothing could be found.
     */
    private suspend fun scan(session: Int): LedgerBluetoothDevice? {
        if (ledgerDeviceDataSource.isLocationOffForScan()) {
            Twig.info { "Ledger signing: location is off" }
            publish(session, LedgerSigningState.Failed(LedgerIssue.locationOff))
            return null
        }
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
                    delay(LEDGER_SCAN_TIMEOUT)
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
                if (settled.size == 1 && !isPickerRequired) {
                    settled.single()
                } else {
                    awaitSelection(session, found)
                }
            collector.cancel()
            if (device == null) {
                Twig.info { "Ledger signing: no device left to select" }
                publish(session, LedgerSigningState.Failed(LedgerIssue.noDevices))
            }
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

    /**
     * Waits for the user to pick a device. Returns null once the list has stayed empty for the scan
     * timeout, as a scan that finds nothing would.
     */
    private suspend fun awaitSelection(
        session: Int,
        found: StateFlow<List<LedgerBluetoothDevice>>
    ): LedgerBluetoothDevice? {
        val devices = found.value
        var choice = CompletableDeferred<String>()
        selection = choice
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
        val device =
            coroutineScope {
                val emptied = CompletableDeferred<Unit>()
                val emptyTimer =
                    launch {
                        found.collectLatest { current ->
                            if (current.isEmpty()) {
                                delay(LEDGER_SCAN_TIMEOUT)
                                emptied.complete(Unit)
                            }
                        }
                    }
                var picked: LedgerBluetoothDevice? = null
                while (picked == null && !emptied.isCompleted) {
                    val identifier =
                        select<String?> {
                            choice.onAwait { it }
                            emptied.onAwait { null }
                        }
                    picked = identifier?.let { id -> found.value.firstOrNull { it.identifier == id } }
                    if (picked == null && identifier != null) {
                        choice = CompletableDeferred()
                        selection = choice
                    }
                }
                emptyTimer.cancel()
                picked
            }
        selection = null
        if (device != null) {
            lastSelectedIdentifier = device.identifier
            isPickerRequired = false
        }
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
     * it can never close the link the next session opens. Nothing happens when no session ever ran
     * and no link is held.
     */
    override fun cancelSigning() {
        if (isIdle) return
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

    override fun failSignedSessionWithoutProposal() {
        synchronized(lock) {
            if (signingState.value == LedgerSigningState.Signed) {
                signingState.value = LedgerSigningState.Failed(LedgerIssue.unknownWithoutRetry)
            }
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
        isPickerRequired = false

        pcztWithProofsJob?.cancel()
        pcztWithProofsJob = null
        pcztWithProofs.update { LedgerPcztState(isLoading = false, pczt = null) }

        transactionProposal.update { null }
        submitState.update { null }
        proposalPczt = null
        pcztWithSignatures = null
    }

    private val isIdle: Boolean
        get() = sessionJob == null && signingState.value == null && !ledgerSigningDataSource.isLinked

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
        val SETTLE_DELAY = 1.seconds

        /**
         * The cap on connecting and opening the Zcash app, the same enrollment puts on its pairing.
         */
        val CONNECT_TIMEOUT = 5.minutes
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
