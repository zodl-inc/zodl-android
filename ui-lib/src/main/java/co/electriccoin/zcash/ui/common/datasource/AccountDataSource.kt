package co.electriccoin.zcash.ui.common.datasource

import android.content.Context
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Account
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
                                    observeIsSelected(sdkAccount, allSdkAccounts),
                                ) { unified, transparent, sapling, isSelected ->
                                    when (sdkAccount.keySource?.lowercase()) {
                                        KEYSTONE_KEYSOURCE -> {
                                            KeystoneAccount(
                                                sdkAccount = sdkAccount,
                                                unified = unified,
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
                    result = responseChannel.receive()
                    log("received address ${result.address} for $accountUuid")
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
                            val address = rotateAddress()
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
            synchronizer.walletBalances
                .map {
                    it?.get(sdkAccount.accountUuid)?.orchard ?: createEmptyWalletBalance()
                }

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
        return combine(transparentAddress, synchronizer.walletBalances) { address, balances ->
            val balance = balances?.get(sdkAccount.accountUuid)
            TransparentInfo(address = address, balance = balance?.unshielded ?: Zatoshi.ZERO)
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
            combine(saplingAddress, synchronizer.walletBalances) { address, balances ->
                val balance = balances?.get(sdkAccount.accountUuid)
                SaplingInfo(address = address, balance = balance?.sapling ?: createEmptyWalletBalance())
            }
        }

    private fun createEmptyWalletBalance() = WalletBalance(Zatoshi.ZERO, Zatoshi.ZERO, Zatoshi.ZERO)
}

private data class AddressRequest(
    val accountUuid: AccountUuid,
    val responseChannel: Channel<WalletAddress.Unified>
)

private const val RETRY_DELAY = 3L
private const val KEYSTONE_KEYSOURCE = "keystone"

class AccountDeletionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
