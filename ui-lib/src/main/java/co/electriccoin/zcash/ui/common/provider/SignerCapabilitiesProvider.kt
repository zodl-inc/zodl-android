package co.electriccoin.zcash.ui.common.provider

import android.app.ActivityManager
import android.content.Context
import cash.z.ecc.android.sdk.model.ExternalSignerBudgets
import cash.z.ecc.android.sdk.model.TransactionSizeBound
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import kotlinx.coroutines.CancellationException

/**
 * What the party that signs and proves a transaction can handle.
 *
 * @property sizeBound the limits each transaction of a send must stay within.
 * @property singleChangeOutput whether the signer accepts exactly one change output. No signer needs this yet.
 */
data class SignerCapabilities(
    val sizeBound: TransactionSizeBound,
    val singleChangeOutput: Boolean = false
)

/**
 * The one place that decides, per account, how large a transaction the wallet may build. Max, Review, Swap and Pay and
 * the gift card redemption all ask it, and nothing else switches on the type of an account for this.
 *
 * Shielding and the Orchard to Ironwood migration never ask: they are not bounded.
 *
 * The memory bound is measured once per send flow, at the first [forAccount] call (Max or Review), and reused for the
 * rest of that flow (proposal, PCZT creation, submit): the memory available keeps changing, and a bound that shrank
 * between Max and Review would reject a send the user already reviewed. [endFlow] ends the flow; the next call
 * measures again.
 */
interface SignerCapabilitiesProvider {
    /** The capabilities of whoever signs for [account], for the current send flow. */
    suspend fun forAccount(account: WalletAccount): SignerCapabilities

    /** Ends the current send flow (sent, cancelled or abandoned): the next [forAccount] measures the memory again. */
    fun endFlow()

    /**
     * Starts a send flow owned by one screen and returns its token. The next [forAccount] measures the memory again.
     * Only the owner's token ends it through [endFlow], so a screen that is cleared late cannot end the flow of the
     * screen that replaced it.
     */
    fun beginFlow(): Long

    /** Ends the flow [token] belongs to; nothing happens when another flow has started or ended since. */
    fun endFlow(token: Long)

    /** The capabilities of signing with a key this device holds, as a gift card's is: the device alone limits them. */
    suspend fun forSoftwareSigning(): SignerCapabilities
}

/**
 * The memory the OS lets the app use for proving a transaction right now.
 *
 * @property availableBytes the memory available to start using.
 * @property isLowRamDevice whether the OS considers the device a low-RAM one.
 */
data class AvailableMemory(
    val availableBytes: Long,
    val isLowRamDevice: Boolean
)

/** Where the memory available at send time is read from, so that tests can replace it. */
fun interface AvailableMemorySource {
    fun read(): AvailableMemory
}

/** Reads the memory from the [ActivityManager]. */
class ActivityManagerAvailableMemorySource(
    private val context: Context
) : AvailableMemorySource {
    override fun read(): AvailableMemory {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return AvailableMemory(availableBytes = info.availMem, isLowRamDevice = activityManager.isLowRamDevice)
    }
}

/** Where the signing budget of a QR signer is read from, so that tests can replace it. */
fun interface ExternalSignerBudgetSource {
    /** The most shielded actions one signing round covers. */
    suspend fun maxActionsPerRound(): Int
}

/** Reads the budget librustzcash defines, through the SDK. */
class SdkExternalSignerBudgetSource : ExternalSignerBudgetSource {
    override suspend fun maxActionsPerRound(): Int = ExternalSignerBudgets.qrSigningRoundMaxActions()
}

class SignerCapabilitiesProviderImpl(
    private val availableMemorySource: AvailableMemorySource,
    private val externalSignerBudgetSource: ExternalSignerBudgetSource,
    private val elapsedRealtimeMillis: () -> Long,
) : SignerCapabilitiesProvider {
    private val lock = Any()
    private var flowBound: TransactionSizeBound? = null
    private var flowStartedAt = 0L
    private var flowToken = 0L

    override suspend fun forAccount(account: WalletAccount): SignerCapabilities =
        when (account) {
            is KeystoneAccount -> SignerCapabilities(flowMemoryBound().withSigningBudget())
            else -> SignerCapabilities(flowMemoryBound())
        }

    override fun endFlow() =
        synchronized(lock) {
            flowToken++
            flowBound = null
        }

    override fun beginFlow(): Long =
        synchronized(lock) {
            flowToken++
            flowBound = null
            flowToken
        }

    override fun endFlow(token: Long) =
        synchronized(lock) {
            if (token == flowToken) {
                flowToken++
                flowBound = null
            }
        }

    override suspend fun forSoftwareSigning(): SignerCapabilities = SignerCapabilities(memoryBound())

    /**
     * The memory bound of the current flow: measured at the first use and kept until [endFlow]. A flow nobody ended
     * (the user left the form) is not trusted for longer than [FLOW_MAX_AGE_MILLIS].
     */
    private fun flowMemoryBound(): TransactionSizeBound =
        synchronized(lock) {
            val now = elapsedRealtimeMillis()
            val cached = flowBound
            if (cached != null && now - flowStartedAt < FLOW_MAX_AGE_MILLIS) {
                cached
            } else {
                memoryBound().also {
                    flowBound = it
                    flowStartedAt = now
                }
            }
        }

    /**
     * Proving takes memory in proportion to the transaction's shielded actions. The SDK turns the memory we can count
     * on into a bound; a low-RAM device counts on none, which the SDK answers with its floor.
     */
    private fun memoryBound(): TransactionSizeBound {
        val memory = availableMemorySource.read()
        val usable =
            if (memory.isLowRamDevice) {
                0L
            } else {
                memory.availableBytes * (PERCENT - PROVING_MEMORY_SAFETY_MARGIN_PERCENT) / PERCENT
            }
        return TransactionSizeBound.forProvingMemory(usable)
    }

    /**
     * What a Keystone signs per round, as librustzcash defines it. If it cannot be read, the memory bound stands alone
     * rather than the send failing; the failure is logged by class name only.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun TransactionSizeBound.withSigningBudget(): TransactionSizeBound =
        try {
            intersect(TransactionSizeBound(maxShieldedActions = externalSignerBudgetSource.maxActionsPerRound()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Twig.warn { "External signer budget unavailable: ${e::class.simpleName}" }
            this
        }
}

/** The share of the available memory kept back from proving, for what else the app and the OS use meanwhile. */
const val PROVING_MEMORY_SAFETY_MARGIN_PERCENT = 20L

/** How long a send flow's memory bound is kept if the flow is never ended explicitly. */
const val FLOW_MAX_AGE_MILLIS = 10 * 60 * 1000L
private const val PERCENT = 100L
