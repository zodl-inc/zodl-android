package co.electriccoin.zcash.ui.common.zns

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class ZnsIndexNameResolverTest {
    private val alice = checkNotNull(ZnsName.fromRegistryName("alice"))
    private val bob = checkNotNull(ZnsName.fromRegistryName("bob"))

    /** A resolver host serving [names] in order, with a scriptable status. */
    private class FakeHost(
        var names: List<Pair<String, String?>>,
        var viewingKey: String = KEY,
        var synced: List<Boolean> = listOf(true),
    ) {
        val requests = mutableListOf<String>()
        var statusCalls = 0
        var fail: (() -> Nothing)? = null
        var pageOverride: ((offset: Int, limit: Int) -> String)? = null

        fun answer(body: String): String {
            requests += body
            fail?.invoke()
            val request = Json.parseToJsonElement(body).jsonObject
            return when (val method = request["method"]!!.jsonPrimitive.content) {
                "status" -> {
                    val isSynced = synced[minOf(statusCalls, synced.lastIndex)]
                    statusCalls++
                    rpc("""{"viewing_key":"$viewingKey","synced":$isSynced,"synced_height":10}""")
                }

                "list_names" -> {
                    val params = request["params"]!!.jsonObject
                    val offset = params["offset"]!!.jsonPrimitive.int
                    val limit = params["limit"]!!.jsonPrimitive.int
                    pageOverride?.let { return it(offset, limit) }
                    page(names.drop(offset).take(limit), names.size)
                }

                else -> {
                    error("unexpected $method")
                }
            }
        }
    }

    private fun TestScope.resolver(
        vararg hosts: Pair<String, FakeHost>,
        timeSource: TestTimeSource = TestTimeSource(),
    ) = ZnsIndexNameResolver(
        endpoints = hosts.map { it.first },
        registryViewingKey = KEY,
        transport = { url, body -> hosts.toMap().getValue(url).answer(body) },
        syncWait = 1.seconds,
        timeSource = timeSource,
        scope = resolverScope(),
    )

    /** The resolver's own scope, on the test scheduler so its waits take virtual time. */
    private fun TestScope.resolverScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @Test
    fun aNameResolvesFromTheDownloadedIndex() =
        runTest {
            val host = FakeHost(listOf("alice" to "u1alice", "bob" to "u1bob"))
            val resolver = resolver(PRIMARY to host)

            assertEquals(ZnsRegistration(alice, "u1alice"), resolver.resolve(alice))
            assertEquals("u1bob", resolver.resolve(bob)?.address)
            assertNull(resolver.resolve(checkNotNull(ZnsName.fromRegistryName("carol"))))
        }

    @Test
    fun noRequestEverCarriesTheNameBeingLookedUp() =
        runTest {
            val host = FakeHost(listOf("alice" to "u1alice"))
            resolver(PRIMARY to host).resolve(alice)

            assertTrue(host.requests.isNotEmpty())
            host.requests.forEach { assertFalse(it.contains("alice"), it) }
        }

    @Test
    fun theIndexIsDownloadedPageByPage() =
        runTest {
            val names = (0 until 1_200).map { "n${it.toString().padStart(4, '0')}" to "u1$it" }
            val host = FakeHost(names)
            val resolver = resolver(PRIMARY to host)

            assertEquals("u11199", resolver.resolve(checkNotNull(ZnsName.fromRegistryName("n1199")))?.address)
            assertEquals(3, host.requests.count { it.contains("list_names") })
            assertTrue(host.requests.any { it.contains("\"offset\":1000") })
        }

    @Test
    fun aReleasedRecordIsNoRegistration() =
        runTest {
            val resolver = resolver(PRIMARY to FakeHost(listOf("alice" to null, "bob" to "u1bob")))

            assertNull(resolver.resolve(alice))
            assertEquals("u1bob", resolver.resolve(bob)?.address)
        }

    @Test
    fun theCopyAnswersForFiveMinutesThenIsDownloadedAgain() =
        runTest {
            val time = TestTimeSource()
            val host = FakeHost(listOf("alice" to "u1old"))
            val resolver = resolver(PRIMARY to host, timeSource = time)

            assertEquals("u1old", resolver.resolve(alice)?.address)
            host.names = listOf("alice" to "u1new")
            time += 4.minutes
            assertEquals("u1old", resolver.resolve(alice)?.address)
            time += 1.minutes
            assertEquals("u1new", resolver.resolve(alice)?.address)
            assertEquals(2, host.statusCalls)
        }

    @Test
    fun lookupsDuringADownloadShareIt() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val host = FakeHost(listOf("alice" to "u1alice", "bob" to "u1bob"))
            val resolver =
                ZnsIndexNameResolver(
                    endpoints = listOf(PRIMARY),
                    registryViewingKey = KEY,
                    transport = { _, body ->
                        gate.await()
                        host.answer(body)
                    },
                    scope = resolverScope(),
                )

            val first = async { resolver.resolve(alice) }
            val second = async { resolver.resolve(bob) }
            runCurrent()
            gate.complete(Unit)

            assertEquals("u1alice", first.await()?.address)
            assertEquals("u1bob", second.await()?.address)
            assertEquals(1, host.statusCalls)
        }

    @Test
    fun aCancelledLookupDoesNotCancelTheDownloadOthersWaitFor() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val host = FakeHost(listOf("alice" to "u1alice", "bob" to "u1bob"))
            val resolver =
                ZnsIndexNameResolver(
                    endpoints = listOf(PRIMARY),
                    registryViewingKey = KEY,
                    transport = { _, body ->
                        gate.await()
                        host.answer(body)
                    },
                    scope = resolverScope(),
                )

            val cancelled = async { resolver.resolve(alice) }
            val waiting = async { resolver.resolve(bob) }
            runCurrent()
            cancelled.cancel()
            runCurrent()
            gate.complete(Unit)

            assertEquals("u1bob", waiting.await()?.address)
            assertEquals(1, host.statusCalls)
        }

    @Test
    fun aHostServingAnotherRegistryIsRefusedWithoutFallback() =
        runTest {
            val primary = FakeHost(listOf("alice" to "u1evil"), viewingKey = "uview1other")
            val fallback = FakeHost(listOf("alice" to "u1alice"))
            val resolver = resolver(PRIMARY to primary, FALLBACK to fallback)

            assertFailsWith<ZnsLookupException> { resolver.resolve(alice) }
            assertTrue(fallback.requests.isEmpty())
            assertTrue(primary.requests.none { it.contains("list_names") })
        }

    @Test
    fun anUnsyncedHostIsAskedAgainBeforeTheFallbackIsTried() =
        runTest {
            val catchingUp = FakeHost(listOf("alice" to "u1alice"), synced = listOf(false, true))
            assertEquals("u1alice", resolver(PRIMARY to catchingUp).resolve(alice)?.address)
            assertEquals(2, catchingUp.statusCalls)

            val stuck = FakeHost(listOf("alice" to "u1stale"), synced = listOf(false))
            val fallback = FakeHost(listOf("alice" to "u1alice"))
            assertEquals("u1alice", resolver(PRIMARY to stuck, FALLBACK to fallback).resolve(alice)?.address)
            assertEquals(3, stuck.statusCalls)
            assertTrue(stuck.requests.none { it.contains("list_names") })
        }

    @Test
    fun anUnreachableOrBrokenHostFallsBack() =
        runTest {
            val down = FakeHost(emptyList()).apply { fail = { throw IOException("offline") } }
            val fallback = FakeHost(listOf("alice" to "u1alice"))
            assertEquals("u1alice", resolver(PRIMARY to down, FALLBACK to fallback).resolve(alice)?.address)

            val proxyPage =
                FakeHost(listOf("alice" to "u1x")).apply { pageOverride = { _, _ -> "{\"error\":\"bad\"}" } }
            assertEquals("u1alice", resolver(PRIMARY to proxyPage, FALLBACK to fallback).resolve(alice)?.address)
        }

    @Test
    fun noReachableHostIsALookupFailureAndIsNotCached() =
        runTest {
            val host = FakeHost(listOf("alice" to "u1alice")).apply { fail = { throw IOException("offline") } }
            val resolver = resolver(PRIMARY to host)

            assertFailsWith<ZnsLookupException> { resolver.resolve(alice) }
            host.fail = null
            assertEquals("u1alice", resolver.resolve(alice)?.address)
        }

    @Test
    fun anIndexThatMovesDuringTheDownloadIsDownloadedAgain() =
        runTest {
            val host = FakeHost(listOf("alice" to "u1alice", "bob" to "u1bob"))
            var pages = 0
            host.pageOverride = { offset, limit ->
                pages++
                // The first download sees the total change between the status and its first page reply.
                if (pages == 1) page(host.names.drop(offset).take(limit), total = 3) else page(host.names, 2)
            }
            // A total of 3 with only 2 records reads as an index that moved.
            assertEquals("u1bob", resolver(PRIMARY to host).resolve(bob)?.address)
            assertTrue(pages >= 2)
        }

    @Test
    fun namesOutOfOrderReadAsAMovedIndex() =
        runTest {
            val host = FakeHost(listOf("bob" to "u1bob", "alice" to "u1alice"))
            val fallback = FakeHost(listOf("alice" to "u1alice"))

            assertEquals("u1alice", resolver(PRIMARY to host, FALLBACK to fallback).resolve(alice)?.address)
            assertEquals(3, host.requests.count { it.contains("list_names") })
        }

    @Test
    fun anIndexPastTheSizeCapIsRefused() =
        runTest {
            val host = FakeHost(emptyList())
            host.pageOverride = { _, _ -> page(emptyList(), ZnsIndexNameResolver.MAX_INDEX_NAMES + 1) }

            assertFailsWith<ZnsLookupException> { resolver(PRIMARY to host).resolve(alice) }
        }

    private companion object {
        const val KEY = "uview1registry"
        const val PRIMARY = "https://primary.example"
        const val FALLBACK = "https://fallback.example"

        fun rpc(result: String) = """{"jsonrpc":"2.0","id":1,"result":$result}"""

        fun page(
            names: List<Pair<String, String?>>,
            total: Int
        ): String {
            val items =
                names.joinToString(",") { (name, address) ->
                    if (address == null) {
                        """{"name":"$name","address":"","last_action":"release"}"""
                    } else {
                        """{"name":"$name","address":"$address","last_action":"claim"}"""
                    }
                }
            return rpc("""{"items":[$items],"total":$total}""")
        }
    }
}
