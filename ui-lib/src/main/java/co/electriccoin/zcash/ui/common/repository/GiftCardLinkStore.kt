package co.electriccoin.zcash.ui.common.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Holds gift card links scanned, picked from an image or pasted inside the app in memory between the scanner and the
 * redeem screen. Navigation routes only ever carry the random id returned by [stash]: routes are serialized into the
 * saved instance state, and a gift card link carries a spending secret that must not end up there or in logs.
 *
 * A link that comes from outside the app (an app link, any other VIEW intent) is never kept here: another app could
 * have altered it on its way in. Only the fact that one arrived is kept, see [isInAppScanRequested], so the user can
 * be asked to scan the card with the app itself.
 *
 * Nothing here is persisted. After process death the ids no longer resolve, and the redeem screen asks the user to
 * open the link again.
 */
interface GiftCardLinkStore {
    /**
     * Whether a gift card link arrived from outside the app and the gift card scanner should be opened, once the
     * wallet is ready, to ask the user to scan the card in the app.
     */
    val isInAppScanRequested: StateFlow<Boolean>

    /**
     * Keeps [link] and returns the id to retrieve it with.
     */
    fun stash(link: String): String

    /**
     * Returns the link for [id] and forgets it.
     */
    fun take(id: String): String?

    /**
     * Records that a gift card link arrived from outside the app. The link itself is deliberately not an argument.
     */
    fun requestInAppScan()

    /**
     * Returns whether an in-app scan was requested and clears the request.
     */
    fun consumeInAppScanRequest(): Boolean
}

class GiftCardLinkStoreImpl : GiftCardLinkStore {
    private val links = MutableStateFlow(emptyMap<String, String>())

    private val scanRequested = MutableStateFlow(false)

    override val isInAppScanRequested: StateFlow<Boolean> = scanRequested.asStateFlow()

    override fun stash(link: String): String =
        UUID.randomUUID().toString().also { id -> links.update { it + (id to link) } }

    override fun take(id: String): String? = links.getAndUpdate { it - id }[id]

    override fun requestInAppScan() {
        scanRequested.update { true }
    }

    override fun consumeInAppScanRequest(): Boolean = scanRequested.getAndUpdate { false }
}
