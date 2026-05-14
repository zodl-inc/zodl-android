package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationPirPrecomputeResult
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
import kotlin.test.assertFailsWith

class VotingProofPrecomputeRepositoryTest {
    @Test
    fun precomputeResolvesPirServerAndRunsAgainstVotingDb() =
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
            val request = precomputeRequest()

            repository.startDelegationPirPrecompute(request)

            val result =
                requireNotNull(repository.awaitDelegationPirPrecompute(request.key))
                    .getOrThrow()

            assertEquals(VotingDelegationPirPrecomputeResult(cachedCount = 2, fetchedCount = 3), result)
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
                    CryptoCall.SetWalletId(dbHandle = DB_HANDLE, walletId = "wallet-id"),
                    CryptoCall.PrecomputeDelegationPir(
                        dbHandle = DB_HANDLE,
                        roundId = "round-id",
                        bundleIndex = 1,
                        pirServerUrl = "https://pir.example",
                        networkId = 0,
                        notesJson = "[notes]"
                    ),
                    CryptoCall.CloseVotingDb(DB_HANDLE)
                ),
                cryptoClient.calls
            )

            scope.cancel()
        }

    @Test
    fun precomputeFailureIsReturnedAsResultAndClosesVotingDb() =
        runBlocking {
            val failure = IllegalStateException("pir failed")
            val cryptoClient = FakeVotingCryptoClient(precomputeFailure = failure)
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example"),
                    scope = scope
                )
            val request = precomputeRequest()

            repository.startDelegationPirPrecompute(request)

            val result = requireNotNull(repository.awaitDelegationPirPrecompute(request.key))
            val thrown =
                assertFailsWith<IllegalStateException> {
                    result.getOrThrow()
                }

            assertEquals(failure, thrown)
            assertEquals(CryptoCall.CloseVotingDb(DB_HANDLE), cryptoClient.calls.last())

            scope.cancel()
        }

    @Test
    fun phaseRegressionPrecomputeFailureReturnsEmptyResultAndClosesVotingDb() =
        runBlocking {
            val cryptoClient =
                FakeVotingCryptoClient(
                    precomputeFailure =
                        IllegalStateException(
                            "refusing to regress round phase from PROVED to DELEGATION"
                        )
                )
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val repository =
                VotingProofPrecomputeRepositoryImpl(
                    votingCryptoClient = cryptoClient.client,
                    pirSnapshotResolver = FakePirSnapshotResolver("https://pir.example"),
                    scope = scope
                )
            val request = precomputeRequest()

            repository.startDelegationPirPrecompute(request)

            val result =
                requireNotNull(repository.awaitDelegationPirPrecompute(request.key))
                    .getOrThrow()

            assertEquals(VotingDelegationPirPrecomputeResult(cachedCount = 0, fetchedCount = 0), result)
            assertEquals(CryptoCall.CloseVotingDb(DB_HANDLE), cryptoClient.calls.last())

            scope.cancel()
        }

    @Test
    fun warmProvingCachesStartsOnlyOnce() =
        runBlocking {
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

            assertEquals(1, cryptoClient.warmupCount)

            scope.cancel()
        }

    private fun precomputeRequest() =
        VotingDelegationPirPrecomputeRequest(
            accountUuid = "account",
            walletId = "wallet-id",
            votingDbPath = "/tmp/voting.sqlite3",
            roundId = "round-id",
            bundleIndex = 1,
            pirEndpoints = listOf("https://pir-a", "https://pir-b"),
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
    private val precomputeFailure: Exception? = null
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
                            walletId = args.valueAt(1)
                        )
                    Unit
                }

                "precomputeDelegationPir" -> {
                    calls +=
                        CryptoCall.PrecomputeDelegationPir(
                            dbHandle = args.valueAt(0),
                            roundId = args.valueAt(1),
                            bundleIndex = args.valueAt(2),
                            pirServerUrl = args.valueAt(3),
                            networkId = args.valueAt(4),
                            notesJson = args.valueAt(5)
                        )
                    precomputeFailure?.let { throw it }
                    VotingDelegationPirPrecomputeResult(cachedCount = 2, fetchedCount = 3)
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
        val walletId: String
    ) : CryptoCall

    data class PrecomputeDelegationPir(
        val dbHandle: Long,
        val roundId: String,
        val bundleIndex: Int,
        val pirServerUrl: String,
        val networkId: Int,
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
