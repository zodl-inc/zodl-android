package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.AddressBookDataSource
import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.AddressBookKeyStorageProvider
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.serialization.addressbook.AddressBookKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

interface AddressBookRepository {
    val contacts: Flow<List<EnhancedABContact>?>

    fun saveContact(
        name: String,
        address: String,
        chain: String?,
    )

    fun updateContact(
        contact: EnhancedABContact,
        name: String,
        address: String,
        chain: String?,
    )

    fun deleteContact(contact: EnhancedABContact)

    fun observeContactByAddress(address: String): Flow<EnhancedABContact?>

    fun delete()
}

data class EnhancedABContact(
    val contact: AddressBookContact,
    val blockchain: SwapBlockchain?
) {
    val name = contact.name
    val address = contact.address
    val lastUpdated = contact.lastUpdated
}

class AddressBookRepositoryImpl(
    private val addressBookDataSource: AddressBookDataSource,
    private val addressBookKeyStorageProvider: AddressBookKeyStorageProvider,
    private val accountDataSource: AccountDataSource,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val blockchainProvider: BlockchainProvider
) : AddressBookRepository {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val mutex = Mutex()

    @Suppress("TooGenericExceptionCaught", "MagicNumber")
    @OptIn(ExperimentalCoroutinesApi::class)
    private val addressBook =
        accountDataSource
            .zashiAccount
            .distinctUntilChangedBy { it?.sdkAccount?.accountUuid }
            .map { getAddressBookKey(it ?: return@map null) }
            .distinctUntilChanged()
            .flatMapLatest { if (it == null) flowOf(null) else addressBookDataSource.observe(it) }
            .retryWhen { _, attempt ->
                delay(attempt.coerceAtMost(3).seconds)
                true
            }.shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(0, 0),
                replay = 1
            )

    override val contacts: Flow<List<EnhancedABContact>?> =
        addressBook
            .map { ab ->
                ab?.contacts?.map { contact ->
                    EnhancedABContact(
                        contact = contact,
                        blockchain = contact.chain?.let { blockchainProvider.getBlockchain(it) }
                    )
                }
            }.distinctUntilChanged()

    override fun saveContact(
        name: String,
        address: String,
        chain: String?,
    ) = updateAB {
        Twig.info { "Address Book: saving a contact" }
        addressBookDataSource.saveContact(
            name = name,
            address = address,
            chain = chain,
            key = it
        )
    }

    override fun updateContact(
        contact: EnhancedABContact,
        name: String,
        address: String,
        chain: String?,
    ) = updateAB {
        addressBookDataSource.updateContact(
            contact = contact.contact,
            name = name,
            address = address,
            chain = chain,
            key = it
        )
    }

    override fun deleteContact(contact: EnhancedABContact) =
        updateAB {
            addressBookDataSource.deleteContact(
                addressBookContact = contact.contact,
                key = it
            )
        }

    override fun observeContactByAddress(address: String): Flow<EnhancedABContact?> =
        contacts
            .filterNotNull()
            .map {
                it.find { contact -> contact.address == address }
            }.distinctUntilChanged()

    @Suppress("TooGenericExceptionCaught")
    override fun delete() {
        scope.launch {
            mutex.withLock {
                try {
                    val account = accountDataSource.getZashiAccount()
                    val key = getAddressBookKey(account)
                    addressBookDataSource.delete(key)
                } catch (e: Exception) {
                    Twig.error(e) { "Unable to delete Address Book" }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun updateAB(block: suspend (AddressBookKey) -> Unit) {
        scope.launch {
            mutex.withLock {
                try {
                    val selectedAccount = accountDataSource.getZashiAccount()
                    val key = getAddressBookKey(selectedAccount)
                    block(key)
                } catch (e: Exception) {
                    Twig.error(e) { "Unable to update Address Book" }
                }
            }
        }
    }

    private suspend fun getAddressBookKey(zashiAccount: ZashiAccount): AddressBookKey {
        val key = addressBookKeyStorageProvider.getAddressBookKey()

        return if (key != null) {
            key
        } else {
            val persistableWallet = persistableWalletProvider.requirePersistableWallet()
            val newKey =
                AddressBookKey.derive(
                    seedPhrase = persistableWallet.seedPhrase,
                    network = persistableWallet.network,
                    account = zashiAccount
                )
            addressBookKeyStorageProvider.storeAddressBookKey(newKey)
            newKey
        }
    }
}
