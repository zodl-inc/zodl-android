package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.model.TransparentInfo
import co.electriccoin.zcash.ui.common.model.UnifiedInfo
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneBundleSignature
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.repository.withRemainingKeystoneBundlesSkipped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SkipRemainingKeystoneBundlesUseCaseTest {
    @Test
    fun skipsRemainingBundlesInVotingDbAndRecoverySnapshot() =
        runBlocking {
            val selectedAccount = keystoneAccount()
            val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
            val roundId = "round"
            val recoveryRepository =
                FakeVotingRecoveryRepository(
                    snapshot = recoverySnapshot(accountUuid = accountUuid, roundId = roundId)
                )
            val cryptoClient = FakeVotingCryptoClient()

            val result =
                useCase(
                    selectedAccount = selectedAccount,
                    cryptoClient = cryptoClient.client,
                    recoveryRepository = recoveryRepository
                )(accountUuid, roundId)

            assertEquals(
                SkippedKeystoneBundles(
                    signedBundleCount = 2,
                    skippedBundleCount = 1,
                    signedWeight = 500,
                    skippedWeight = 100
                ),
                result
            )
            assertEquals(2, recoveryRepository.snapshot?.bundleCount)
            assertEquals(500, recoveryRepository.snapshot?.eligibleWeight)
            assertEquals(listOf(300L, 200L), recoveryRepository.snapshot?.bundleWeights)
            assertEquals(listOf(SkipRequest(accountUuid, roundId, keepCount = 2)), recoveryRepository.skipRequests)
            assertEquals(
                listOf(
                    CryptoCall.OpenVotingDb("/tmp/wallet/voting.sqlite3"),
                    CryptoCall.SetWalletId(
                        dbHandle = DB_HANDLE,
                        walletId = selectedAccount.sdkAccount.accountUuid.toString()
                    ),
                    CryptoCall.DeleteSkippedBundles(
                        dbHandle = DB_HANDLE,
                        roundId = roundId,
                        keepCount = 2
                    ),
                    CryptoCall.CloseVotingDb(DB_HANDLE)
                ),
                cryptoClient.calls
            )
        }

    @Test
    fun snapshotFailureAfterDbDeleteLeavesDeleteRetrySafe() =
        runBlocking {
            val selectedAccount = keystoneAccount()
            val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
            val roundId = "round"
            val originalSnapshot = recoverySnapshot(accountUuid = accountUuid, roundId = roundId)
            val recoveryRepository =
                FakeVotingRecoveryRepository(
                    snapshot = originalSnapshot,
                    failOnSkip = true
                )
            val cryptoClient = FakeVotingCryptoClient(deletedRows = 0)

            val failure =
                assertFailsWith<IllegalStateException> {
                    useCase(
                        selectedAccount = selectedAccount,
                        cryptoClient = cryptoClient.client,
                        recoveryRepository = recoveryRepository
                    )(accountUuid, roundId)
                }

            assertEquals("snapshot write failed", failure.message)
            assertEquals(originalSnapshot, recoveryRepository.snapshot)
            assertEquals(listOf(SkipRequest(accountUuid, roundId, keepCount = 2)), recoveryRepository.skipRequests)
            assertEquals(
                listOf(
                    CryptoCall.OpenVotingDb("/tmp/wallet/voting.sqlite3"),
                    CryptoCall.SetWalletId(
                        dbHandle = DB_HANDLE,
                        walletId = selectedAccount.sdkAccount.accountUuid.toString()
                    ),
                    CryptoCall.DeleteSkippedBundles(
                        dbHandle = DB_HANDLE,
                        roundId = roundId,
                        keepCount = 2
                    ),
                    CryptoCall.CloseVotingDb(DB_HANDLE)
                ),
                cryptoClient.calls
            )
        }

    private fun useCase(
        selectedAccount: WalletAccount,
        cryptoClient: VotingCryptoClient,
        recoveryRepository: FakeVotingRecoveryRepository
    ) = SkipRemainingKeystoneBundlesUseCase(
        getSelectedWalletAccount = GetSelectedWalletAccountUseCase(FakeAccountDataSource(selectedAccount)),
        synchronizerProvider = FakeSynchronizerProvider("/tmp/wallet/data.sqlite3"),
        votingCryptoClient = cryptoClient,
        votingRecoveryRepository = recoveryRepository.repository
    )

    private suspend fun keystoneAccount(): KeystoneAccount =
        KeystoneAccount(
            sdkAccount = AccountFixture.new(),
            unified =
                UnifiedInfo(
                    address = WalletAddressFixture.unified(),
                    balance = WalletBalanceFixture.newLong()
                ),
            transparent =
                TransparentInfo(
                    address = WalletAddressFixture.transparent(),
                    balance = Zatoshi(0)
                ),
            isSelected = true
        )

    private fun recoverySnapshot(
        accountUuid: String,
        roundId: String
    ) = VotingRecoverySnapshot(
        accountUuid = accountUuid,
        roundId = roundId,
        bundleCount = 3,
        eligibleWeight = 600,
        bundleWeights = listOf(300L, 200L, 100L),
        keystoneBundleSignatures =
            mapOf(
                0 to signature(),
                1 to signature()
            )
    )

    private fun signature() =
        VotingKeystoneBundleSignature(
            spendAuthSigBase64 = "signature",
            sighashBase64 = "sighash"
        )
}

private const val DB_HANDLE = 7L

private class FakeAccountDataSource(
    private val selectedAccountValue: WalletAccount
) : AccountDataSource {
    override val allAccounts: StateFlow<List<WalletAccount>?> = MutableStateFlow(listOf(selectedAccountValue))
    override val selectedAccount: Flow<WalletAccount?> = flowOf(selectedAccountValue)
    override val zashiAccount: Flow<ZashiAccount?> = flowOf(null)

    override suspend fun getAllAccounts(): List<WalletAccount> = listOf(selectedAccountValue)

    override suspend fun getSelectedAccount(): WalletAccount = selectedAccountValue

    override suspend fun getZashiAccount(): ZashiAccount = unsupported()

    override suspend fun selectAccount(account: Account) = unsupported()

    override suspend fun selectAccount(account: WalletAccount) = unsupported()

    override suspend fun importKeystoneAccount(
        ufvk: String,
        seedFingerprint: String,
        index: Long,
        birthday: BlockHeight?
    ): Account = unsupported()

    override suspend fun requestNextShieldedAddress(): WalletAddress.Unified = unsupported()

    override suspend fun deleteAccount(account: WalletAccount) = unsupported()
}

private class FakeSynchronizerProvider(
    private val walletDbPath: String
) : SynchronizerProvider {
    private val fakeSynchronizer = synchronizer(walletDbPath)

    override val error: StateFlow<SynchronizerError?> = MutableStateFlow(null)
    override val synchronizer: StateFlow<Synchronizer?> = MutableStateFlow(fakeSynchronizer)
    override val isSeedMismatch: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun getSynchronizer(): Synchronizer = fakeSynchronizer

    override suspend fun getSynchronizerOrNull(): Synchronizer? = fakeSynchronizer

    override suspend fun getVotingWalletDbPath(): String = walletDbPath

    override fun resetSynchronizer() = Unit
}

private class FakeVotingCryptoClient(
    private val deletedRows: Long = 1
) {
    val calls = mutableListOf<CryptoCall>()

    val client: VotingCryptoClient =
        Proxy.newProxyInstance(
            VotingCryptoClient::class.java.classLoader,
            arrayOf(VotingCryptoClient::class.java)
        ) { _, method, args ->
            when (method.name) {
                "openVotingDb" -> {
                    calls += CryptoCall.OpenVotingDb(args.valueAt<String>(0))
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

                "deleteSkippedBundles" -> {
                    calls +=
                        CryptoCall.DeleteSkippedBundles(
                            dbHandle = args.valueAt(0),
                            roundId = args.valueAt(1),
                            keepCount = args.valueAt(2)
                        )
                    deletedRows
                }

                "closeVotingDb" -> {
                    calls += CryptoCall.CloseVotingDb(args.valueAt(0))
                    Unit
                }

                else -> {
                    method.handleObjectMethod(this)
                }
            }
        } as VotingCryptoClient
}

private class FakeVotingRecoveryRepository(
    var snapshot: VotingRecoverySnapshot?,
    private val failOnSkip: Boolean = false
) {
    val skipRequests = mutableListOf<SkipRequest>()

    val repository: VotingRecoveryRepository =
        Proxy.newProxyInstance(
            VotingRecoveryRepository::class.java.classLoader,
            arrayOf(VotingRecoveryRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "observe" -> {
                    flowOf(snapshot)
                }

                "get" -> {
                    snapshot
                }

                "store" -> {
                    snapshot = args.valueAt(0)
                    Unit
                }

                "skipRemainingKeystoneBundles" -> {
                    skipRemainingKeystoneBundles(
                        accountUuid = args.valueAt(0),
                        roundId = args.valueAt(1),
                        keepCount = args.valueAt(2)
                    )
                }

                else -> {
                    method.handleObjectMethod(this)
                }
            }
        } as VotingRecoveryRepository

    private fun skipRemainingKeystoneBundles(
        accountUuid: String,
        roundId: String,
        keepCount: Int
    ): VotingRecoverySnapshot {
        skipRequests += SkipRequest(accountUuid, roundId, keepCount)
        if (failOnSkip) {
            error("snapshot write failed")
        }
        return requireNotNull(snapshot)
            .withRemainingKeystoneBundlesSkipped(keepCount)
            .also { snapshot = it }
    }
}

private data class SkipRequest(
    val accountUuid: String,
    val roundId: String,
    val keepCount: Int
)

private sealed interface CryptoCall {
    data class OpenVotingDb(
        val dbPath: String
    ) : CryptoCall

    data class SetWalletId(
        val dbHandle: Long,
        val walletId: String
    ) : CryptoCall

    data class DeleteSkippedBundles(
        val dbHandle: Long,
        val roundId: String,
        val keepCount: Int
    ) : CryptoCall

    data class CloseVotingDb(
        val dbHandle: Long
    ) : CryptoCall
}

private fun synchronizer(walletDbPath: String): Synchronizer =
    Proxy.newProxyInstance(
        Synchronizer::class.java.classLoader,
        arrayOf(Synchronizer::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "getWalletDbPath" -> walletDbPath
            else -> method.handleObjectMethod(proxy, args)
        }
    } as Synchronizer

private fun Method.handleObjectMethod(
    proxy: Any,
    args: Array<Any?>? = null
): Any? =
    when (name) {
        "toString" -> "Fake${declaringClass.simpleName}"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> unsupported()
    }

@Suppress("UNCHECKED_CAST")
private fun <T> Array<Any?>?.valueAt(index: Int): T = requireNotNull(this)[index] as T

private fun unsupported(): Nothing = error("Unexpected call in fake")
