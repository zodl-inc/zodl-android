package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
                        torRuntime = TOR_RUNTIME,
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

    /**
     * Regression test for Important #4 of the final whole-plan review: a job that completes WITH
     * a failure must not permanently poison its own dedup key. Before this fix, the dedup check
     * only skipped a re-request while `!existing.isCancelled` -- a failed-but-not-cancelled job
     * stayed in the map forever, silently disabling retries for that key until process restart.
     */
    @Test
    fun pirWarmupRetriesAfterAPreviousFailure() =
        runBlocking {
            val cryptoClient = FakeVotingCryptoClient(pirWarmupFailure = IllegalStateException("pir failed"))
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example"),
                    scope = scope
                )
            val request = pirWarmupRequest()

            repository.startPirWarmup(request)
            yield()
            yield()
            assertEquals(1, cryptoClient.calls.count { it is CryptoCall.PrecomputePirProofs })

            // The transient failure clears -- the next screen entry must be able to retry the
            // SAME key, not stay silently stuck.
            cryptoClient.pirWarmupFailure = null
            repository.startPirWarmup(request)
            yield()
            yield()

            assertEquals(2, cryptoClient.calls.count { it is CryptoCall.PrecomputePirProofs })

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
                        torRuntime = TOR_RUNTIME,
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

    /** See pirWarmupRetriesAfterAPreviousFailure's doc comment -- same Important #4 fix. */
    @Test
    fun snapshotBundlePrecomputeRetriesAfterAPreviousFailure() =
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
            val request = snapshotBundlePrecomputeRequest()

            repository.startSnapshotBundlePrecompute(request)
            yield()
            yield()
            assertEquals(1, cryptoClient.calls.count { it is CryptoCall.PrecomputeSnapshotBundles })

            // Important #3/#4 composition: a round that failed (e.g. because it wasn't fully
            // scanned yet, per Important #3) must be able to retry on the next screen entry, not
            // stay permanently stuck.
            cryptoClient.snapshotBundlePrecomputeFailure = null
            repository.startSnapshotBundlePrecompute(request)
            yield()
            yield()

            assertEquals(2, cryptoClient.calls.count { it is CryptoCall.PrecomputeSnapshotBundles })

            scope.cancel()
        }

    /**
     * Regression test for Important #1 of the final whole-plan review: browse-time precompute can
     * hold the shared native DB lock across into the submit path. [cancelAndAwaitPrecompute] must
     * actually cancel a genuinely in-flight job (not just one that already finished) and await its
     * real termination before returning.
     */
    @Test
    fun cancelAndAwaitPrecomputeCancelsAnInFlightSnapshotBundlePrecomputeJobAndAwaitsIts() =
        runBlocking {
            val cryptoClient = FakeVotingCryptoClient()
            val gate = CompletableDeferred<Unit>()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example", gate = gate),
                    scope = scope
                )
            val request = snapshotBundlePrecomputeRequest(accountUuid = "account-under-test")

            repository.startSnapshotBundlePrecompute(request)
            yield()
            yield()
            // The job is genuinely in-flight now, parked inside resolve() -- never reached
            // openVotingDb yet.
            assertEquals(emptyList(), cryptoClient.calls)

            withTimeout(TIMEOUT_MS) {
                repository.cancelAndAwaitPrecompute(accountUuid = "account-under-test", roundId = "round-id")
            }

            // Cancelled before it ever opened the native DB -- the whole point of coordinating
            // with SubmitVotesUseCase is that this job never gets to contend for that lock.
            assertEquals(emptyList(), cryptoClient.calls)

            // The dedup key is free again -- a fresh call for the same key starts a brand-new job
            // rather than being (wrongly) deduped against the now-cancelled one.
            gate.complete(Unit)
            repository.startSnapshotBundlePrecompute(request)
            yield()
            yield()
            assertEquals(1, cryptoClient.calls.count { it is CryptoCall.PrecomputeSnapshotBundles })

            scope.cancel()
        }

    @Test
    fun cancelAndAwaitPrecomputeCancelsEveryInFlightPirWarmupJobForTheAccount() =
        runBlocking {
            val cryptoClient = FakeVotingCryptoClient()
            val gate = CompletableDeferred<Unit>()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example", gate = gate),
                    scope = scope
                )
            // Two distinct snapshot heights (distinct dedup keys) for the SAME account -- PIR
            // warmup is round-independent, so cancelAndAwaitPrecompute must sweep every one of
            // the account's own in-flight warmups, not just a single key.
            val requestA = pirWarmupRequest(accountUuid = "account-under-test").copy(snapshotHeight = 100L)
            val requestB = pirWarmupRequest(accountUuid = "account-under-test").copy(snapshotHeight = 200L)
            val requestOtherAccount = pirWarmupRequest(accountUuid = "other-account").copy(snapshotHeight = 300L)

            repository.startPirWarmup(requestA)
            repository.startPirWarmup(requestB)
            repository.startPirWarmup(requestOtherAccount)
            yield()
            yield()
            assertEquals(emptyList(), cryptoClient.calls)

            withTimeout(TIMEOUT_MS) {
                repository.cancelAndAwaitPrecompute(accountUuid = "account-under-test", roundId = "unrelated-round")
            }

            // The other account's own in-flight job is untouched -- it is still parked at the
            // gate, not cancelled, confirmed by releasing the gate and seeing it complete below.
            gate.complete(Unit)
            yield()
            yield()
            // Only the OTHER account's job survives -- both of "account-under-test"'s own jobs
            // (across two distinct snapshot-height keys) were cancelled and never reached this
            // point.
            assertEquals(1, cryptoClient.calls.count { it is CryptoCall.PrecomputePirProofs })

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

    private fun pirWarmupRequest(accountUuid: String = "account") =
        VotingPirWarmupRequest(
            accountUuid = accountUuid,
            walletId = "wallet-id",
            votingDbPath = "/tmp/voting.sqlite3",
            snapshotHeight = 123L,
            pirEndpoints = listOf("https://pir-a", "https://pir-b"),
            pirLayout = VotingPirLayout(),
            networkId = 0,
            notesJson = "[notes]",
            torRuntime = TOR_RUNTIME
        )

    private fun snapshotBundlePrecomputeRequest(accountUuid: String = "account") =
        VotingSnapshotBundlePrecomputeRequest(
            accountUuid = accountUuid,
            walletId = "wallet-id",
            votingDbPath = "/tmp/voting.sqlite3",
            roundId = "round-id",
            pirEndpoints = listOf("https://pir-a", "https://pir-b"),
            pirLayout = VotingPirLayout(),
            expectedSnapshotHeight = 123L,
            networkId = 0,
            notesJson = "[notes]",
            torRuntime = TOR_RUNTIME
        )
}

private const val DB_HANDLE = 42L
private const val TOR_RUNTIME = 7L
private const val TIMEOUT_MS = 5_000L

private class FakePirSnapshotResolver(
    private val resolvedUrl: String,
    // When set, resolve() suspends here until the deferred completes -- lets a test hold a
    // background precompute job mid-flight so it can exercise cancelAndAwaitPrecompute against a
    // genuinely in-flight job, rather than a synchronously-finished one.
    private val gate: CompletableDeferred<Unit>? = null
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
        gate?.await()
        return resolvedUrl
    }
}

private class FakeVotingCryptoClient(
    // Mutable (not constructor-fixed) so a single fake can simulate a failure on one call and a
    // success on the next -- needed for Important #4's retry-after-failure regression test.
    var pirWarmupFailure: Exception? = null,
    var snapshotBundlePrecomputeFailure: Exception? = null
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
                            torRuntime = args.valueAt(1),
                            pirServerUrl = args.valueAt(2),
                            pirLayout = args.valueAt(3),
                            notesJson = args.valueAt(4)
                        )
                    pirWarmupFailure?.let { throw it }
                    fakePirWarmupResult()
                }

                "precomputeSnapshotBundles" -> {
                    calls +=
                        CryptoCall.PrecomputeSnapshotBundles(
                            dbHandle = args.valueAt(0),
                            torRuntime = args.valueAt(1),
                            roundId = args.valueAt(2),
                            pirServerUrl = args.valueAt(3),
                            pirLayout = args.valueAt(4),
                            notesJson = args.valueAt(5)
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
        val torRuntime: Long,
        val pirServerUrl: String,
        val pirLayout: VotingPirLayout,
        val notesJson: String
    ) : CryptoCall

    data class PrecomputeSnapshotBundles(
        val dbHandle: Long,
        val torRuntime: Long,
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
