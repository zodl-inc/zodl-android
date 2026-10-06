package co.electriccoin.zcash.ui.common.datasource

import android.app.Application
import cash.z.ecc.android.sdk.GiftCardRedeemer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.GiftCard
import cash.z.ecc.android.sdk.model.GiftCardLinkError
import cash.z.ecc.android.sdk.model.MemoContent
import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GIFT_CARD_MEMO_SEPARATOR
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardOrigin
import co.electriccoin.zcash.ui.common.model.GiftCardRedemption
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.GiftCardSummary
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.UUID
import cash.z.ecc.android.sdk.exception.GiftCardException as SdkGiftCardException
import cash.z.ecc.android.sdk.model.GiftCardOrigin as SdkGiftCardOrigin

/**
 * The app's only boundary to the SDK's gift card redemption. It holds the parsed cards and their redeemers, and
 * nothing else: the redemption flow itself, and when a card is closed, are decided by
 * [co.electriccoin.zcash.ui.common.repository.GiftCardRepository].
 *
 * Implementations must never log, persist or put into exception messages the link passed to [parse], nor any
 * secret derived from it.
 */
interface GiftCardDataSource {
    /**
     * Parses [link] and keeps the card in memory until [close].
     *
     * @throws GiftCardException.InvalidLink when the link cannot be read
     * @throws GiftCardException.WrongNetwork when the card is for another network than the wallet's
     * @throws GiftCardException.NotAvailable when this build cannot redeem gift cards
     */
    suspend fun parse(link: String): ParsedGiftCard

    /**
     * Syncs the card's own temporary wallet and reports what it holds. A card holding no more than the fee a
     * redemption would pay is [GiftCardStatus.Empty].
     *
     * @throws GiftCardException.InUse when another redemption of the same card is still using its wallet
     * @throws GiftCardException.UnknownHandle when the card was closed
     * @throws GiftCardException.CheckFailed when the card's wallet could not be created or synced
     */
    suspend fun check(handle: GiftCardHandle): GiftCardStatus

    /**
     * Sends everything the card holds to [toAddress], with a memo that identifies the transaction as a gift card
     * redemption (and carries the card's message, if any) in the user's history. [toAddress] must therefore be a
     * shielded address.
     *
     * @throws GiftCardException.NotChecked when no [check] of this card has completed
     * @throws GiftCardException.NothingToRedeem when the card holds nothing spendable above the fee
     * @throws GiftCardException.InUse when another redemption of the same card is still using its wallet
     * @throws GiftCardException.SubmitFailed when the network did not accept the redemption, or creating or
     * submitting it failed in any other way; the card is reset so that the next check sees its true state
     */
    suspend fun redeem(
        handle: GiftCardHandle,
        toAddress: String
    ): GiftCardRedemption

    /**
     * Forgets the card, erases the card's temporary wallet data and wipes the card's key from memory. Safe to call
     * more than once and for unknown handles. Does not suspend: the erase completes in the implementation's own scope
     * so that it also runs when the caller's scope is being cancelled.
     */
    fun close(handle: GiftCardHandle)

    /**
     * The card wallets whose data is stored on the device, on every network, whether or not a card is held for them.
     */
    suspend fun findStoredCardWallets(): List<StoredCardWallet>

    /**
     * Deletes the data of a card wallet found by [findStoredCardWallets].
     */
    suspend fun eraseCardWallet(wallet: StoredCardWallet)
}

/**
 * A card held by a [GiftCardDataSource].
 *
 * @param walletAlias the alias of the card's temporary wallet: the same for every link of the same card.
 */
data class ParsedGiftCard(
    val summary: GiftCardSummary,
    val walletAlias: String
)

/**
 * A card wallet stored on the device.
 */
data class StoredCardWallet(
    val network: ZcashNetwork,
    val alias: String
)

/**
 * SDK-backed implementation: a [GiftCardRedeemer] per parsed card, which runs the card's temporary wallet beside
 * the main one. The card wallet syncs from, and submits to, the lightwalletd endpoint the main synchronizer is
 * using, which is the one stored in the [PersistableWalletProvider]'s wallet (the main synchronizer is rebuilt
 * from that same wallet whenever it changes). It also connects the way the main synchronizer does: over Tor
 * exactly when the user's Tor setting ([IsTorEnabledStorageProvider]) is on, so the server never sees the user's IP
 * address next to the card's birthday and claim when the user chose Tor.
 *
 * The redemption is also recorded in the main synchronizer as a trusted transaction (ZIP 315), so the wallet shows
 * the claimed funds at once and can spend them after 3 confirmations rather than 10. Failing to record it does not
 * fail the redemption: the wallet then finds the funds on its next sync, as an ordinary receive.
 *
 * Redeemers are keyed by [GiftCardHandle]; the SDK keys the card wallet's data on disk by an alias derived from
 * the card itself ([ParsedGiftCard.walletAlias]), so the caller must not use two cards with the same alias at once.
 * Operations on a new redeemer wait for any earlier close of the same alias.
 *
 * Closing (which erases the card wallet's data) happens in this data source's own scope, so it still runs when the
 * caller's scope is cancelled; the SDK cancels an in-flight check and serializes the close after any in-flight
 * redemption on the same redeemer.
 */
class GiftCardDataSourceImpl(
    private val application: Application,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider,
) : GiftCardDataSource {
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Guards [cards] and [closeJobs]. */
    private val lock = Any()

    private val cards = mutableMapOf<GiftCardHandle, HeldCard>()

    /** The latest close of each alias' card wallet, so that a new redeemer for the alias can wait for it. */
    private val closeJobs = mutableMapOf<String, Job>()

    override suspend fun parse(link: String): ParsedGiftCard {
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
            held.priorClose = closeJobs[redeemer.alias]
            cards[handle] = held
        }
        return ParsedGiftCard(
            summary =
                GiftCardSummary(
                    handle = handle,
                    origin = card.origin.toAppOrigin(),
                    birthdayHeight = card.birthdayHeight.value,
                    statedAmount = card.statedAmount,
                    message = card.description
                ),
            walletAlias = redeemer.alias
        )
    }

    override suspend fun check(handle: GiftCardHandle): GiftCardStatus {
        val held = held(handle)
        held.priorClose?.join()
        val status =
            try {
                held.redeemer.check()
            } catch (e: SdkGiftCardException.Closed) {
                throw GiftCardException.UnknownHandle(e)
            } catch (e: SdkGiftCardException.InUse) {
                throw GiftCardException.InUse(e)
            } catch (e: SdkGiftCardException.SyncFailed) {
                throw GiftCardException.CheckFailed(e)
            }
        return when (status) {
            is GiftCardRedeemer.Status.Ready -> {
                GiftCardStatus.Ready(spendable = status.balance.spendable, redeemable = status.redeemable)
            }

            is GiftCardRedeemer.Status.Pending -> {
                GiftCardStatus.Pending(pending = status.balance.pending)
            }

            GiftCardRedeemer.Status.Empty -> {
                GiftCardStatus.Empty
            }
        }
    }

    /**
     * Any failure of the SDK's redeem other than its typed refusals, which come before anything is created, may come
     * after the redemption's transaction was created and stored in the card wallet, where it holds the card's notes.
     * Such a failure is handled like an unsubmitted redemption: the redeemer is replaced, per the SDK contract. So is
     * a cancellation from inside the SDK while the caller itself is not cancelled.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun redeem(
        handle: GiftCardHandle,
        toAddress: String
    ): GiftCardRedemption {
        val held = held(handle)
        held.priorClose?.join()
        val recipient = RecipientAddress.new(toAddress, held.network)
        val memo = redeemMemo(label = application.getString(R.string.redeemGift_memo), message = held.card.description)
        val redemption =
            try {
                held.redeemer.redeem(recipient, memo, synchronizerProvider.synchronizer.value)
            } catch (e: SdkGiftCardException.Closed) {
                throw GiftCardException.UnknownHandle(e)
            } catch (e: SdkGiftCardException.NetworkMismatch) {
                throw GiftCardException.WrongNetwork(e)
            } catch (e: SdkGiftCardException.NotChecked) {
                throw GiftCardException.NotChecked(e)
            } catch (e: SdkGiftCardException.NothingToRedeem) {
                throw GiftCardException.NothingToRedeem(e)
            } catch (e: SdkGiftCardException.InUse) {
                throw GiftCardException.InUse(e)
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                replaceRedeemer(handle, held)
                throw GiftCardException.SubmitFailed(e)
            } catch (e: Exception) {
                replaceRedeemer(handle, held)
                throw GiftCardException.SubmitFailed(e)
            }
        val result = redemption.results.firstOrNull()
        if (result == null || !redemption.isSubmitted) {
            replaceRedeemer(handle, held)
            throw GiftCardException.SubmitFailed()
        }
        if (!redemption.recordedInDestination) {
            Twig.warn { "Gift card redemption not recorded in the wallet as trusted; it will be found on sync" }
        }
        return GiftCardRedemption(txId = result.txIdString(), received = redemption.amount)
    }

    override fun close(handle: GiftCardHandle) {
        synchronized(lock) {
            cards.remove(handle)?.let { closeLocked(it.redeemer, wipe = it.card) }
        }
    }

    override suspend fun findStoredCardWallets(): List<StoredCardWallet> =
        listOf(ZcashNetwork.Mainnet, ZcashNetwork.Testnet).flatMap { network ->
            GiftCardRedeemer.storedAliases(application, network).map { StoredCardWallet(network, it) }
        }

    override suspend fun eraseCardWallet(wallet: StoredCardWallet) {
        Synchronizer.eraseAlias(application, wallet.network, wallet.alias)
    }

    private fun held(handle: GiftCardHandle): HeldCard =
        synchronized(lock) { cards[handle] } ?: throw GiftCardException.UnknownHandle()

    /**
     * A redeemer whose card wallet connects as the main synchronizer does: over Tor exactly when the user's Tor
     * setting is on (an unset setting means off, as for the main synchronizer). Read at creation, like the endpoint.
     */
    private suspend fun newRedeemer(
        card: GiftCard,
        network: ZcashNetwork,
        endpoint: LightWalletEndpoint
    ): GiftCardRedeemer {
        val isTorEnabled = isTorEnabledStorageProvider.get() == true
        return try {
            GiftCardRedeemer.new(
                context = application,
                card = card,
                network = network,
                lightWalletEndpoint = endpoint,
                isTorEnabled = isTorEnabled
            )
        } catch (e: SdkGiftCardException.NetworkMismatch) {
            throw GiftCardException.WrongNetwork(e)
        }
    }

    /**
     * Drops the card wallet that holds an unsubmitted redemption and starts over with a fresh redeemer for the same
     * card, without wiping it, per the SDK contract, so that the next check sees the card's true state. Does nothing
     * for a card closed meanwhile, whose redeemer is already being closed. A fresh redeemer that cannot be created
     * leaves the old one in place, after logging.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun replaceRedeemer(
        handle: GiftCardHandle,
        held: HeldCard
    ) {
        val fresh =
            try {
                newRedeemer(held.card, held.network, held.endpoint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Twig.error { "Replacing a gift card redeemer failed: ${e::class.simpleName}" }
                return
            }
        synchronized(lock) {
            if (cards[handle] !== held) return
            closeLocked(held.redeemer, wipe = null)
            held.priorClose = closeJobs[fresh.alias]
            held.redeemer = fresh
        }
    }

    /**
     * Closes [redeemer] in [scope], after any earlier close of the same alias, then wipes the key of [wipe], if
     * given. Call with [lock] held.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun closeLocked(
        redeemer: GiftCardRedeemer,
        wipe: GiftCard?
    ) {
        val alias = redeemer.alias
        val previous = closeJobs[alias]
        val job =
            scope.launch {
                previous?.join()
                try {
                    redeemer.close()
                } catch (e: Exception) {
                    Twig.error { "Closing a gift card wallet failed: ${e::class.simpleName}" }
                } finally {
                    wipe?.wipe()
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
 * The memo on the redeem transaction: the localized "Gift Card" [label], followed by the card's [message] when it
 * has one. The message is the SDK's [GiftCard.description], which the SDK has already sanitized (no control or
 * invisible characters, never blank); here it is only cut on a UTF-8 character boundary so that the memo always fits
 * [MemoContent.MAX_MEMO_LENGTH_BYTES]. A card's message is never a reason to fail the redemption.
 */
private fun redeemMemo(
    label: String,
    message: String?
): MemoContent {
    val memo =
        if (message == null) {
            label
        } else {
            val prefix = label + GIFT_CARD_MEMO_SEPARATOR
            prefix + message.truncatedToUtf8Bytes(MemoContent.MAX_MEMO_LENGTH_BYTES - MemoContent.length(prefix))
        }
    return MemoContent.fromString(memo)
}

/**
 * The longest prefix of this string that encodes to at most [maxBytes] UTF-8 bytes, cut on a code point boundary:
 * the cut steps back over the continuation bytes (`10xxxxxx`) of a code point cut in the middle.
 */
private fun String.truncatedToUtf8Bytes(maxBytes: Int): String {
    val bytes = toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return this
    var end = maxBytes.coerceAtLeast(0)
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

        SdkGiftCardOrigin.LegacyV1,
        SdkGiftCardOrigin.LegacyV2,
        SdkGiftCardOrigin.LegacyV3 -> GiftCardOrigin.VIZOR
    }
