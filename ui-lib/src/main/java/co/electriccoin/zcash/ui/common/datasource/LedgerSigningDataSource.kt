package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerAccountBinding
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothTransport
import cash.z.ecc.android.sdk.ledger.LedgerDeviceIdentity
import cash.z.ecc.android.sdk.ledger.LedgerSigningProgress
import cash.z.ecc.android.sdk.model.Pczt
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.toLedgerIssue
import co.electriccoin.zcash.ui.common.provider.LedgerScannerProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Holds the Bluetooth link to the Ledger a transaction is signed with. The link never leaves this
 * layer: the repository above only asks whether one is open, to choose between repeating a request
 * over it and looking for the device again.
 */
interface LedgerSigningDataSource {
    val isLinked: Boolean

    /**
     * Opens a link to [device], closing a link held from an earlier attempt first.
     */
    suspend fun connect(device: LedgerBluetoothDevice)

    /**
     * Signs [pczt] for [account] over the open link. The link is closed on success and after every
     * failure that needs the device to be found again; it stays open after a refusal that can be
     * repeated over it, such as a rejected review.
     *
     * @throws LedgerLinkMissingException when no link is open.
     * @throws LedgerBindingUnusableException when the account's stored binding cannot be read.
     * @throws LedgerException for every device failure.
     */
    suspend fun sign(
        pczt: Pczt,
        account: LedgerAccount,
        onProgress: (LedgerSigningProgress) -> Unit,
    ): Pczt

    suspend fun close()
}

/**
 * [LedgerSigningDataSource.sign] ran without an open link, e.g. because the link was closed while
 * the request was on its way.
 */
class LedgerLinkMissingException : Exception("No Ledger link is open.")

/**
 * The Ledger binding stored with the account is missing or corrupt, so the account cannot be signed
 * for until it is connected again.
 */
class LedgerBindingUnusableException(
    cause: Throwable
) : Exception("The stored Ledger binding is unusable.", cause)

class LedgerSigningDataSourceImpl(
    private val ledgerScannerProvider: LedgerScannerProvider,
    private val synchronizerProvider: SynchronizerProvider,
) : LedgerSigningDataSource {
    private val mutex = Mutex()

    @Volatile
    private var transport: LedgerBluetoothTransport? = null

    override val isLinked: Boolean
        get() = transport != null

    override suspend fun connect(device: LedgerBluetoothDevice) =
        mutex.withLock {
            closeLink()
            Twig.info { "Ledger signing: connecting" }
            transport = ledgerScannerProvider.connect(device)
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun sign(
        pczt: Pczt,
        account: LedgerAccount,
        onProgress: (LedgerSigningProgress) -> Unit,
    ): Pczt =
        mutex.withLock {
            val link = transport ?: throw LedgerLinkMissingException()
            val binding =
                try {
                    LedgerAccountBinding(
                        deviceIdentity = LedgerDeviceIdentity.new(checkNotNull(account.deviceIdentity)),
                        zip32AccountIndex = checkNotNull(account.zip32AccountIndex)
                    )
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { closeLink() }
                    throw e
                } catch (e: Exception) {
                    Twig.warn { "Ledger signing: the stored binding is unusable: ${e.javaClass.simpleName}" }
                    closeLink()
                    throw LedgerBindingUnusableException(e)
                }
            try {
                Twig.info { "Ledger signing: signing" }
                val signed =
                    synchronizerProvider.getSynchronizer().signPcztWithLedger(
                        pczt = pczt,
                        accountUuid = account.sdkAccount.accountUuid,
                        binding = binding,
                        transport = link,
                        onProgress = onProgress
                    )
                Twig.info { "Ledger signing: signed" }
                closeLink()
                signed
            } catch (e: CancellationException) {
                Twig.info { "Ledger signing: cancelled" }
                withContext(NonCancellable) { closeLink() }
                throw e
            } catch (e: LedgerException) {
                Twig.warn { "Ledger signing failed: ${e.javaClass.simpleName}" }
                if (e.toLedgerIssue(LedgerIssueContext.SIGNING).retry != LedgerIssueRetry.SAME_LINK) {
                    closeLink()
                }
                throw e
            } catch (e: Exception) {
                Twig.warn { "Ledger signing failed: ${e.javaClass.simpleName}" }
                closeLink()
                throw e
            }
        }

    override suspend fun close() =
        mutex.withLock {
            closeLink()
        }

    /**
     * Closing an already closed transport is a no-op, and a failing close must not replace the
     * failure that led to it.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeLink() {
        val link = transport ?: return
        transport = null
        try {
            link.close()
            Twig.info { "Ledger signing: link closed" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Twig.warn { "Ledger signing: closing the link failed: ${e.javaClass.simpleName}" }
        }
    }
}
