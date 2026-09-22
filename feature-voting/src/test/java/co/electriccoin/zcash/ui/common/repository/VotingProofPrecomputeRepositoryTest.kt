package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * voting-5.0.0 background-precompute port (Task 4): the old per-bundle
 * `startDelegationPirPrecompute`/`awaitDelegationPirPrecompute` mechanism this test used to cover
 * had zero production callers (confirmed by repo-wide grep) and is superseded by
 * `precomputeSnapshotBundles` (Task 2's SDK call, a verified strict superset). Replaced with
 * coverage for the two new fire-and-forget background-warmup entry points below, which follow the
 * same dedup-by-key/scope.launch shape the old delegation methods and `warmProvingCaches` used.
 */
class VotingProofPrecomputeRepositoryTest {
    @Test
    fun pirWarmupResolvesPirServerAndRunsAgainstVotingDb() =
        runBlocking {
            val cryptoClient = FakeVotingCryptoClient()
            val pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example")
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = pirSnapshotResolver,
                    scope = scope
                )
            val request = pirWarmupRequest()

            repository.startPirWarmup(request)
            yield()
            yield()

            assertEquals(
                listOf(
                    ResolveCall(
                        endpoints = listOf("https://pir-a", "https://pir-b"),
                        expectedSnapshotHeight = 123L
                    )
                ),
                pirSnapshotResolver.calls
            )
            assertEquals(
                listOf(
                    CryptoCall.OpenVotingDb("/tmp/voting.sqlite3"),
                    CryptoCall.SetWalletId(dbHandle = DB_HANDLE, walletId = "wallet-id", networkId = 0),
                    CryptoCall.PrecomputePirProofs(
                        dbHandle = DB_HANDLE,
                        pirServerUrl = "https://pir.example",
                        pirLayout = VotingPirLayout(),
                        notesJson = "[notes]"
                    ),
                    CryptoCall.CloseVotingDb(DB_HANDLE)
                ),
                cryptoClient.calls
            )

            scope.cancel()
        }

    @Test
    fun pirWarmupIsDedupedForTheSameKey() =
        runBlocking {
            val cryptoClient = FakeVotingCryptoClient()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example"),
                    scope = scope
                )
            val request = pirWarmupRequest()

            repository.startPirWarmup(request)
            repository.startPirWarmup(request)
            repository.startPirWarmup(request)
            yield()
            yield()

            assertEquals(1, cryptoClient.calls.count { it is CryptoCall.PrecomputePirProofs })

            scope.cancel()
        }

    @Test
    fun pirWarmupFailureIsSwallowedAndStillClosesVotingDb() =
        runBlocking {
            val cryptoClient = FakeVotingCryptoClient(pirWarmupFailure = IllegalStateException("pir failed"))
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example"),
                    scope = scope
                )

            // Must not throw / must not cancel the caller -- fire-and-forget precompute is never
            // allowed to fail the triggering screen.
            repository.startPirWarmup(pirWarmupRequest())
            yield()
            yield()

            assertEquals(CryptoCall.CloseVotingDb(DB_HANDLE), cryptoClient.calls.last())

            scope.cancel()
        }

    @Test
    fun snapshotBundlePrecomputeResolvesPirServerAndRunsAgainstVotingDb() =
        runBlocking {
            val cryptoClient = FakeVotingCryptoClient()
            val pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example")
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = pirSnapshotResolver,
                    scope = scope
                )
            val request = snapshotBundlePrecomputeRequest()

            repository.startSnapshotBundlePrecompute(request)
            yield()
            yield()

            assertEquals(
                listOf(
                    CryptoCall.OpenVotingDb("/tmp/voting.sqlite3"),
                    CryptoCall.SetWalletId(dbHandle = DB_HANDLE, walletId = "wallet-id", networkId = 0),
                    CryptoCall.PrecomputeSnapshotBundles(
                        dbHandle = DB_HANDLE,
                        roundId = "round-id",
                        pirServerUrl = "https://pir.example",
                        pirLayout = VotingPirLayout(),
                        notesJson = "[notes]"
                    ),
                    CryptoCall.CloseVotingDb(DB_HANDLE)
                ),
                cryptoClient.calls
            )

            scope.cancel()
        }

    @Test
    fun snapshotBundlePrecomputeIsDedupedPerRoundKey() =
        runBlocking {
            val cryptoClient = FakeVotingCryptoClient()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example"),
                    scope = scope
                )
            val request = snapshotBundlePrecomputeRequest()

            repository.startSnapshotBundlePrecompute(request)
            repository.startSnapshotBundlePrecompute(request)
            yield()
            yield()
            // A different round is not deduped against the first.
            repository.startSnapshotBundlePrecompute(request.copy(roundId = "round-id-2"))
            yield()
            yield()

            assertEquals(2, cryptoClient.calls.count { it is CryptoCall.PrecomputeSnapshotBundles })

            scope.cancel()
        }

    @Test
    fun snapshotBundlePrecomputeFailureIsSwallowedAndStillClosesVotingDb() =
        runBlocking {
            val cryptoClient =
                FakeVotingCryptoClient(snapshotBundlePrecomputeFailure = IllegalStateException("bundle plan failed"))
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example"),
                    scope = scope
                )

            repository.startSnapshotBundlePrecompute(snapshotBundlePrecomputeRequest())
            yield()
            yield()

            assertEquals(CryptoCall.CloseVotingDb(DB_HANDLE), cryptoClient.calls.last())

            scope.cancel()
        }

    @Test
    fun warmProvingCachesCallsThroughOnEveryInvocation() =
        runBlocking {
            // Task 3: the crate's own start_proving_cache_warmup() now dedupes for free, so this
            // wrapper no longer needs its own AtomicBoolean gate -- every call is forwarded
            // directly and the crate is responsible for making repeat calls cheap.
            val cryptoClient = FakeVotingCryptoClient()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example"),
                    scope = scope
                )

            repository.warmProvingCaches()
            repository.warmProvingCaches()
            yield()
            yield()

            assertEquals(2, cryptoClient.warmupCount)

            scope.cancel()
        }

    private fun pirWarmupRequest() =
        VotingPirWarmupRequest(
            accountUuid = "account",
            walletId = "wallet-id",
            votingDbPath = "/tmp/voting.sqlite3",
            snapshotHeight = 123L,
            pirEndpoints = listOf("https://pir-a", "https://pir-b"),
            pirLayout = VotingPirLayout(),
            networkId = 0,
            notesJson = "[notes]"
        )

    private fun snapshotBundlePrecomputeRequest() =
        VotingSnapshotBundlePrecomputeRequest(
            accountUuid = "account",
            walletId = "wallet-id",
            votingDbPath = "/tmp/voting.sqlite3",
            roundId = "round-id",
            pirEndpoints = listOf("https://pir-a", "https://pir-b"),
            pirLayout = VotingPirLayout(),
            expectedSnapshotHeight = 123L,
            networkId = 0,
            notesJson = "[notes]"
        )
}

private const val DB_HANDLE = 42L

private class FakePirSnapshotResolver(
    private val resolvedUrl: String
) : PirSnapshotResolver {
    val calls = mutableListOf<ResolveCall>()

    override suspend fun resolve(
        endpoints: List<String>,
        expectedSnapshotHeight: Long
    ): String {
        calls +=
            ResolveCall(
                endpoints = endpoints,
                expectedSnapshotHeight = expectedSnapshotHeight
            )
        return resolvedUrl
    }
}

private class FakeVotingCryptoClient(
    private val pirWarmupFailure: Exception? = null,
    private val snapshotBundlePrecomputeFailure: Exception? = null
) {
    val calls = mutableListOf<CryptoCall>()
    var warmupCount = 0

    val client: VotingCryptoClient =
        Proxy.newProxyInstance(
            VotingCryptoClient::class.java.classLoader,
            arrayOf(VotingCryptoClient::class.java)
        ) { _, method, args ->
            when (method.name) {
                "openVotingDb" -> {
                    calls += CryptoCall.OpenVotingDb(args.valueAt(0))
                    DB_HANDLE
                }

                "setWalletId" -> {
                    calls +=
                        CryptoCall.SetWalletId(
                            dbHandle = args.valueAt(0),
                            walletId = args.valueAt(1),
                            networkId = args.valueAt(2)
                        )
                    Unit
                }

                "precomputePirProofs" -> {
                    calls +=
                        CryptoCall.PrecomputePirProofs(
                            dbHandle = args.valueAt(0),
                            pirServerUrl = args.valueAt(1),
                            pirLayout = args.valueAt(2),
                            notesJson = args.valueAt(3)
                        )
                    pirWarmupFailure?.let { throw it }
                    fakePirWarmupResult()
                }

                "precomputeSnapshotBundles" -> {
                    calls +=
                        CryptoCall.PrecomputeSnapshotBundles(
                            dbHandle = args.valueAt(0),
                            roundId = args.valueAt(1),
                            pirServerUrl = args.valueAt(2),
                            pirLayout = args.valueAt(3),
                            notesJson = args.valueAt(4)
                        )
                    snapshotBundlePrecomputeFailure?.let { throw it }
                    fakeSnapshotBundlePrecomputeResult()
                }

                "closeVotingDb" -> {
                    calls += CryptoCall.CloseVotingDb(args.valueAt(0))
                    Unit
                }

                "warmProvingCaches" -> {
                    warmupCount += 1
                    Unit
                }

                else -> {
                    method.handleObjectMethod(this, args)
                }
            }
        } as VotingCryptoClient
}

private fun fakePirWarmupResult() =
    co.electriccoin.zcash.ui.common.model.voting.VotingPirWarmupResult(
        cachedCount = 2,
        fetchedCount = 3,
        servedRoot = ByteArray(32)
    )

private fun fakeSnapshotBundlePrecomputeResult() =
    co.electriccoin.zcash.ui.common.model.voting.VotingSnapshotBundlePrecomputeResult(
        bundleCount = 1,
        eligibleWeight = 100L,
        bundleReports = emptyList()
    )

private data class ResolveCall(
    val endpoints: List<String>,
    val expectedSnapshotHeight: Long
)

private sealed interface CryptoCall {
    data class OpenVotingDb(
        val votingDbPath: String
    ) : CryptoCall

    data class SetWalletId(
        val dbHandle: Long,
        val walletId: String,
        val networkId: Int
    ) : CryptoCall

    data class PrecomputePirProofs(
        val dbHandle: Long,
        val pirServerUrl: String,
        val pirLayout: VotingPirLayout,
        val notesJson: String
    ) : CryptoCall

    data class PrecomputeSnapshotBundles(
        val dbHandle: Long,
        val roundId: String,
        val pirServerUrl: String,
        val pirLayout: VotingPirLayout,
        val notesJson: String
    ) : CryptoCall

    data class CloseVotingDb(
        val dbHandle: Long
    ) : CryptoCall
}

@Suppress("UNCHECKED_CAST")
private fun <T> Array<Any?>?.valueAt(index: Int): T = this?.get(index) as T

private fun Method.handleObjectMethod(
    target: Any,
    args: Array<Any?>?
): Any? =
    when (name) {
        "equals" -> target === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(target)
        "toString" -> target.toString()
        else -> error("Unexpected VotingCryptoClient call: $name")
    }
