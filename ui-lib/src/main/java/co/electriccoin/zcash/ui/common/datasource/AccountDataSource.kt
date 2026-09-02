package co.electriccoin.zcash.ui.common.datasource

import android.content.Context
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountImportSetup
import cash.z.ecc.android.sdk.model.AccountPurpose
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.UnifiedAddressRequest
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.retainWhileWalletExists
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

    suspend fun getZashiAccount(): ZashiAccount

    suspend fun selectAccount(account: Account)

    suspend fun selectAccount(account: WalletAccount)

    suspend fun importKeystoneAccount(
        ufvk: String,
        seedFingerprint: String,
        index: Long,
        birthday: BlockHeight? = null
    ): Account

    suspend fun requestNextShieldedAddress(): String

    suspend fun deleteAccount(account: WalletAccount)
}

@Suppress("TooManyFunctions")
class AccountDataSourceImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val selectedAccountUUIDProvider: SelectedAccountUUIDProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val context: Context,
) : AccountDataSource {
    private val log = loggableNot("AccountDataSource")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val requestNextShieldedAddressChannel = MutableSharedFlow<AddressRequest>()

    /**
     * A null balances snapshot from the synchronizer propagates as null all the way through each
     * account's balance fields (never suppressed, never defaulted to zero) — a fresh wallet's
     * accounts are visible immediately, with their balances arriving once the synchronizer reports
     * them. Zero is only ever a real reported value, never a stand-in for "not loaded yet".
     */
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
                                    observeAccountBalance(synchronizer, sdkAccount),
                                    observeUnifiedAddress(synchronizer, sdkAccount),
                                    observeTransparentAddress(synchronizer, sdkAccount),
                                    observeSaplingAddress(synchronizer, sdkAccount),
                                    observeIsSelected(sdkAccount, allSdkAccounts),
                                ) {
                                    balance,
                                    unifiedAddress,
                                    transparentAddress,
                                    saplingAddress,
                                    isSelected,
                                    ->
                                    if (isKeystoneAccount(sdkAccount)) {
                                        KeystoneAccount(
                                            sdkAccount = sdkAccount,
                                            unifiedAddress = unifiedAddress,
                                            transparentAddress = transparentAddress,
                                            orchardBalance = balance?.orchard,
                                            ironwoodBalance = balance?.ironwood,
                                            transparentBalance = balance?.unshielded,
                                            isSelected = isSelected,
                                        )
                                    } else {
                                        ZashiAccount(
                                            sdkAccount = sdkAccount,
                                            unifiedAddress = unifiedAddress,
                                            transparentAddress = transparentAddress,
                                            saplingAddress = saplingAddress!!,
                                            orchardBalance = balance?.orchard,
                                            saplingBalance = balance?.sapling,
                                            ironwoodBalance = balance?.ironwood,
                                            transparentBalance = balance?.unshielded,
                                            isSelected = isSelected,
                                        )
                                    }
                                }
                            }.combineToFlow()
                    }
                    ?: flowOf(null)
            }.map { it?.sortedDescending() }
            .retainWhileWalletExists(persistableWalletProvider)
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
    override suspend fun requestNextShieldedAddress(): String {
        var result: String? = null
        scope
            .launch {
                val accountUuid = getSelectedAccount().sdkAccount.accountUuid
                log("requestNextShieldedAddress for $accountUuid")
                val responseChannel = Channel<String>(1)
                requestNextShieldedAddressChannel.emit(AddressRequest(accountUuid, responseChannel))
                try {
                    val address = withTimeoutOrNull(ADDRESS_REQUEST_TIMEOUT) { responseChannel.receive() }
                    if (address == null) {
                        log("timed out waiting for address for $accountUuid")
                    } else {
                        log("received address $address for $accountUuid")
                    }
                    result = address
                } catch (e: Exception) {
                    log("failed to receive address for $accountUuid", e)
                }
            }.join()
        return (result ?: getSelectedAccount().unifiedAddress).also {
            log("returning address $it")
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

    private fun isKeystoneAccount(sdkAccount: Account) = sdkAccount.keySource?.lowercase() == KEYSTONE_KEYSOURCE

    private fun observeIsSelected(sdkAccount: Account, allAccounts: List<Account>) =
        selectedAccountUUIDProvider
            .uuid
            .map { uuid ->
                if (isKeystoneAccount(sdkAccount)) {
                    sdkAccount.accountUuid == uuid || allAccounts.size == 1
                } else {
                    uuid == null || sdkAccount.accountUuid == uuid || allAccounts.size == 1
                }
            }

    @Suppress("TooGenericExceptionCaught")
    private fun observeUnifiedAddress(synchronizer: Synchronizer, sdkAccount: Account): Flow<String> {
        suspend fun rotateAddress(): String {
            log("deriving unified address for ${sdkAccount.accountUuid}")

            val addressRequest =
                if (isKeystoneAccount(sdkAccount)) {
                    UnifiedAddressRequest.Orchard
                } else {
                    UnifiedAddressRequest.shielded
                }

            val address = synchronizer.getCustomUnifiedAddress(sdkAccount, addressRequest)

            log("derived address $address for ${sdkAccount.accountUuid}")

            return address
        }

        return channelFlow {
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
    }

    /**
     * The account's full balance snapshot, or null while the synchronizer hasn't reported one yet
     * — see [allAccounts]'s kdoc for the null-propagation contract this feeds.
     */
    private fun observeAccountBalance(synchronizer: Synchronizer, sdkAccount: Account): Flow<AccountBalance?> =
        synchronizer.walletBalances
            .map { balances -> balances?.get(sdkAccount.accountUuid) }

    private fun observeTransparentAddress(synchronizer: Synchronizer, sdkAccount: Account): Flow<String> =
        flow {
            emit(synchronizer.getTransparentAddress(sdkAccount))
        }.retryWhen { _, attempt ->
            delay(attempt.coerceAtMost(RETRY_DELAY).seconds)
            true
        }

    private fun observeSaplingAddress(synchronizer: Synchronizer, sdkAccount: Account): Flow<String?> =
        if (isKeystoneAccount(sdkAccount)) {
            flowOf(null)
        } else {
            flow {
                emit(synchronizer.getSaplingAddress(sdkAccount))
            }.retryWhen { _, attempt ->
                delay(attempt.coerceAtMost(RETRY_DELAY).seconds)
                true
            }
        }
}

private data class AddressRequest(
    val accountUuid: AccountUuid,
    val responseChannel: Channel<String>
)

private const val RETRY_DELAY = 3L

/**
 * Upper bound on how long [AccountDataSourceImpl.requestNextShieldedAddress] waits for
 * [AccountDataSourceImpl.observeUnifiedAddress]'s collector to deliver a rotated address. The
 * collector can be unsubscribed during a [kotlinx.coroutines.flow.retryWhen] backoff, so an
 * unbounded wait can hang indefinitely; on timeout the caller falls back to the account's current
 * address.
 */
private val ADDRESS_REQUEST_TIMEOUT = 15.seconds

private const val KEYSTONE_KEYSOURCE = "keystone"

class AccountDeletionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
