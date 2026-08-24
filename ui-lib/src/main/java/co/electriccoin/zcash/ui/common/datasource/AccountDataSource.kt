package co.electriccoin.zcash.ui.common.datasource

import android.content.Context
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountImportSetup
import cash.z.ecc.android.sdk.model.AccountPurpose
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.UnifiedAddressRequest
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import cash.z.ecc.sdk.extension.ZERO
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.SaplingInfo
import co.electriccoin.zcash.ui.common.model.TransparentInfo
import co.electriccoin.zcash.ui.common.model.UnifiedInfo
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.design.util.combineToFlow
import co.electriccoin.zcash.ui.util.loggableNot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

interface AccountDataSource {
    val allAccounts: StateFlow<List<WalletAccount>?>

    val selectedAccount: Flow<WalletAccount?>

    val zashiAccount: Flow<ZashiAccount?>

    suspend fun getAllAccounts(): List<WalletAccount>

    suspend fun getSelectedAccount(): WalletAccount

    /**
     * Forces the SDK to re-read the wallet summary from the wallet database and returns the selected
     * account with its balances taken from that fresh read.
     *
     * [selectedAccount] is only as fresh as [cash.z.ecc.android.sdk.Synchronizer.walletBalances], which
     * the SDK refreshes at processor start and after each scanned batch. Between a chain-tip update and
     * the scan of the queued range (minutes over Tor, or when far behind the tip) the Rust wallet treats
     * every non-stabilized note in the tip shard as unspendable, so the displayed spendable balance can be
     * stale in either direction. Call this right before an operation that must agree with the Rust
     * wallet's current view (proposal creation) instead of trusting the cached account.
     *
     * A failed refresh never blocks the caller: it is logged and the last known account is returned.
     */
    suspend fun refreshSelectedAccount(): WalletAccount

    suspend fun getZashiAccount(): ZashiAccount

    suspend fun selectAccount(account: Account)

    suspend fun selectAccount(account: WalletAccount)

    suspend fun importKeystoneAccount(
        ufvk: String,
        seedFingerprint: String,
        index: Long,
        birthday: BlockHeight? = null
    ): Account

    suspend fun requestNextShieldedAddress(): WalletAddress.Unified

    suspend fun deleteAccount(account: WalletAccount)
}

@Suppress("TooManyFunctions")
class AccountDataSourceImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val selectedAccountUUIDProvider: SelectedAccountUUIDProvider,
    private val context: Context,
) : AccountDataSource {
    private val log = loggableNot("AccountDataSource")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val requestNextShieldedAddressChannel = MutableSharedFlow<AddressRequest>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val allAccounts: StateFlow<List<WalletAccount>?> =
        synchronizerProvider
            .synchronizer
            .flatMapLatest { synchronizer ->
                synchronizer
                    ?.accountsFlow
                    ?.filterNotNull()
                    ?.flatMapLatest { allSdkAccounts ->
                        allSdkAccounts
                            .map { sdkAccount ->
                                combine(
                                    observeUnified(synchronizer, sdkAccount),
                                    observeTransparent(synchronizer, sdkAccount),
                                    observeSapling(synchronizer, sdkAccount),
                                    observeIronwoodBalance(sdkAccount),
                                    observeIsSelected(sdkAccount, allSdkAccounts),
                                ) { unified, transparent, sapling, ironwoodBalance, isSelected ->
                                    when (sdkAccount.keySource?.lowercase()) {
                                        KEYSTONE_KEYSOURCE -> {
                                            KeystoneAccount(
                                                sdkAccount = sdkAccount,
                                                unified = unified,
                                                ironwoodBalance = ironwoodBalance,
                                                transparent = transparent,
                                                isSelected = isSelected,
                                            )
                                        }

                                        else -> {
                                            ZashiAccount(
                                                sdkAccount = sdkAccount,
                                                unified = unified,
                                                transparent = transparent,
                                                sapling = sapling!!,
                                                ironwoodBalance = ironwoodBalance,
                                                isSelected = isSelected,
                                            )
                                        }
                                    }
                                }
                            }.combineToFlow()
                    }
                    ?: flowOf(null)
            }.map { it?.sortedDescending() }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    override val selectedAccount: Flow<WalletAccount?> =
        allAccounts
            .map { account ->
                account?.firstOrNull { it.isSelected }
            }.distinctUntilChanged()

    override val zashiAccount: Flow<ZashiAccount?> =
        allAccounts
            .map { account ->
                account?.filterIsInstance<ZashiAccount>()?.firstOrNull()
            }.distinctUntilChanged()

    override suspend fun getAllAccounts() = withContext(Dispatchers.IO) { allAccounts.filterNotNull().first() }

    override suspend fun getSelectedAccount() = withContext(Dispatchers.IO) { selectedAccount.filterNotNull().first() }

    override suspend fun getZashiAccount() = withContext(Dispatchers.IO) { zashiAccount.filterNotNull().first() }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun refreshSelectedAccount(): WalletAccount =
        withContext(Dispatchers.IO) {
            val account = getSelectedAccount()
            val synchronizer = synchronizerProvider.getSynchronizer()
            if (synchronizer !is SdkSynchronizer) {
                return@withContext account
            }
            try {
                synchronizer.refreshAllBalances()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Twig.warn(e) { "Balance refresh failed; falling back to the last known balance" }
                return@withContext account
            }
            // Read the SDK's StateFlow directly rather than waiting on [selectedAccount]: the refresh
            // has already published the new summary synchronously, whereas the account pipeline above
            // propagates it asynchronously (and never re-emits if nothing changed).
            val balance = synchronizer.walletBalances.value?.get(account.sdkAccount.accountUuid)
            if (balance == null) account else account.withBalances(balance)
        }

    override suspend fun selectAccount(account: Account) =
        withContext(Dispatchers.IO) {
            selectedAccountUUIDProvider.setUUID(account.accountUuid)
        }

    override suspend fun selectAccount(account: WalletAccount) = selectAccount(account.sdkAccount)

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun importKeystoneAccount(
        ufvk: String,
        seedFingerprint: String,
        index: Long,
        birthday: BlockHeight?
    ): Account =
        withContext(Dispatchers.IO) {
            synchronizerProvider
                .getSynchronizer()
                .importAccountByUfvk(
                    AccountImportSetup(
                        accountName = context.getString(R.string.accounts_keystone),
                        keySource = KEYSTONE_KEYSOURCE,
                        ufvk = UnifiedFullViewingKey(ufvk),
                        purpose =
                            AccountPurpose.Spending(
                                seedFingerprint = seedFingerprint.hexToByteArray(),
                                zip32AccountIndex = Zip32AccountIndex.new(index)
                            ),
                        birthday = birthday,
                    ),
                )
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun requestNextShieldedAddress(): WalletAddress.Unified {
        var result: WalletAddress.Unified? = null
        scope
            .launch {
                val accountUuid = getSelectedAccount().sdkAccount.accountUuid
                log("requestNextShieldedAddress for $accountUuid")
                val responseChannel = Channel<WalletAddress.Unified>(1)
                requestNextShieldedAddressChannel.emit(AddressRequest(accountUuid, responseChannel))
                try {
                    val address = withTimeoutOrNull(ADDRESS_REQUEST_TIMEOUT) { responseChannel.receive() }
                    if (address == null) {
                        log("timed out waiting for address for $accountUuid")
                    } else {
                        log("received address ${address.address} for $accountUuid")
                    }
                    result = address
                } catch (e: Exception) {
                    log("failed to receive address for $accountUuid", e)
                }
            }.join()
        return (result ?: getSelectedAccount().unified.address).also {
            log("returning address ${it.address}")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteAccount(account: WalletAccount) =
        withContext(Dispatchers.IO) {
            try {
                val synchronizer = synchronizerProvider.getSynchronizer()
                // Reset selected account to null if the deleted account was selected
                if (account.isSelected) {
                    selectedAccountUUIDProvider.clearUUID()
                }
                val deleted = synchronizer.deleteAccount(account.sdkAccount.accountUuid)
                if (!deleted) {
                    throw AccountDeletionException("Failed to delete account")
                }
            } catch (e: Exception) {
                // Re-throw as specific exception
                throw AccountDeletionException("Failed to delete account: ${e.message}", e)
            }
        }

    private fun observeIsSelected(sdkAccount: Account, allAccounts: List<Account>) =
        selectedAccountUUIDProvider
            .uuid
            .map { uuid ->
                when (sdkAccount.keySource?.lowercase()) {
                    KEYSTONE_KEYSOURCE -> sdkAccount.accountUuid == uuid || allAccounts.size == 1
                    else -> uuid == null || sdkAccount.accountUuid == uuid || allAccounts.size == 1
                }
            }

    @Suppress("TooGenericExceptionCaught")
    private fun observeUnified(synchronizer: Synchronizer, sdkAccount: Account): Flow<UnifiedInfo> {
        suspend fun rotateAddress(): WalletAddress.Unified {
            log("deriving unified address for ${sdkAccount.accountUuid}")

            val addressRequest =
                if (sdkAccount.keySource?.lowercase() == KEYSTONE_KEYSOURCE) {
                    UnifiedAddressRequest.Orchard
                } else {
                    UnifiedAddressRequest.shielded
                }

            val address =
                WalletAddress
                    .Unified
                    .new(synchronizer.getCustomUnifiedAddress(sdkAccount, addressRequest))

            log("derived address ${address.address} for ${sdkAccount.accountUuid}")

            return address
        }

        val addressFlow =
            channelFlow {
                send(rotateAddress())

                launch {
                    requestNextShieldedAddressChannel
                        .filter { it.accountUuid == sdkAccount.accountUuid }
                        .collect {
                            val address =
                                try {
                                    rotateAddress()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    it.responseChannel.close(e)
                                    throw e
                                }
                            send(address)
                            try {
                                it.responseChannel.send(address)
                                it.responseChannel.close()
                            } catch (_: ClosedSendChannelException) {
                                // ignore
                            }
                        }
                }

                awaitClose()
            }.retryWhen { cause, attempt ->
                log(
                    "retrying address derivation for ${sdkAccount.accountUuid}, attempt $attempt",
                    cause as? Exception
                )
                delay(attempt.coerceAtMost(RETRY_DELAY).seconds)
                true
            }

        val balanceFlow =
            synchronizerProvider.walletBalances
                .map { it?.get(sdkAccount.accountUuid).unifiedBalance() }

        return combine(addressFlow, balanceFlow) { address, balance ->
            UnifiedInfo(address = address, balance = balance)
        }
    }

    private fun observeTransparent(synchronizer: Synchronizer, sdkAccount: Account): Flow<TransparentInfo> {
        val transparentAddress =
            flow {
                emit(WalletAddress.Transparent.new(synchronizer.getTransparentAddress(sdkAccount)))
            }.retryWhen { _, attempt ->
                delay(attempt.coerceAtMost(RETRY_DELAY).seconds)
                true
            }
        return combine(transparentAddress, synchronizerProvider.walletBalances) { address, balances ->
            TransparentInfo(address = address, balance = balances?.get(sdkAccount.accountUuid).transparentBalance())
        }
    }

    private fun observeSapling(synchronizer: Synchronizer, sdkAccount: Account): Flow<SaplingInfo?> =
        if (sdkAccount.keySource == KEYSTONE_KEYSOURCE) {
            flowOf(null)
        } else {
            val saplingAddress =
                flow {
                    emit(WalletAddress.Sapling.new(synchronizer.getSaplingAddress(sdkAccount)))
                }.retryWhen { _, attempt ->
                    delay(attempt.coerceAtMost(RETRY_DELAY).seconds)
                    true
                }
            combine(saplingAddress, synchronizerProvider.walletBalances) { address, balances ->
                SaplingInfo(address = address, balance = balances?.get(sdkAccount.accountUuid).saplingBalance())
            }
        }

    // Ironwood shares the same unified address as Orchard (no address of its own to observe) —
    // just its balance.
    private fun observeIronwoodBalance(sdkAccount: Account): Flow<WalletBalance> =
        synchronizerProvider.walletBalances.map { balances ->
            balances?.get(sdkAccount.accountUuid).ironwoodBalance()
        }
}

/**
 * Returns a copy of this account carrying the balances of [balance]. This is the single place that maps
 * an SDK [AccountBalance] onto [WalletAccount]'s pool fields, shared by the account observers and by
 * [AccountDataSource.refreshSelectedAccount], so a refreshed account and an observed one always agree.
 */
internal fun WalletAccount.withBalances(balance: AccountBalance): WalletAccount =
    when (this) {
        is ZashiAccount -> {
            copy(
                unified = unified.copy(balance = balance.unifiedBalance()),
                sapling = sapling.copy(balance = balance.saplingBalance()),
                ironwoodBalance = balance.ironwoodBalance(),
                transparent = transparent.copy(balance = balance.transparentBalance()),
            )
        }

        is KeystoneAccount -> {
            copy(
                unified = unified.copy(balance = balance.unifiedBalance()),
                ironwoodBalance = balance.ironwoodBalance(),
                transparent = transparent.copy(balance = balance.transparentBalance()),
            )
        }
    }

// The unified pool folds Orchard + Ironwood together (see WalletAccount.unified's kdoc).
private fun AccountBalance?.unifiedBalance(): WalletBalance =
    this?.let { it.orchard + it.ironwood } ?: createEmptyWalletBalance()

private fun AccountBalance?.saplingBalance(): WalletBalance = this?.sapling ?: createEmptyWalletBalance()

private fun AccountBalance?.ironwoodBalance(): WalletBalance = this?.ironwood ?: createEmptyWalletBalance()

private fun AccountBalance?.transparentBalance(): Zatoshi = this?.unshielded ?: Zatoshi.ZERO

private fun createEmptyWalletBalance() = WalletBalance(Zatoshi.ZERO, Zatoshi.ZERO, Zatoshi.ZERO)

private operator fun WalletBalance.plus(other: WalletBalance) =
    WalletBalance(
        available = available + other.available,
        changePending = changePending + other.changePending,
        valuePending = valuePending + other.valuePending
    )

private data class AddressRequest(
    val accountUuid: AccountUuid,
    val responseChannel: Channel<WalletAddress.Unified>
)

private const val RETRY_DELAY = 3L

/**
 * Upper bound on how long [AccountDataSourceImpl.requestNextShieldedAddress] waits for
 * [AccountDataSourceImpl.observeUnified]'s collector to deliver a rotated address. The collector
 * can be unsubscribed during a [kotlinx.coroutines.flow.retryWhen] backoff, so an unbounded wait
 * can hang indefinitely; on timeout the caller falls back to the account's current address.
 */
private val ADDRESS_REQUEST_TIMEOUT = 15.seconds

private const val KEYSTONE_KEYSOURCE = "keystone"

class AccountDeletionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
