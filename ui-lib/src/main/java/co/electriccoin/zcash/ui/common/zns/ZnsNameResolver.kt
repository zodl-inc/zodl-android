package co.electriccoin.zcash.ui.common.zns

import co.electriccoin.zcash.spackle.Twig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Looks up ZNS names. */
interface ZnsNameResolver {
    /**
     * Returns the registration of [name], or null when the name is not registered.
     *
     * @throws ZnsLookupException when no resolver answered usefully
     */
    suspend fun resolve(name: ZnsName): ZnsRegistration?
}

/** Sends one JSON-RPC request [body] to [url] and returns the reply body. */
fun interface ZnsTransport {
    suspend fun post(
        url: String,
        body: String
    ): String
}

/**
 * Resolves names against a downloaded copy of the whole ZNS registry index.
 *
 * The wallet never asks a resolver about one name: it downloads the index with the resolver's `list_names` verb and
 * looks the name up on the device, so the resolver cannot learn which name, or whose address, the user is about to
 * pay. Before every download it checks the host's `status`: the viewing key must be the published registry key, and
 * the host must have indexed up to the chain tip.
 *
 * The copy answers lookups for [maxAge] and is held in memory only. Lookups that arrive during a download share it,
 * and the download runs in the resolver's own [scope], so a lookup that is cancelled (the user kept typing) never
 * cancels the download the others wait for. A failed download is not kept.
 *
 * The index is always downloaded whole: the resolver's `events` verb could keep a copy in sync, but a reorg deletes
 * events above the fork and can reuse their ids, so an incremental copy could silently miss a change.
 *
 * Expiry is the ZNS mint's job: it releases an expired name on chain and the resolver drops it, so this client does
 * not judge expiry itself.
 */
class ZnsIndexNameResolver(
    private val endpoints: List<String>,
    private val registryViewingKey: String,
    private val transport: ZnsTransport,
    private val maxAge: Duration = 5.minutes,
    private val syncWait: Duration = 2.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ZnsNameResolver {
    init {
        require(endpoints.isNotEmpty())
    }

    private val lock = Mutex()
    private var index: Map<ZnsName, ZnsRegistration>? = null
    private var fetchedAt: TimeMark? = null
    private var download: Deferred<Map<ZnsName, ZnsRegistration>>? = null

    override suspend fun resolve(name: ZnsName): ZnsRegistration? = currentIndex()[name]

    private suspend fun currentIndex(): Map<ZnsName, ZnsRegistration> {
        val running =
            lock.withLock {
                val cached = index
                val cachedAt = fetchedAt
                if (cached != null && cachedAt != null && cachedAt.elapsedNow() < maxAge) {
                    return cached
                }
                download ?: startDownload().also { download = it }
            }
        return running.await()
    }

    /** Starts a download that stores its index when it succeeds, and is forgotten either way. */
    @Suppress("TooGenericExceptionCaught")
    private fun startDownload(): Deferred<Map<ZnsName, ZnsRegistration>> =
        scope.async {
            try {
                val downloaded = downloadIndex()
                lock.withLock {
                    index = downloaded
                    fetchedAt = timeSource.markNow()
                    download = null
                }
                downloaded
            } catch (e: Throwable) {
                lock.withLock { download = null }
                throw e
            }
        }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun downloadIndex(): Map<ZnsName, ZnsRegistration> {
        for (endpoint in endpoints) {
            try {
                checkStatus(endpoint)
                return downloadFrom(endpoint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ZnsHostUnavailableException) {
                Twig.info { "ZNS: resolver failed: ${e.message}" }
            } catch (e: ZnsLookupException) {
                // The host answered: another registry, a malformed record, or an index too large to download. That
                // answer is final and is not retried elsewhere.
                throw e
            } catch (e: Exception) {
                Twig.info { "ZNS: resolver unreachable: ${e.javaClass.simpleName}" }
            }
        }
        throw ZnsLookupException("No ZNS resolver could be reached")
    }

    /**
     * Checks that [endpoint] serves the published registry and has indexed up to the chain tip.
     *
     * A host that reports another viewing key could map names anywhere, so its answers are refused. A host that is
     * still catching up could serve a name's old address. A synced resolver also reports `synced: false` for a moment
     * after each new block, so an unsynced host is asked again a few times before it counts as unavailable.
     */
    private suspend fun checkStatus(endpoint: String) {
        repeat(STATUS_ATTEMPTS) { attempt ->
            val reply = transport.post(endpoint, ZnsRpcParser.requestBody("status", emptyMap()))
            val status = ZnsRpcParser.parseStatus(reply)
            if (status.viewingKey != registryViewingKey) {
                throw ZnsLookupException("ZNS resolver serves another registry")
            }
            if (status.synced) return
            if (attempt < STATUS_ATTEMPTS - 1) delay(syncWait)
        }
        throw ZnsHostUnavailableException("resolver is still syncing")
    }

    private suspend fun downloadFrom(endpoint: String): Map<ZnsName, ZnsRegistration> {
        repeat(DOWNLOAD_ATTEMPTS) {
            try {
                return downloadPages(endpoint)
            } catch (_: IndexChangedException) {
                // A registration moved the pages under the download: start again.
            }
        }
        throw ZnsHostUnavailableException("index kept changing during the download")
    }

    @Suppress("ThrowsCount")
    private suspend fun downloadPages(endpoint: String): Map<ZnsName, ZnsRegistration> {
        val result = mutableMapOf<ZnsName, ZnsRegistration>()
        var lastName: String? = null
        var total: Int? = null
        var received = 0
        while (total == null || received < total) {
            val page =
                ZnsRpcParser.parseNamesPage(
                    transport.post(
                        endpoint,
                        ZnsRpcParser.requestBody("list_names", mapOf("limit" to PAGE_SIZE, "offset" to received))
                    )
                )
            if (page.total > MAX_INDEX_NAMES) {
                // Past this the index is refused rather than queried name by name, which would hand the resolver the
                // name being paid.
                throw ZnsLookupException("ZNS index holds more names than the wallet downloads")
            }
            if (total != null && page.total != total) throw IndexChangedException()
            total = page.total
            if (page.names.isEmpty() && received < total) throw IndexChangedException()
            // A page cannot hold more names than the index it reports.
            if (received + page.names.size > total) throw IndexChangedException()
            // The resolver lists names in order. A name out of order means the pages moved under the download. A
            // release that moves them the other way can hide one name from this copy; that name then reads as
            // unregistered until the next download, which blocks the send rather than misdirecting it.
            for (record in page.names) {
                val previous = lastName
                if (previous != null && record.name.value <= previous) throw IndexChangedException()
                lastName = record.name.value
                record.address?.let { result[record.name] = ZnsRegistration(record.name, it) }
            }
            received += page.names.size
        }
        return result.toMap()
    }

    private class IndexChangedException : Exception()

    companion object {
        /** Names per `list_names` page. The resolver caps a page at 500. */
        const val PAGE_SIZE = 500

        /** The most names the wallet downloads. Mainnet held 358 names in November 2026. */
        const val MAX_INDEX_NAMES = 20_000

        private const val STATUS_ATTEMPTS = 3
        private const val DOWNLOAD_ATTEMPTS = 3
    }
}
