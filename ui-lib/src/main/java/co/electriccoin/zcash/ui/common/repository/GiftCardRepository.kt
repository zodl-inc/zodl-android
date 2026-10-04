package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import cash.z.ecc.android.sdk.GiftCardRedeemer
import cash.z.ecc.android.sdk.model.GiftCard
import cash.z.ecc.android.sdk.model.GiftCardLinkError
import cash.z.ecc.android.sdk.model.MemoContent
import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardOrigin
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.GiftCardSummary
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.UUID
import cash.z.ecc.android.sdk.exception.GiftCardException as SdkGiftCardException
import cash.z.ecc.android.sdk.model.GiftCardOrigin as SdkGiftCardOrigin

/**
 * The app's only boundary to gift card redemption. Everything gift-card specific that needs the SDK lives behind
 * this interface, so wiring the SDK in is limited to [GiftCardRepositoryImpl].
 *
 * Implementations must never log, persist or put into exception messages the link passed to [parse], nor any
 * secret derived from it.
 */
interface GiftCardRepository {
    /**
     * A cheap check on the text's shape only, without parsing or validating it. Used by the scanners and the app
     * link entry point to route text to the redeem flow; [parse] does the real validation.
     */
    fun isGiftCardLink(value: String): Boolean

    /**
     * Parses [link] and keeps the card in memory until [cleanup].
     *
     * @throws GiftCardException.InvalidLink when the link cannot be read
     * @throws GiftCardException.WrongNetwork when the card is for another network than the wallet's
     * @throws GiftCardException.NotAvailable when this build cannot redeem gift cards
     */
    suspend fun parse(link: String): GiftCardSummary

    /**
     * Progress of the card's sync while [check] runs, from 0 to 1. Emits nothing when the implementation cannot
     * report progress.
     */
    fun observeCheckProgress(handle: GiftCardHandle): Flow<Float>

    /**
     * Syncs the card's own temporary wallet and reports what it holds.
     */
    suspend fun check(handle: GiftCardHandle): GiftCardStatus

    /**
     * Sends everything the card holds to [toAddress], with a memo that identifies the transaction as a gift card
     * redemption (and carries the card's message, if any) in the user's history. [toAddress] must therefore be a
     * shielded address.
     *
     * @return the id of the submitted transaction
     */
    suspend fun redeem(
        handle: GiftCardHandle,
        toAddress: String
    ): String

    /**
     * Forgets the card and erases the card's temporary wallet data, and only that. Safe to call more than once and
     * for unknown handles. Does not suspend: the erase completes in the implementation's own scope so that it also
     * runs when the caller's scope is being cancelled.
     */
    fun cleanup(handle: GiftCardHandle)
}

/**
 * Prefixes of the links the redeem flow accepts. Only the shape is checked here; the SDK parses the link.
 */
internal object GiftCardLinkPrefixes {
    const val ZODL = "https://gift.zodl.com/#"
    const val VIZOR = "https://link.vizor.cash/payment-links/open#"

    val all = listOf(ZODL, VIZOR)

    fun matches(value: String): Boolean {
        val trimmed = value.trim()
        return all.any { trimmed.startsWith(it, ignoreCase = true) }
    }
}

/**
 * SDK-backed implementation: a [GiftCardRedeemer] per parsed card, which runs the card's temporary wallet beside
 * the main one. The card wallet syncs from, and submits to, the lightwalletd endpoint the main synchronizer is
 * using, which is the one stored in the [PersistableWalletProvider]'s wallet (the main synchronizer is rebuilt
 * from that same wallet whenever it changes).
 *
 * Redeemers are keyed by [GiftCardHandle]; the SDK keys the card wallet's data on disk by an alias derived from
 * the card itself, so two live redeemers for the same card would share that data. Parsing a card that is already
 * held therefore evicts and closes the earlier redeemer first, and operations on the new one wait for that close.
 *
 * Closing (which erases the card wallet's data) happens in this repository's own scope, so it still runs when
 * the caller's scope is cancelled; the SDK serializes it after any in-flight operation on the same redeemer, and
 * [redeem] itself runs in that scope too, so an interrupted screen cannot abandon a half-done redemption.
 */
class GiftCardRepositoryImpl(
    private val application: Application,
    private val persistableWalletProvider: PersistableWalletProvider,
) : GiftCardRepository {
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Guards [cards] and [closeJobs]. */
    private val lock = Any()

    private val cards = mutableMapOf<GiftCardHandle, HeldCard>()

    /** The latest close of each alias' card wallet, so that a new redeemer for the alias can wait for it. */
    private val closeJobs = mutableMapOf<String, Job>()

    override fun isGiftCardLink(value: String): Boolean = GiftCardLinkPrefixes.matches(value)

    override suspend fun parse(link: String): GiftCardSummary {
        val wallet = persistableWalletProvider.getPersistableWallet() ?: throw GiftCardException.NotAvailable()
        val card =
            try {
                GiftCard.parse(link)
            } catch (e: SdkGiftCardException.InvalidLink) {
                throw e.toAppException()
            }
        if (card.network != wallet.network) throw GiftCardException.WrongNetwork()
        val redeemer = newRedeemer(card, wallet.network, wallet.endpoint)

        val handle = GiftCardHandle(UUID.randomUUID().toString())
        val held = HeldCard(card = card, network = wallet.network, endpoint = wallet.endpoint, redeemer = redeemer)
        synchronized(lock) {
            val evicted = cards.filterValues { it.redeemer.alias == redeemer.alias }
            evicted.keys.forEach { cards.remove(it) }
            evicted.values.forEach { closeLocked(it.redeemer) }
            held.priorClose = closeJobs[redeemer.alias]
            cards[handle] = held
        }
        return GiftCardSummary(
            handle = handle,
            origin = card.origin.toAppOrigin(),
            birthdayHeight = card.birthdayHeight.value,
            statedAmount = card.statedAmount,
            message = card.description
        )
    }

    // The SDK reports no sync progress for the card wallet, so the UI keeps its indeterminate state.
    override fun observeCheckProgress(handle: GiftCardHandle): Flow<Float> = emptyFlow()

    override suspend fun check(handle: GiftCardHandle): GiftCardStatus {
        val held = held(handle)
        held.priorClose?.join()
        val status =
            try {
                held.redeemer.check()
            } catch (e: SdkGiftCardException.Closed) {
                throw GiftCardException.UnknownHandle(e)
            }
        return when (status) {
            is GiftCardRedeemer.Status.Ready -> {
                GiftCardStatus.Ready(spendable = status.balance.spendable)
            }

            is GiftCardRedeemer.Status.Pending -> {
                GiftCardStatus.Pending(pending = status.balance.pending, confirmationsRemaining = null)
            }

            GiftCardRedeemer.Status.Empty -> {
                GiftCardStatus.Empty
            }
        }
    }

    override suspend fun redeem(
        handle: GiftCardHandle,
        toAddress: String
    ): String {
        val held = held(handle)
        held.priorClose?.join()
        val recipient = RecipientAddress.new(toAddress, held.network)
        val memo = redeemMemo(label = application.getString(R.string.redeemGift_memo), message = held.card.description)
        val redeemer = held.redeemer
        // Runs in this repository's scope: once started, a redemption is never left half done by the caller going
        // away. The SDK serializes any later close() after it.
        val redemption =
            try {
                scope.async { redeemer.redeem(recipient, memo) }.await()
            } catch (e: SdkGiftCardException.Closed) {
                throw GiftCardException.UnknownHandle(e)
            }
        if (!redemption.isSubmitted) {
            // The unsubmitted transaction holds the funds in the card wallet: per the SDK contract, drop that
            // wallet and start over with a fresh one so that the next check sees the card's true state.
            replaceRedeemer(handle, held)
            throw GiftCardException.SubmitFailed()
        }
        return redemption.results.first().txIdString()
    }

    override fun cleanup(handle: GiftCardHandle) {
        synchronized(lock) {
            cards.remove(handle)?.let { closeLocked(it.redeemer) }
        }
    }

    private fun held(handle: GiftCardHandle): HeldCard =
        synchronized(lock) { cards[handle] } ?: throw GiftCardException.UnknownHandle()

    private fun newRedeemer(
        card: GiftCard,
        network: ZcashNetwork,
        endpoint: LightWalletEndpoint
    ): GiftCardRedeemer =
        try {
            GiftCardRedeemer.new(
                context = application,
                card = card,
                network = network,
                lightWalletEndpoint = endpoint
            )
        } catch (e: SdkGiftCardException.NetworkMismatch) {
            throw GiftCardException.WrongNetwork(e)
        }

    private fun replaceRedeemer(
        handle: GiftCardHandle,
        held: HeldCard
    ) {
        val fresh = newRedeemer(held.card, held.network, held.endpoint)
        synchronized(lock) {
            // The card was cleaned up meanwhile: its redeemer is already being closed.
            if (cards[handle] !== held) return
            closeLocked(held.redeemer)
            held.priorClose = closeJobs[fresh.alias]
            held.redeemer = fresh
        }
    }

    /**
     * Closes [redeemer] in [scope], after any earlier close of the same alias. Call with [lock] held.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun closeLocked(redeemer: GiftCardRedeemer) {
        val alias = redeemer.alias
        val previous = closeJobs[alias]
        val job =
            scope.launch {
                previous?.join()
                try {
                    redeemer.close()
                } catch (e: Exception) {
                    Twig.error(e) { "Closing a gift card wallet failed" }
                }
            }
        closeJobs[alias] = job
        job.invokeOnCompletion {
            synchronized(lock) { if (closeJobs[alias] === job) closeJobs.remove(alias) }
        }
    }

    private class HeldCard(
        val card: GiftCard,
        val network: ZcashNetwork,
        val endpoint: LightWalletEndpoint,
        @Volatile var redeemer: GiftCardRedeemer,
    ) {
        /** A close of this card's alias that must finish before [redeemer] touches the card wallet. */
        @Volatile
        var priorClose: Job? = null
    }
}

/**
 * The memo on the redeem transaction: the localized "Gift card" [label], followed by the card's [message] when it
 * has one. The message is cut on a UTF-8 character boundary so that the memo always fits
 * [MemoContent.MAX_MEMO_LENGTH_BYTES]; a card's message is never a reason to fail the redemption.
 */
private fun redeemMemo(
    label: String,
    message: String?
): MemoContent {
    val text = message?.replace("\u0000", "")?.trim()?.takeIf { it.isNotEmpty() }
    val memo =
        if (text == null) {
            label
        } else {
            val prefix = label + MEMO_MESSAGE_SEPARATOR
            prefix + text.truncatedToUtf8Bytes(MemoContent.MAX_MEMO_LENGTH_BYTES - MemoContent.length(prefix))
        }
    return MemoContent.fromString(memo)
}

private const val MEMO_MESSAGE_SEPARATOR = " · "

/**
 * The longest prefix of this string that encodes to at most [maxBytes] UTF-8 bytes, cut on a code point boundary.
 */
private fun String.truncatedToUtf8Bytes(maxBytes: Int): String {
    val bytes = toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return this
    var end = maxBytes.coerceAtLeast(0)
    // Step back over the continuation bytes (10xxxxxx) of a code point cut in the middle.
    while (end > 0 && (bytes[end].toInt() and CONTINUATION_BYTE_MASK) == CONTINUATION_BYTE) end--
    return String(bytes, 0, end, Charsets.UTF_8)
}

private const val CONTINUATION_BYTE_MASK = 0xC0
private const val CONTINUATION_BYTE = 0x80

private fun SdkGiftCardException.InvalidLink.toAppException(): GiftCardException =
    when (reason) {
        GiftCardLinkError.NetworkMismatch,
        GiftCardLinkError.UnsupportedNetwork -> GiftCardException.WrongNetwork()

        GiftCardLinkError.NotAGiftLink,
        GiftCardLinkError.UnsupportedVersion,
        GiftCardLinkError.MissingField,
        GiftCardLinkError.DuplicateField,
        GiftCardLinkError.InvalidField,
        GiftCardLinkError.TooLong,
        GiftCardLinkError.KeyDerivation,
        GiftCardLinkError.Unknown -> GiftCardException.InvalidLink(this)
    }

private fun SdkGiftCardOrigin.toAppOrigin(): GiftCardOrigin =
    when (this) {
        SdkGiftCardOrigin.Zodl -> GiftCardOrigin.ZODL

        SdkGiftCardOrigin.VizorV1,
        SdkGiftCardOrigin.VizorV2,
        SdkGiftCardOrigin.VizorV3 -> GiftCardOrigin.VIZOR
    }
