package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deleting a view-only account: the selection is cleared only after the SDK deleted the account and
 * only when the stored selection still names it, whatever the account snapshot says, and clearing
 * the selection is best-effort once the account is gone. A stored selection the clear never reached,
 * naming an account that is gone, falls back to the Zodl account.
 */
class AccountDataSourceImplViewOnlyDeletionTest {
    private val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val account = Account.new(accountUuid)

    private val viewOnlyContent =
        mockk<ViewOnlyContent> {
            every { defaultAccountName } returns DEFAULT_NAME
            every { accountIcon } returns VIEW_ONLY_ICON
        }

    @Test
    fun deletingTheSelectedViewOnlyAccountClearsTheSelectionAfterwards() =
        runBlocking {
            val selection = selectionProvider(selected = accountUuid)
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(accountUuid) } returns true
                }

            dataSource(synchronizer, relaxedBindingProvider(), selectedAccountUUIDProvider = selection)
                .deleteAccount(viewOnlyAccount().copy(isSelected = true))

            coVerifyOrder {
                synchronizer.deleteAccount(accountUuid)
                selection.clearUUID()
            }
        }

    @Test
    fun aFailedDeletionKeepsTheSelection() =
        runBlocking {
            val selection = selectionProvider(selected = accountUuid)
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(accountUuid) } returns false
                }

            assertFailsWith<AccountDeletionException> {
                dataSource(synchronizer, relaxedBindingProvider(), selectedAccountUUIDProvider = selection)
                    .deleteAccount(viewOnlyAccount().copy(isSelected = true))
            }

            coVerify(exactly = 0) { selection.clearUUID() }
        }

    @Test
    fun aStaleSelectedSnapshotDoesNotClearTheSelectionOfAnotherAccount() =
        runBlocking {
            val otherUuid = AccountUuid.new(ByteArray(16) { (it + 7).toByte() })
            val selection = selectionProvider(selected = otherUuid)
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(accountUuid) } returns true
                }

            dataSource(synchronizer, relaxedBindingProvider(), selectedAccountUUIDProvider = selection)
                .deleteAccount(viewOnlyAccount().copy(isSelected = true))

            coVerify(exactly = 1) { synchronizer.deleteAccount(accountUuid) }
            coVerify(exactly = 0) { selection.clearUUID() }
        }

    @Test
    fun aPreferencesFailureAfterASuccessfulDeletionIsNotReported() =
        runBlocking {
            val selection =
                mockk<SelectedAccountUUIDProvider>(relaxed = true) {
                    every { uuid } returns MutableStateFlow(accountUuid)
                    coEvery { getUUID() } returns accountUuid
                    coEvery { clearUUID() } throws IllegalStateException("preferences unavailable")
                }
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(accountUuid) } returns true
                }

            dataSource(
                synchronizer = synchronizer,
                ledgerAccountBindingProvider = relaxedBindingProvider(),
                selectedAccountUUIDProvider = selection,
            ).deleteAccount(viewOnlyAccount().copy(isSelected = true))

            coVerify(exactly = 1) { selection.clearUUID() }
        }

    @Test
    fun aFailedSelectionReadAfterASuccessfulDeletionIsNotReported() =
        runBlocking {
            val selection =
                mockk<SelectedAccountUUIDProvider>(relaxed = true) {
                    every { uuid } returns MutableStateFlow(accountUuid)
                    coEvery { getUUID() } throws IllegalStateException("preferences unavailable")
                }
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(accountUuid) } returns true
                }

            dataSource(
                synchronizer = synchronizer,
                ledgerAccountBindingProvider = relaxedBindingProvider(),
                selectedAccountUUIDProvider = selection,
            ).deleteAccount(viewOnlyAccount())

            coVerify(exactly = 1) { synchronizer.deleteAccount(accountUuid) }
        }

    @Test
    fun deletingAnUnselectedAccountKeepsTheSelection() =
        runBlocking {
            val selection = selectionProvider(selected = null)
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(accountUuid) } returns true
                }

            dataSource(synchronizer, relaxedBindingProvider(), selectedAccountUUIDProvider = selection)
                .deleteAccount(viewOnlyAccount())

            coVerify(exactly = 0) { selection.clearUUID() }
        }

    @Test
    fun aStoredSelectionNamingADeletedAccountFallsBackToTheZodlAccount() =
        runBlocking {
            val deletedUuid = AccountUuid.new(ByteArray(16) { (it + 7).toByte() })
            val viewOnlySdkAccount =
                mockk<Account> {
                    every { accountUuid } returns AccountUuid.new(ByteArray(16) { (it + 2).toByte() })
                    every { keySource } returns VIEW_ONLY_KEYSOURCE
                    every { accountName } returns "Savings"
                    every { hdAccountIndex } returns null
                }
            val synchronizer =
                mockk<Synchronizer> {
                    every { accountsFlow } returns MutableStateFlow(listOf(account, viewOnlySdkAccount))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getCustomUnifiedAddress(any(), any()) } returns "unified-address"
                    coEvery { getTransparentAddress(any()) } returns "transparent-address"
                    coEvery { getSaplingAddress(any()) } returns "sapling-address"
                }
            val dataSource =
                dataSource(
                    synchronizer = synchronizer,
                    ledgerAccountBindingProvider = relaxedBindingProvider(),
                    selectedAccountUUIDProvider = selectionProvider(selected = deletedUuid),
                )

            val accounts = withTimeout(5_000) { dataSource.allAccounts.filterNotNull().first() }
            val selected = withTimeout(5_000) { dataSource.getSelectedAccount() }

            assertTrue(accounts.filterIsInstance<ZodlAccount>().single().isSelected)
            assertFalse(accounts.filterIsInstance<ViewOnlyAccount>().single().isSelected)
            assertTrue(selected is ZodlAccount)
        }

    private fun viewOnlyAccount() =
        ViewOnlyAccount(
            sdkAccount = account,
            unifiedAddress = "u1secretaddress",
            transparentAddress = "t1secretaddress",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = VIEW_ONLY_ICON,
        )

    private fun relaxedBindingProvider() =
        mockk<LedgerAccountBindingProvider>(relaxed = true) {
            every { observe(any()) } returns MutableStateFlow(null)
        }

    private fun selectionProvider(selected: AccountUuid?) =
        mockk<SelectedAccountUUIDProvider>(relaxed = true) {
            every { uuid } returns MutableStateFlow(selected)
            coEvery { getUUID() } returns selected
        }

    private fun dataSource(
        synchronizer: Synchronizer,
        ledgerAccountBindingProvider: LedgerAccountBindingProvider,
        selectedAccountUUIDProvider: SelectedAccountUUIDProvider = selectionProvider(selected = null),
    ): AccountDataSourceImpl {
        val synchronizerProvider =
            mockk<SynchronizerProvider> {
                every { this@mockk.synchronizer } returns MutableStateFlow(synchronizer)
                coEvery { getSynchronizer() } returns synchronizer
            }
        return AccountDataSourceImpl(
            synchronizerProvider = synchronizerProvider,
            selectedAccountUUIDProvider = selectedAccountUUIDProvider,
            persistableWalletProvider =
                mockk {
                    every { persistableWallet } returns MutableStateFlow(mockk(relaxed = true))
                },
            ledgerAccountBindingProvider = ledgerAccountBindingProvider,
            viewOnlyContent = viewOnlyContent,
            context = mockk(relaxed = true),
        )
    }

    private companion object {
        const val VIEW_ONLY_ICON = 42

        val DEFAULT_NAME = stringRes(co.electriccoin.zcash.ui.R.string.accounts_zashi)
    }
}
