package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.GiftCardSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
     * Sends everything the card holds to [toAddress].
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
 * SDK-backed implementation.
 *
 * SDK WIRING PENDING: wire to the SDK's gift card API once it lands (see the SDK's `feature/gift-cards` work):
 *  - [parse]: parse through the SDK (JNI into librustzcash, never a Kotlin parser), map the SDK's link error
 *    categories to [GiftCardException.InvalidLink] and a network mismatch against the wallet's `ZcashNetwork` to
 *    [GiftCardException.WrongNetwork], then create the SDK's gift card redeemer (an isolated Synchronizer under its
 *    own alias, started at the card's birthday height) and key it by a fresh [GiftCardHandle].
 *  - [observeCheckProgress]: map the redeemer's sync progress.
 *  - [check]: sync the redeemer and map its balance to [GiftCardStatus].
 *  - [redeem]: have the redeemer send its whole balance to the address and return the txid.
 *  - [cleanup]: close the redeemer and erase its alias' data only, in an application-lifetime scope, after any
 *    in-flight [redeem] for the same handle has finished.
 * Until then every operation that needs the SDK throws [GiftCardException.NotAvailable].
 */
class GiftCardRepositoryImpl : GiftCardRepository {
    override fun isGiftCardLink(value: String): Boolean = GiftCardLinkPrefixes.matches(value)

    override suspend fun parse(link: String): GiftCardSummary = throw GiftCardException.NotAvailable()

    override fun observeCheckProgress(handle: GiftCardHandle): Flow<Float> = flowOf()

    override suspend fun check(handle: GiftCardHandle): GiftCardStatus = throw GiftCardException.NotAvailable()

    override suspend fun redeem(
        handle: GiftCardHandle,
        toAddress: String
    ): String = throw GiftCardException.NotAvailable()

    override fun cleanup(handle: GiftCardHandle) {
        // SDK WIRING PENDING: nothing is held until parse() is wired to the SDK.
    }
}
