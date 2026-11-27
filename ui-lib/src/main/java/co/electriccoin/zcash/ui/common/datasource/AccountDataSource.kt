package co.electriccoin.zcash.ui.common.datasource

import android.content.Context
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
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
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccountBindingData
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccountAlreadyExistsException
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccountImportException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.retainWhileWalletExists
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.util.combineToFlow
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.util.loggableNot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
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

@Suppress("TooManyFunctions")
interface AccountDataSource {
    val allAccounts: StateFlow<List<WalletAccount>?>

    val selectedAccount: Flow<WalletAccount?>

    val zashiAccount: Flow<ZodlAccount?>

    suspend fun getAllAccounts(): List<WalletAccount>

    suspend fun getSelectedAccount(): WalletAccount

    suspend fun getZashiAccount(): ZodlAccount

    suspend fun selectAccount(account: Account)

    suspend fun selectAccount(account: WalletAccount)

    suspend fun importKeystoneAccount(
        ufvk: String,
        seedFingerprint: String,
        index: Long,
        birthday: BlockHeight? = null
    ): Account

    /**
     * Imports the account a Ledger device just exported and persists its binding next to it, so a
     * later signing session knows which device and ZIP 32 account to sign with.
     */
    suspend fun importLedgerAccount(
        pairing: LedgerAccountPairing,
        birthday: BlockHeight? = null
    ): Account

    /**
     * Imports [ufvk] as a view-only account named [name]. An SDK failure is reported without the
     * SDK's exception or message.
     *
     * The SDK is asked right before the import whether [ufvk] shares a component with an account the
     * wallet holds; any overlap refuses the import, so an existing account is never widened.
     *
     * @throws ViewOnlyAccountAlreadyExistsException if [ufvk] shares a component with an account the
     *         wallet holds, or the import returned an account the wallet already held. In that last
     *         case, only possible when the wallet changed between the check and the import, the SDK
     *         has already widened that account with [ufvk]; this reports it, it cannot undo it.
     * @throws ViewOnlyAccountImportException if the overlap check failed or the SDK rejected the
     *         import.
     */
    suspend fun importViewOnlyAccount(
        ufvk: String,
        name: String,
        birthday: BlockHeight? = null
    ): Account

    suspend fun requestNextShieldedAddress(): String

    suspend fun deleteAccount(account: WalletAccount)

    /**
     * Stores [name] as the name of the view-only [account] in the wallet database, from where
     * [allAccounts] picks it up. Hardware-wallet and Zodl accounts are never renamed.
     *
     * @throws cash.z.ecc.android.sdk.exception.InitializeException.RenameAccountException if the SDK
     *         could not store the name.
     */
    suspend fun renameAccount(
        account: ViewOnlyAccount,
        name: String
    )
}

@Suppress("TooManyFunctions")
class AccountDataSourceImpl(
    private val synchronizerProvider: SynchronizerProvider,
    private val selectedAccountUUIDProvider: SelectedAccountUUIDProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val ledgerAccountBindingProvider: LedgerAccountBindingProvider,
    private val viewOnlyContent: ViewOnlyContent,
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
                                        AccountSnapshot(
                                            balance = balance,
                                            unifiedAddress = unifiedAddress,
                                            transparentAddress = transparentAddress,
                                            saplingAddress = saplingAddress,
                                            isSelected = isSelected,
                                        )
                                    },
                                    observeLedgerBinding(sdkAccount),
                                ) { snapshot, ledgerBinding ->
                                    createWalletAccount(sdkAccount, snapshot, ledgerBinding)
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

    override val zashiAccount: Flow<ZodlAccount?> =
        allAccounts
            .map { account ->
                account?.filterIsInstance<ZodlAccount>()?.firstOrNull()
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

    /**
     * A Ledger account with no binding can never sign, so the import and the binding write are
     * all-or-nothing: if the binding cannot be stored, the just-imported account is deleted again
     * before the failure propagates, cancellation included, so the rollback runs non-cancellable.
     * It is best-effort — a failure to undo must not replace the exception that says what actually
     * went wrong.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun importLedgerAccount(
        pairing: LedgerAccountPairing,
        birthday: BlockHeight?
    ): Account =
        withContext(Dispatchers.IO) {
            val synchronizer = synchronizerProvider.getSynchronizer()
            val created =
                synchronizer.importAccountByUfvk(
                    pairing.accountImportSetup(
                        accountName = context.getString(R.string.accounts_ledger),
                        birthday = birthday,
                    )
                )
            try {
                ledgerAccountBindingProvider.save(
                    accountUuid = created.accountUuid,
                    deviceIdentityEncoding = pairing.binding.deviceIdentity.encoding,
                    zip32AccountIndex = pairing.binding.zip32AccountIndex.index,
                )
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    runCatching { synchronizer.deleteAccount(created.accountUuid) }
                        .onFailure { log("failed to roll back the Ledger account import", it) }
                }
                throw e
            }
            created
        }

    /**
     * The SDK lookup right before the import is the guard that keeps an existing account intact: an
     * import of a key that would widen an account succeeds in the SDK and replaces that account's
     * key, so any overlap the lookup finds refuses the import. A failed lookup fails the import
     * closed, since importing unchecked could widen an account; the caller can retry. The account
     * list compared after the import is a last guard for an account added in between.
     *
     * The import itself goes through [retryViewOnlyImport]: the engine refuses a wallet mutation
     * while it is still finishing a previous pass, for example the initial sync of an account
     * imported just before, and writes nothing when it does, so a failed import is retried quietly
     * before it is reported.
     */
    override suspend fun importViewOnlyAccount(
        ufvk: String,
        name: String,
        birthday: BlockHeight?
    ): Account =
        withContext(Dispatchers.IO) {
            val synchronizer = synchronizerProvider.getSynchronizer()
            val overlap =
                mapViewOnlyImportFailure {
                    synchronizer.findAccountSharingKeyComponents(UnifiedFullViewingKey(ufvk))
                }
            if (overlap != null) {
                throw ViewOnlyAccountAlreadyExistsException()
            }
            val existing = synchronizer.getAccounts()
            val created =
                mapViewOnlyImportFailure {
                    retryViewOnlyImport {
                        synchronizer.importAccountByUfvk(
                            AccountImportSetup(
                                accountName = name,
                                keySource = VIEW_ONLY_KEYSOURCE,
                                ufvk = UnifiedFullViewingKey(ufvk),
                                purpose = AccountPurpose.ViewOnly,
                                birthday = birthday,
                            )
                        )
                    }
                }
            if (existing.any { it.accountUuid == created.accountUuid }) {
                throw ViewOnlyAccountAlreadyExistsException()
            }
            created
        }

    /**
     * Maps an SDK failure of the view-only import: a key collision to
     * [ViewOnlyAccountAlreadyExistsException], anything else but cancellation to
     * [ViewOnlyAccountImportException]. Neither carries the SDK's exception or message.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun <T> mapViewOnlyImportFailure(block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: InitializeException.ImportAccountCollisionException) {
            throw ViewOnlyAccountAlreadyExistsException()
        } catch (e: Exception) {
            throw ViewOnlyAccountImportException()
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

    /**
     * The selection is cleared only once the SDK has deleted the account, so a failed deletion
     * leaves the account selected as it was, and only when the stored selection still names the
     * deleted account: [account] is a snapshot whose [WalletAccount.isSelected] can be stale, for
     * example when another account was selected while a confirmation was showing. Clearing the
     * selection or a Ledger account's stored binding both happen after the deletion has succeeded,
     * and only best-effort: the account is gone by then, so reporting a failure would tell the user to
     * retry something that cannot be retried. An orphaned binding is keyed by an account UUID that no
     * longer resolves.
     *
     * Between the cleared selection and the caller's next one there is a short window in which no
     * account UUID is stored and the selection falls back to the default account. DisconnectUseCase
     * and RemoveViewOnlyAccountUseCase close it by selecting the Zodl account right after this
     * returns, when the deleted account was the selected one. The order is deliberate: clearing the
     * selection before the deletion would leave a failed deletion with its account deselected. A
     * selection left naming the deleted account, when the clear never ran, falls back to the
     * default account as well.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteAccount(account: WalletAccount) =
        withContext(Dispatchers.IO) {
            val accountUuid = account.sdkAccount.accountUuid
            try {
                val synchronizer = synchronizerProvider.getSynchronizer()
                val deleted = synchronizer.deleteAccount(accountUuid)
                if (!deleted) {
                    throw AccountDeletionException("Failed to delete account")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw AccountDeletionException("Failed to delete account: ${e.message}", e)
            }
            bestEffort("failed to clear the selection of a deleted account") {
                if (selectedAccountUUIDProvider.getUUID() == accountUuid) {
                    selectedAccountUUIDProvider.clearUUID()
                }
            }
            if (account is LedgerAccount) {
                bestEffort("failed to clear the Ledger binding of a deleted account") {
                    ledgerAccountBindingProvider.clear(accountUuid)
                }
            }
        }

    override suspend fun renameAccount(
        account: ViewOnlyAccount,
        name: String
    ) = withContext(Dispatchers.IO) {
        synchronizerProvider.getSynchronizer().renameAccount(account.sdkAccount.accountUuid, name)
    }

    /**
     * Runs [block], logging instead of propagating its failure; cancellation still propagates.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun bestEffort(
        failureMessage: String,
        block: suspend () -> Unit
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(failureMessage, e)
        }
    }

    private fun isKeystoneAccount(sdkAccount: Account) = sdkAccount.keySource?.lowercase() == KEYSTONE_KEYSOURCE

    private fun isLedgerAccount(sdkAccount: Account) =
        sdkAccount.keySource?.lowercase() == Account.LEDGER_KEY_SOURCE

    private fun isHWAccount(sdkAccount: Account) =
        isKeystoneAccount(sdkAccount) || isLedgerAccount(sdkAccount)

    private fun isViewOnlyAccount(sdkAccount: Account) = sdkAccount.keySource?.lowercase() == VIEW_ONLY_KEYSOURCE

    /**
     * Accounts that have no Sapling receiver of their own and are never selected by default: the
     * hardware-wallet accounts and the view-only ones.
     */
    private fun isOrchardOnlyAccount(sdkAccount: Account) = isHWAccount(sdkAccount) || isViewOnlyAccount(sdkAccount)

    /**
     * A Ledger account with no stored binding still displays and receives; it just cannot derive
     * anything, which is why its index stays null rather than being guessed as zero. A view-only
     * account is matched before the Zashi fallback, which requires an HD index and a Sapling
     * address that such an account does not have. Its name is the one stored in the wallet database,
     * else the default view-only name.
     */
    private fun createWalletAccount(
        sdkAccount: Account,
        snapshot: AccountSnapshot,
        ledgerBinding: LedgerAccountBindingData?,
    ): WalletAccount =
        when {
            isLedgerAccount(sdkAccount) -> {
                LedgerAccount(
                    sdkAccount = sdkAccount,
                    unifiedAddress = snapshot.unifiedAddress,
                    transparentAddress = snapshot.transparentAddress,
                    orchardBalance = snapshot.balance?.orchard,
                    ironwoodBalance = snapshot.balance?.ironwood,
                    transparentBalance = snapshot.balance?.unshielded,
                    isSelected = snapshot.isSelected,
                    deviceIdentity = ledgerBinding?.deviceIdentityEncoding,
                    zip32AccountIndex = ledgerBinding?.zip32AccountIndex,
                )
            }

            isKeystoneAccount(sdkAccount) -> {
                KeystoneAccount(
                    sdkAccount = sdkAccount,
                    unifiedAddress = snapshot.unifiedAddress,
                    transparentAddress = snapshot.transparentAddress,
                    orchardBalance = snapshot.balance?.orchard,
                    ironwoodBalance = snapshot.balance?.ironwood,
                    transparentBalance = snapshot.balance?.unshielded,
                    isSelected = snapshot.isSelected,
                )
            }

            isViewOnlyAccount(sdkAccount) -> {
                val customName = sdkAccount.accountName?.takeIf { it.isNotBlank() }
                ViewOnlyAccount(
                    sdkAccount = sdkAccount,
                    unifiedAddress = snapshot.unifiedAddress,
                    transparentAddress = snapshot.transparentAddress,
                    orchardBalance = snapshot.balance?.orchard,
                    saplingBalance = snapshot.balance?.sapling,
                    ironwoodBalance = snapshot.balance?.ironwood,
                    transparentBalance = snapshot.balance?.unshielded,
                    isSelected = snapshot.isSelected,
                    name = customName?.let { stringRes(it) } ?: viewOnlyContent.defaultAccountName,
                    customName = customName,
                    icon = viewOnlyContent.accountIcon,
                )
            }

            else -> {
                ZodlAccount(
                    sdkAccount = sdkAccount,
                    unifiedAddress = snapshot.unifiedAddress,
                    transparentAddress = snapshot.transparentAddress,
                    saplingAddress = snapshot.saplingAddress!!,
                    orchardBalance = snapshot.balance?.orchard,
                    saplingBalance = snapshot.balance?.sapling,
                    ironwoodBalance = snapshot.balance?.ironwood,
                    transparentBalance = snapshot.balance?.unshielded,
                    isSelected = snapshot.isSelected,
                )
            }
        }

    private fun observeLedgerBinding(sdkAccount: Account): Flow<LedgerAccountBindingData?> =
        if (isLedgerAccount(sdkAccount)) {
            ledgerAccountBindingProvider.observe(sdkAccount.accountUuid)
        } else {
            flowOf(null)
        }

    /**
     * A stored UUID that names none of [allAccounts] counts as no stored UUID, so the default account
     * is selected. That heals a selection [deleteAccount] could not clear, for example when the
     * process died between the SDK deletion and the clear.
     */
    private fun observeIsSelected(sdkAccount: Account, allAccounts: List<Account>) =
        selectedAccountUUIDProvider
            .uuid
            .map { storedUuid ->
                storedUuid?.takeIf { stored -> allAccounts.any { it.accountUuid == stored } }
            }.map { uuid ->
                if (isOrchardOnlyAccount(sdkAccount)) {
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
                if (isOrchardOnlyAccount(sdkAccount)) {
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
        if (isOrchardOnlyAccount(sdkAccount)) {
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

/**
 * Everything one account's per-field flows contribute, bundled so the Ledger binding can be combined
 * in as a further source (kotlinx's typed [combine] tops out at five).
 */
private data class AccountSnapshot(
    val balance: AccountBalance?,
    val unifiedAddress: String,
    val transparentAddress: String,
    val saplingAddress: String?,
    val isSelected: Boolean,
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

/**
 * The key source a view-only import is stored with, which is how the account is recognised again.
 */
internal const val VIEW_ONLY_KEYSOURCE = "view_only"

/**
 * How many times [retryViewOnlyImport] tries the SDK's view-only import before it gives up.
 */
private const val VIEW_ONLY_IMPORT_ATTEMPTS = 3

/**
 * The wait before the first retry of [retryViewOnlyImport], in milliseconds; it doubles before
 * every further retry.
 */
private const val VIEW_ONLY_IMPORT_FIRST_RETRY_DELAY_MS = 2_000L

/**
 * Runs [attempt], the SDK's view-only import, up to [VIEW_ONLY_IMPORT_ATTEMPTS] times with a
 * doubling wait in between, and rethrows the last failure. The engine refuses a wallet mutation
 * while it is still finishing a previous pass and writes nothing when it does, so a later attempt
 * can succeed. Cancellation and a key collision are rethrown at once: neither changes on a retry.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend fun <T> retryViewOnlyImport(attempt: suspend () -> T): T {
    var retryDelay = VIEW_ONLY_IMPORT_FIRST_RETRY_DELAY_MS
    repeat(VIEW_ONLY_IMPORT_ATTEMPTS - 1) {
        try {
            return attempt()
        } catch (e: CancellationException) {
            throw e
        } catch (e: InitializeException.ImportAccountCollisionException) {
            throw e
        } catch (e: Exception) {
            delay(retryDelay)
            retryDelay *= 2
        }
    }
    return attempt()
}

class AccountDeletionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
