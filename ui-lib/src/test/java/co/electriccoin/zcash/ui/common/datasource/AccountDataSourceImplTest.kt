package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccountBindingData
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MOB-1723: a null balances snapshot from the synchronizer must propagate as null through every
 * account's balance fields — never suppressed (a fresh wallet's accounts stay invisible) and never
 * defaulted to zero (a truth claim the wallet hasn't made yet).
 */
class AccountDataSourceImplTest {
    private val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val account = Account.new(accountUuid)

    private fun zeroBalance() = WalletBalance(Zatoshi(0), Zatoshi(0), Zatoshi(0))

    private fun accountBalance(
        available: Long,
        unshielded: Long,
    ): Map<AccountUuid, AccountBalance> =
        mapOf(
            accountUuid to
                AccountBalance(
                    sapling = zeroBalance(),
                    orchard = zeroBalance().copy(available = Zatoshi(available)),
                    ironwood = zeroBalance(),
                    unshielded = Zatoshi(unshielded),
                )
        )

    private fun dataSource(
        walletBalances: MutableStateFlow<Map<AccountUuid, AccountBalance>?>,
        sdkAccount: Account = account,
        ledgerBinding: LedgerAccountBindingData? = null,
    ): AccountDataSourceImpl {
        val synchronizer =
            mockk<Synchronizer> {
                every { accountsFlow } returns MutableStateFlow(listOf(sdkAccount))
                every { this@mockk.walletBalances } returns walletBalances
                coEvery { getCustomUnifiedAddress(any(), any()) } returns "unified-address"
                coEvery { getTransparentAddress(any()) } returns "transparent-address"
                coEvery { getSaplingAddress(any()) } returns "sapling-address"
            }
        val synchronizerProvider =
            mockk<SynchronizerProvider> {
                every { this@mockk.synchronizer } returns MutableStateFlow(synchronizer)
            }
        val selectedAccountUUIDProvider =
            mockk<SelectedAccountUUIDProvider> {
                every { uuid } returns MutableStateFlow(null)
            }
        val persistableWalletProvider =
            mockk<PersistableWalletProvider> {
                every { persistableWallet } returns MutableStateFlow(mockk(relaxed = true))
            }
        val ledgerAccountBindingProvider =
            mockk<LedgerAccountBindingProvider> {
                every { observe(any()) } returns MutableStateFlow(ledgerBinding)
            }
        return AccountDataSourceImpl(
            synchronizerProvider = synchronizerProvider,
            selectedAccountUUIDProvider = selectedAccountUUIDProvider,
            persistableWalletProvider = persistableWalletProvider,
            ledgerAccountBindingProvider = ledgerAccountBindingProvider,
            context = mockk(relaxed = true),
        )
    }

    @Test
    fun nullBalancesMapEmitsAccountWithNullBalancesRatherThanNoEmissionOrZeros() =
        runBlocking {
            val walletBalances = MutableStateFlow<Map<AccountUuid, AccountBalance>?>(null)
            val dataSource = dataSource(walletBalances)

            val accounts = withTimeout(5_000) { dataSource.allAccounts.filterNotNull().first() }

            assertEquals(1, accounts.size)
            val zashiAccount = accounts.single() as ZashiAccount
            assertNull(zashiAccount.unifiedBalance)
            assertNull(zashiAccount.transparentBalance)
            assertNull(zashiAccount.saplingBalance)
            assertNull(zashiAccount.ironwoodBalance)
            assertNull(zashiAccount.totalBalance)
            assertNull(zashiAccount.totalShieldedBalance)
            assertNull(zashiAccount.totalTransparentBalance)
            assertNull(zashiAccount.spendableShieldedBalance)
            assertNull(zashiAccount.pendingShieldedBalance)
            assertNull(zashiAccount.isShieldedPending)
            assertNull(zashiAccount.isShieldingAvailable)
            assertNull(zashiAccount.isAllShielded)
            assertNull(zashiAccount.canSpend(Zatoshi(0)))
        }

    @Test
    fun realBalancesSnapshotPopulatesRealValues() =
        runBlocking {
            val walletBalances =
                MutableStateFlow<Map<AccountUuid, AccountBalance>?>(
                    accountBalance(available = 500_000L, unshielded = 250_000L)
                )
            val dataSource = dataSource(walletBalances)

            val accounts = withTimeout(5_000) { dataSource.allAccounts.filterNotNull().first() }

            assertEquals(1, accounts.size)
            val zashiAccount = accounts.single() as ZashiAccount
            val unifiedBalance = zashiAccount.unifiedBalance
            assertNotNull(unifiedBalance)
            assertEquals(Zatoshi(500_000L), unifiedBalance.available)
            assertEquals(Zatoshi(250_000L), zashiAccount.transparentBalance)
            assertEquals(Zatoshi(0), zashiAccount.saplingBalance?.available)
            assertEquals(Zatoshi(0), zashiAccount.ironwoodBalance?.available)
            assertEquals(Zatoshi(750_000L), zashiAccount.totalBalance)
            assertEquals(Zatoshi(500_000L), zashiAccount.spendableShieldedBalance)
        }

    @Test
    fun ledgerKeySourceMapsToLedgerAccountCarryingItsStoredBinding() =
        runBlocking {
            val ledgerAccount =
                mockk<Account> {
                    every { accountUuid } returns this@AccountDataSourceImplTest.accountUuid
                    every { keySource } returns "ledger"
                    every { ufvk } returns null
                    every { hdAccountIndex } returns null
                }
            val dataSource =
                dataSource(
                    walletBalances =
                        MutableStateFlow(accountBalance(available = 100L, unshielded = 0L)),
                    sdkAccount = ledgerAccount,
                    ledgerBinding =
                        LedgerAccountBindingData(
                            deviceIdentityEncoding = "tpk0-deadbeef",
                            zip32AccountIndex = Zip32AccountIndex.new(3L),
                        ),
                )

            val accounts = withTimeout(5_000) { dataSource.allAccounts.filterNotNull().first() }

            val account = accounts.single() as LedgerAccount
            assertEquals("tpk0-deadbeef", account.deviceIdentity)
            assertEquals(Zip32AccountIndex.new(3L), account.hdAccountIndex)
            assertNull(account.saplingAddress)
            assertNull(account.saplingBalance)
            assertEquals(Zatoshi(100L), account.spendableShieldedBalance)
        }

    @Test
    fun ledgerAccountWithoutStoredBindingStillDisplaysButHasNoIndex() =
        runBlocking {
            val ledgerAccount =
                mockk<Account> {
                    every { accountUuid } returns this@AccountDataSourceImplTest.accountUuid
                    every { keySource } returns "ledger"
                    every { ufvk } returns null
                    every { hdAccountIndex } returns null
                }
            val dataSource =
                dataSource(
                    walletBalances = MutableStateFlow(null),
                    sdkAccount = ledgerAccount,
                    ledgerBinding = null,
                )

            val accounts = withTimeout(5_000) { dataSource.allAccounts.filterNotNull().first() }

            val account = accounts.single() as LedgerAccount
            assertNull(account.deviceIdentity)
            assertNull(account.zip32AccountIndex)
            assertNull(account.hdAccountIndex)
            assertFalse(account.isBound)
        }

    @Test
    fun accountsSortDescendingAsZodlThenKeystoneThenLedger() {
        val zashi =
            ZashiAccount(
                sdkAccount = account,
                unifiedAddress = "u",
                transparentAddress = "t",
                saplingAddress = "s",
                orchardBalance = null,
                saplingBalance = null,
                ironwoodBalance = null,
                transparentBalance = null,
                isSelected = true,
            )
        val keystone =
            KeystoneAccount(
                sdkAccount = account,
                unifiedAddress = "u",
                transparentAddress = "t",
                orchardBalance = null,
                ironwoodBalance = null,
                transparentBalance = null,
                isSelected = false,
            )
        val ledger =
            LedgerAccount(
                sdkAccount = account,
                unifiedAddress = "u",
                transparentAddress = "t",
                orchardBalance = null,
                ironwoodBalance = null,
                transparentBalance = null,
                isSelected = false,
                deviceIdentity = null,
                zip32AccountIndex = Zip32AccountIndex.new(0L),
            )

        val sorted = listOf<WalletAccount>(ledger, keystone, zashi).sortedDescending()

        assertEquals(listOf(zashi, keystone, ledger), sorted)
    }

    @Test
    fun ledgerAccountToStringDoesNotLeakTheDeviceIdentity() {
        val ledger =
            LedgerAccount(
                sdkAccount = account,
                unifiedAddress = "u1secretaddress",
                transparentAddress = "t1secretaddress",
                orchardBalance = null,
                ironwoodBalance = null,
                transparentBalance = null,
                isSelected = false,
                deviceIdentity = "tpk0-deadbeef",
                zip32AccountIndex = Zip32AccountIndex.new(0L),
            )

        val printed = ledger.toString()

        assertFalse(printed.contains("tpk0-deadbeef"))
        assertFalse(printed.contains("u1secretaddress"))
        assertFalse(printed.contains("t1secretaddress"))
    }

    @Test
    fun aBindingThatCannotBeStoredRollsBackTheImportInsteadOfKeepingAnUnsignableAccount() =
        runBlocking {
            val importedUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
            val imported =
                mockk<Account> {
                    every { accountUuid } returns importedUuid
                }
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { importAccountByUfvk(any()) } returns imported
                    coEvery { deleteAccount(importedUuid) } returns true
                }
            val bindingFailure = RuntimeException("keystore unavailable")
            val ledgerAccountBindingProvider =
                mockk<LedgerAccountBindingProvider> {
                    every { observe(any()) } returns MutableStateFlow(null)
                    coEvery { save(any(), any(), any()) } throws bindingFailure
                }
            val dataSource = dataSource(synchronizer, ledgerAccountBindingProvider)

            val thrown =
                assertFailsWith<RuntimeException> {
                    dataSource.importLedgerAccount(pairing = mockk(relaxed = true), birthday = null)
                }

            assertEquals(bindingFailure.message, thrown.message)
            coVerify(exactly = 1) { synchronizer.deleteAccount(importedUuid) }
        }

    @Test
    fun anImportCancelledWhileStoringTheBindingStillRollsBackToTheEnd() =
        runBlocking {
            val importedUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
            val imported =
                mockk<Account> {
                    every { accountUuid } returns importedUuid
                }
            var isRolledBack = false
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { importAccountByUfvk(any()) } returns imported
                    coEvery { deleteAccount(importedUuid) } coAnswers {
                        delay(1)
                        isRolledBack = true
                        true
                    }
                }
            val saving = CompletableDeferred<Unit>()
            val ledgerAccountBindingProvider =
                mockk<LedgerAccountBindingProvider> {
                    every { observe(any()) } returns MutableStateFlow(null)
                    coEvery { save(any(), any(), any()) } coAnswers {
                        saving.complete(Unit)
                        awaitCancellation()
                    }
                }
            val dataSource = dataSource(synchronizer, ledgerAccountBindingProvider)

            val import = launch { dataSource.importLedgerAccount(pairing = mockk(relaxed = true), birthday = null) }
            saving.await()
            import.cancel()
            import.join()

            assertTrue(isRolledBack)
        }

    @Test
    fun aRollbackThatAlsoFailsStillReportsTheOriginalBindingFailure() =
        runBlocking {
            val importedUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
            val imported =
                mockk<Account> {
                    every { accountUuid } returns importedUuid
                }
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { importAccountByUfvk(any()) } returns imported
                    coEvery { deleteAccount(importedUuid) } throws RuntimeException("delete failed too")
                }
            val bindingFailure = RuntimeException("keystore unavailable")
            val ledgerAccountBindingProvider =
                mockk<LedgerAccountBindingProvider> {
                    every { observe(any()) } returns MutableStateFlow(null)
                    coEvery { save(any(), any(), any()) } throws bindingFailure
                }
            val dataSource = dataSource(synchronizer, ledgerAccountBindingProvider)

            val thrown =
                assertFailsWith<RuntimeException> {
                    dataSource.importLedgerAccount(pairing = mockk(relaxed = true), birthday = null)
                }

            assertEquals(bindingFailure.message, thrown.message)
        }

    @Test
    fun aSuccessfulImportStoresTheBindingForTheNewAccount() =
        runBlocking {
            val importedUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
            val imported = mockk<Account> { every { accountUuid } returns importedUuid }
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { importAccountByUfvk(any()) } returns imported
                }
            val ledgerAccountBindingProvider =
                mockk<LedgerAccountBindingProvider>(relaxed = true) {
                    every { observe(any()) } returns MutableStateFlow(null)
                }
            val pairing =
                mockk<LedgerAccountPairing>(relaxed = true) {
                    every { binding } returns
                        mockk(relaxed = true) {
                            every { deviceIdentity } returns mockk { every { encoding } returns "tpk0-deadbeef" }
                            every { zip32AccountIndex } returns Zip32AccountIndex.new(4L)
                        }
                }

            dataSource(synchronizer, ledgerAccountBindingProvider)
                .importLedgerAccount(pairing = pairing, birthday = null)

            coVerify(exactly = 1) {
                ledgerAccountBindingProvider.save(importedUuid, "tpk0-deadbeef", 4L)
            }
            coVerify(exactly = 0) { synchronizer.deleteAccount(any()) }
        }

    @Test
    fun deletingALedgerAccountAlsoDropsItsStoredBinding() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(any()) } returns true
                }
            val ledgerAccountBindingProvider =
                mockk<LedgerAccountBindingProvider>(relaxed = true) {
                    every { observe(any()) } returns MutableStateFlow(null)
                }
            val ledger = ledgerAccount()

            dataSource(synchronizer, ledgerAccountBindingProvider).deleteAccount(ledger)

            coVerify(exactly = 1) { ledgerAccountBindingProvider.clear(accountUuid) }
        }

    @Test
    fun aBindingThatCannotBeClearedDoesNotFailAnAlreadyCompletedDeletion() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(any()) } returns true
                }
            val ledgerAccountBindingProvider =
                mockk<LedgerAccountBindingProvider>(relaxed = true) {
                    every { observe(any()) } returns MutableStateFlow(null)
                    coEvery { clear(any()) } throws RuntimeException("keystore unavailable")
                }

            dataSource(synchronizer, ledgerAccountBindingProvider).deleteAccount(ledgerAccount())

            coVerify(exactly = 1) { synchronizer.deleteAccount(accountUuid) }
        }

    @Test
    fun deletingASoftwareAccountNeverTouchesTheLedgerBindingStore() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { deleteAccount(any()) } returns true
                }
            val ledgerAccountBindingProvider =
                mockk<LedgerAccountBindingProvider>(relaxed = true) {
                    every { observe(any()) } returns MutableStateFlow(null)
                }
            val zashi =
                ZashiAccount(
                    sdkAccount = account,
                    unifiedAddress = "u",
                    transparentAddress = "t",
                    saplingAddress = "s",
                    orchardBalance = null,
                    saplingBalance = null,
                    ironwoodBalance = null,
                    transparentBalance = null,
                    isSelected = false,
                )

            dataSource(synchronizer, ledgerAccountBindingProvider).deleteAccount(zashi)

            coVerify(exactly = 0) { ledgerAccountBindingProvider.clear(any()) }
        }

    private fun ledgerAccount() =
        LedgerAccount(
            sdkAccount = account,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            deviceIdentity = "tpk0-deadbeef",
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    private fun dataSource(
        synchronizer: Synchronizer,
        ledgerAccountBindingProvider: LedgerAccountBindingProvider,
    ): AccountDataSourceImpl {
        val synchronizerProvider =
            mockk<SynchronizerProvider> {
                every { this@mockk.synchronizer } returns MutableStateFlow(synchronizer)
                coEvery { getSynchronizer() } returns synchronizer
            }
        return AccountDataSourceImpl(
            synchronizerProvider = synchronizerProvider,
            selectedAccountUUIDProvider =
                mockk {
                    every { uuid } returns MutableStateFlow(null)
                },
            persistableWalletProvider =
                mockk {
                    every { persistableWallet } returns MutableStateFlow(mockk(relaxed = true))
                },
            ledgerAccountBindingProvider = ledgerAccountBindingProvider,
            context = mockk(relaxed = true),
        )
    }
}
