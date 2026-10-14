package co.electriccoin.zcash.ui.common.ledger

import androidx.navigation.NavGraphBuilder
import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.ui.common.datasource.ExactInputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ExactOutputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposalNotCreatedException
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.model.LedgerPairingMissingException
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.repository.SubmitProposalState
import kotlinx.coroutines.flow.StateFlow

/**
 * The Ledger side of the send pipeline: the proposal of a Ledger account, created by the shared
 * send, pay, ZIP-321, swap and shield flows and read back by their review, submit and progress
 * screens. Signing it on the device is the feature module's business, started through
 * [LedgerNavigator.forwardToSign].
 *
 * This and the other contracts in this file are the seam between ui-lib (the app "core") and the
 * feature-ledger module. ui-lib never imports feature-ledger classes — it talks exclusively to these
 * contracts, and the app module wires the implementations in via Koin (`featureLedgerModule`).
 * Shaped the same way MigrationContracts.kt and VotingContracts.kt shape their seams: small,
 * single-purpose interfaces, one per call site. What stays in ui-lib is what the rest of the wallet
 * needs to know about a Ledger account — the LedgerAccount model, its binding storage, and the
 * fail-fast LedgerOperationUnsupportedException arms of spend paths a Ledger cannot sign yet; the
 * device transport, pairing, signing session and the Ledger screens live behind these contracts.
 */
interface LedgerProposalPipeline {
    val transactionProposal: StateFlow<TransactionProposal?>

    val submitState: StateFlow<SubmitProposalState?>

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

    fun clear()

    suspend fun getTransactionProposal(): TransactionProposal
}

/**
 * The shared hardware-wallet enrollment screens' way into a Ledger pairing: whether the pairing the
 * device just produced is still held, and the import of its account once the birthday is known.
 */
interface LedgerAccountImporter {
    /**
     * False after process death emptied the in-memory pairing; the screens then return to the root.
     */
    fun hasPendingPairing(): Boolean

    /**
     * Imports and selects the paired account at [birthday], then moves on to the connected screen.
     *
     * @throws LedgerPairingMissingException if the pending pairing is gone.
     */
    @Throws(InitializeException.ImportAccountException::class)
    suspend fun importAccount(birthday: BlockHeight?)
}

/** Routes from the shared flows into the Ledger screens. */
interface LedgerNavigator {
    /** Opens the Ledger sign sheet over the current screen for the pending proposal. */
    fun forwardToSign()

    /** Starts connecting a Ledger (the Choose Hardware Wallet entry point). */
    fun forwardToConnect()

    /** Shows the Ledger connected screen (after Keep Open on the birthday path). */
    fun forwardToConnected()
}

/** Installs the Ledger destinations into the wallet nav graph. */
interface LedgerNavContributor {
    fun contribute(navGraphBuilder: NavGraphBuilder)
}
