package co.electriccoin.zcash.ui.common.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import java.util.UUID

/**
 * Holds scanned, pasted or app-linked gift card links in memory between the place they enter the app and the redeem
 * screen. Navigation routes only ever carry the random id returned by [stash]: routes are serialized into the
 * saved instance state, and a gift card link carries a spending secret that must not end up there or in logs.
 *
 * Nothing here is persisted. After process death the ids no longer resolve, and the redeem screen asks the user to
 * open the link again.
 */
interface GiftCardLinkStore {
    /**
     * A link that arrived through an app link and waits for the wallet to be ready, as the id from [stash].
     */
    val pendingAppLinkId: StateFlow<String?>

    /**
     * Keeps [link] and returns the id to retrieve it with.
     */
    fun stash(link: String): String

    /**
     * Returns the link for [id] and forgets it.
     */
    fun take(id: String): String?

    /**
     * Stashes [link] and marks it as the pending app link, replacing (and forgetting) any earlier pending one.
     */
    fun setPendingAppLink(link: String)

    /**
     * Returns the pending app link's id, if any, and clears the pending mark. The link itself stays stashed until
     * [take].
     */
    fun consumePendingAppLinkId(): String?
}

class GiftCardLinkStoreImpl : GiftCardLinkStore {
    private val links = mutableMapOf<String, String>()

    private val pendingId = MutableStateFlow<String?>(null)

    override val pendingAppLinkId: StateFlow<String?> = pendingId.asStateFlow()

    override fun stash(link: String): String {
        val id = UUID.randomUUID().toString()
        synchronized(links) { links[id] = link }
        return id
    }

    override fun take(id: String): String? = synchronized(links) { links.remove(id) }

    override fun setPendingAppLink(link: String) {
        val id = stash(link)
        pendingId.getAndUpdate { id }?.let { take(it) }
    }

    override fun consumePendingAppLinkId(): String? = pendingId.getAndUpdate { null }
}
