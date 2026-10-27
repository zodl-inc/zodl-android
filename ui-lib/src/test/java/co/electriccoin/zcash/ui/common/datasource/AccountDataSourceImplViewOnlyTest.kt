package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountImportSetup
import cash.z.ecc.android.sdk.model.AccountKeyOverlap
import cash.z.ecc.android.sdk.model.AccountPurpose
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.UnifiedAddressRequest
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccountAlreadyExistsException
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccountImportException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A view-only account as the data source maps and imports it: it is recognised by its key source
 * before the Zashi fallback (which would need an HD index and a Sapling address it lacks), asks for
 * an Orchard address only, is never selected by default, sorts after every other account, cannot
 * spend, and its import refuses a key the wallet already holds and never carries the key in any
 * exception it raises.
 */
class AccountDataSourceImplViewOnlyTest {
    private val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val account = Account.new(accountUuid)

    private val viewOnlyContent =
        mockk<ViewOnlyContent> {
            every { defaultAccountName } returns DEFAULT_NAME
            every { accountIcon } returns VIEW_ONLY_ICON
        }

    @Test
    fun viewOnlyKeySourceMapsToViewOnlyAccountWithoutTouchingTheHdIndexOrSapling() =
        runBlocking {
            val requests = mutableListOf<UnifiedAddressRequest>()
            val synchronizer =
                mockk<Synchronizer> {
                    every { accountsFlow } returns
                        MutableStateFlow(listOf(viewOnlySdkAccount(name = "Savings", uuid = accountUuid)))
                    every { walletBalances } returns MutableStateFlow(accountBalance(available = 100L, unshielded = 5L))
                    coEvery { getCustomUnifiedAddress(any(), capture(requests)) } returns "unified-address"
                    coEvery { getTransparentAddress(any()) } returns "transparent-address"
                }

            val accounts =
                withTimeout(5_000) {
                    dataSource(synchronizer, relaxedBindingProvider()).allAccounts.filterNotNull().first()
                }

            val viewOnly = accounts.single() as ViewOnlyAccount
            assertEquals(stringRes("Savings"), viewOnly.name)
            assertEquals("Savings", viewOnly.customName)
            assertEquals(VIEW_ONLY_ICON, viewOnly.icon)
            assertFalse(viewOnly.isSpendable)
            assertNull(viewOnly.hdAccountIndex)
            assertNull(viewOnly.saplingAddress)
            assertEquals(zeroBalance(), viewOnly.saplingBalance)
            assertEquals("unified-address", viewOnly.unifiedAddress)
            assertEquals("transparent-address", viewOnly.transparentAddress)
            assertEquals(Zatoshi(100L), viewOnly.spendableShieldedBalance)
            assertEquals(Zatoshi(105L), viewOnly.totalBalance)
            assertEquals(listOf<UnifiedAddressRequest>(UnifiedAddressRequest.Orchard), requests)
            coVerify(exactly = 0) { synchronizer.getSaplingAddress(any()) }
        }

    @Test
    fun aViewOnlyAccountCountsItsSaplingBalanceInEveryTotal() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer> {
                    every { accountsFlow } returns
                        MutableStateFlow(listOf(viewOnlySdkAccount(name = "Savings", uuid = accountUuid)))
                    every { walletBalances } returns
                        MutableStateFlow(
                            accountBalance(
                                available = 100L,
                                unshielded = 5L,
                                sapling =
                                    WalletBalance(
                                        available = Zatoshi(40L),
                                        changePending = Zatoshi(2L),
                                        valuePending = Zatoshi(3L),
                                    ),
                            )
                        )
                    coEvery { getCustomUnifiedAddress(any(), any()) } returns "unified-address"
                    coEvery { getTransparentAddress(any()) } returns "transparent-address"
                }

            val accounts =
                withTimeout(5_000) {
                    dataSource(synchronizer, relaxedBindingProvider()).allAccounts.filterNotNull().first()
                }

            val viewOnly = accounts.single() as ViewOnlyAccount
            assertNull(viewOnly.saplingAddress)
            assertEquals(Zatoshi(45L), viewOnly.saplingBalance?.total)
            assertEquals(Zatoshi(145L), viewOnly.totalShieldedBalance)
            assertEquals(Zatoshi(150L), viewOnly.totalBalance)
            assertEquals(Zatoshi(140L), viewOnly.spendableShieldedBalance)
            assertEquals(Zatoshi(5L), viewOnly.pendingShieldedBalance)
            coVerify(exactly = 0) { synchronizer.getSaplingAddress(any()) }
        }

    @Test
    fun aViewOnlyAccountFoldsSaplingLikeTheZodlAccount() {
        val orchard = WalletBalance(Zatoshi(10L), Zatoshi(1L), Zatoshi(2L))
        val sapling = WalletBalance(Zatoshi(20L), Zatoshi(3L), Zatoshi(4L))
        val ironwood = WalletBalance(Zatoshi(30L), Zatoshi(5L), Zatoshi(6L))
        val viewOnly =
            viewOnlyAccount().copy(
                orchardBalance = orchard,
                saplingBalance = sapling,
                ironwoodBalance = ironwood,
                transparentBalance = Zatoshi(7L),
            )
        val zodl =
            ZodlAccount(
                sdkAccount = account,
                unifiedAddress = "u",
                transparentAddress = "t",
                saplingAddress = "s",
                orchardBalance = orchard,
                saplingBalance = sapling,
                ironwoodBalance = ironwood,
                transparentBalance = Zatoshi(7L),
                isSelected = false,
            )

        assertEquals(zodl.totalBalance, viewOnly.totalBalance)
        assertEquals(zodl.totalShieldedBalance, viewOnly.totalShieldedBalance)
        assertEquals(zodl.spendableShieldedBalance, viewOnly.spendableShieldedBalance)
        assertEquals(zodl.pendingShieldedBalance, viewOnly.pendingShieldedBalance)
        assertEquals(Zatoshi(88L), viewOnly.totalBalance)
        assertNull(viewOnly.copy(saplingBalance = null).totalBalance)
    }

    @Test
    fun aViewOnlyAccountWithABlankNameFallsBackToTheDefaultName() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer> {
                    every { accountsFlow } returns MutableStateFlow(listOf(viewOnlySdkAccount(name = " ")))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getCustomUnifiedAddress(any(), any()) } returns "unified-address"
                    coEvery { getTransparentAddress(any()) } returns "transparent-address"
                }

            val accounts =
                withTimeout(5_000) {
                    dataSource(synchronizer, relaxedBindingProvider()).allAccounts.filterNotNull().first()
                }

            assertEquals(DEFAULT_NAME, accounts.single().name)
            assertNull((accounts.single() as ViewOnlyAccount).customName)
        }

    @Test
    fun theNameFollowsTheSdkAccountsNameAcrossARename() =
        runBlocking<Unit> {
            val sdkAccounts = MutableStateFlow(listOf(viewOnlySdkAccount(name = "Savings", uuid = accountUuid)))
            val synchronizer =
                mockk<Synchronizer> {
                    every { accountsFlow } returns sdkAccounts
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getCustomUnifiedAddress(any(), any()) } returns "unified-address"
                    coEvery { getTransparentAddress(any()) } returns "transparent-address"
                }
            val dataSource = dataSource(synchronizer, relaxedBindingProvider())

            withTimeout(5_000) {
                assertEquals(
                    stringRes("Savings"),
                    dataSource.allAccounts
                        .filterNotNull()
                        .first()
                        .single()
                        .name
                )
                sdkAccounts.value = listOf(viewOnlySdkAccount(name = "Vault", uuid = accountUuid))
                dataSource.allAccounts.first {
                    val renamed = it?.singleOrNull() as? ViewOnlyAccount
                    renamed?.name == stringRes("Vault") && renamed.customName == "Vault"
                }
            }
        }

    @Test
    fun renamingAViewOnlyAccountStoresTheNameThroughTheSdk() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                }

            dataSource(synchronizer, relaxedBindingProvider()).renameAccount(viewOnlyAccount(), "Vault")

            coVerify(exactly = 1) { synchronizer.renameAccount(accountUuid, "Vault") }
        }

    @Test
    fun aFailedRenamePropagatesTheSdkFailure() =
        runBlocking<Unit> {
            val failure = InitializeException.RenameAccountException(RuntimeException("Account not found"))
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { renameAccount(any(), any()) } throws failure
                }

            assertFailsWith<InitializeException.RenameAccountException> {
                dataSource(synchronizer, relaxedBindingProvider()).renameAccount(viewOnlyAccount(), "Vault")
            }
        }

    @Test
    fun aViewOnlyAccountIsNeverSelectedByDefault() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer> {
                    every { accountsFlow } returns
                        MutableStateFlow(listOf(account, viewOnlySdkAccount(name = "Savings")))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getCustomUnifiedAddress(any(), any()) } returns "unified-address"
                    coEvery { getTransparentAddress(any()) } returns "transparent-address"
                    coEvery { getSaplingAddress(any()) } returns "sapling-address"
                }

            val accounts =
                withTimeout(5_000) {
                    dataSource(synchronizer, relaxedBindingProvider()).allAccounts.filterNotNull().first()
                }

            assertTrue(accounts.filterIsInstance<ZodlAccount>().single().isSelected)
            assertFalse(accounts.filterIsInstance<ViewOnlyAccount>().single().isSelected)
        }

    @Test
    fun accountsSortDescendingWithViewOnlyAccountsLast() {
        val zodl =
            ZodlAccount(
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
        val ledger = ledgerAccount()
        val viewOnly = viewOnlyAccount()

        val sorted = listOf<WalletAccount>(viewOnly, ledger, zodl, keystone).sortedDescending()

        assertEquals(listOf(zodl, keystone, ledger, viewOnly), sorted)
        assertEquals(0, viewOnly.compareTo(viewOnlyAccount()))
        listOf<WalletAccount>(zodl, keystone, ledger).forEach { other ->
            assertTrue(other > viewOnly, "$other must sort above the view-only account")
            assertTrue(viewOnly < other)
        }
    }

    @Test
    fun onlyTheViewOnlyAccountIsNotSpendable() {
        assertTrue(ledgerAccount().isSpendable)
        assertFalse(viewOnlyAccount().isSpendable)
    }

    @Test
    fun aViewOnlyAccountToStringDoesNotLeakItsAddresses() {
        val printed = viewOnlyAccount().toString()

        assertFalse(printed.contains("u1secretaddress"))
        assertFalse(printed.contains("t1secretaddress"))
    }

    @Test
    fun importingAViewOnlyAccountUsesTheViewOnlyPurposeAndKeySource() =
        runBlocking {
            val setup: CapturingSlot<AccountImportSetup> = slot()
            val importedUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
            val imported = mockk<Account> { every { accountUuid } returns importedUuid }
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } returns null
                    coEvery { importAccountByUfvk(capture(setup)) } returns imported
                }

            val created =
                dataSource(synchronizer, relaxedBindingProvider())
                    .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)

            assertEquals(imported, created)
            assertEquals(AccountPurpose.ViewOnly, setup.captured.purpose)
            assertEquals(VIEW_ONLY_KEYSOURCE, setup.captured.keySource)
            assertEquals("Savings", setup.captured.accountName)
            assertEquals(UFVK, setup.captured.ufvk.encoding)
            coVerify(exactly = 1) { synchronizer.findAccountSharingKeyComponents(UnifiedFullViewingKey(UFVK)) }
        }

    @Test
    fun anImportThatReturnsAnExistingAccountIsReportedAsAlreadyImported() =
        runBlocking<Unit> {
            val existing =
                mockk<Account> {
                    every { accountUuid } returns this@AccountDataSourceImplViewOnlyTest.accountUuid
                    every { ufvk } returns "uview1differentencoding"
                }
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(existing)
                    coEvery { findAccountSharingKeyComponents(any()) } returns null
                    coEvery { importAccountByUfvk(any()) } returns existing
                }

            assertFailsWith<ViewOnlyAccountAlreadyExistsException> {
                dataSource(synchronizer, relaxedBindingProvider())
                    .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
            }
        }

    /**
     * The SDK's exception must not survive anywhere in the cause chain; the only cause allowed is
     * the copy of the wrapper itself that coroutines' stack-trace recovery may attach.
     */
    @Test
    fun aRejectedImportIsWrappedWithoutTheKeyAnywhereInItsStackTrace() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } returns null
                    coEvery { importAccountByUfvk(any()) } throws
                        IllegalArgumentException("Invalid UFVK: $UFVK", RuntimeException("parse_ufvk($UFVK)"))
                }

            val thrown =
                assertFailsWith<ViewOnlyAccountImportException> {
                    dataSource(synchronizer, relaxedBindingProvider())
                        .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
                }

            assertTrue(generateSequence<Throwable>(thrown) { it.cause }.all { it is ViewOnlyAccountImportException })
            assertFalse(thrown.stackTraceToString().contains("uview"))
            coVerify(exactly = 3) { synchronizer.importAccountByUfvk(any()) }
        }

    @Test
    fun anSdkAccountCollisionIsReportedAsAlreadyImportedWithoutItsCause() =
        runBlocking {
            val collision =
                InitializeException.ImportAccountCollisionException(
                    RuntimeException("An account corresponding to $UFVK already exists with AccountUuid(0102).")
                )
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } returns null
                    coEvery { importAccountByUfvk(any()) } throws collision
                }

            val thrown =
                assertFailsWith<ViewOnlyAccountAlreadyExistsException> {
                    dataSource(synchronizer, relaxedBindingProvider())
                        .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
                }

            val causes = generateSequence<Throwable>(thrown) { it.cause }
            assertTrue(causes.all { it is ViewOnlyAccountAlreadyExistsException })
            assertFalse(thrown.stackTraceToString().contains("uview"))
            assertFalse(thrown.stackTraceToString().contains("AccountUuid"))
            coVerify(exactly = 1) { synchronizer.importAccountByUfvk(any()) }
        }

    @Test
    fun aKeyIdenticalToAnAccountsKeyIsLeftToTheSdkAndReportedAsAlreadyImported() =
        runBlocking<Unit> {
            val existing =
                mockk<Account> {
                    every { accountUuid } returns this@AccountDataSourceImplViewOnlyTest.accountUuid
                    every { ufvk } returns UFVK
                }
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(existing)
                    coEvery { findAccountSharingKeyComponents(any()) } returns null
                    coEvery { importAccountByUfvk(any()) } throws
                        InitializeException.ImportAccountCollisionException(null)
                }

            assertFailsWith<ViewOnlyAccountAlreadyExistsException> {
                dataSource(synchronizer, relaxedBindingProvider())
                    .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
            }
        }

    @Test
    fun anUntypedErrorMentioningACollisionIsAnImportFailure() =
        runBlocking<Unit> {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } returns null
                    coEvery { importAccountByUfvk(any()) } throws
                        InitializeException.ImportAccountException(
                            RuntimeException(
                                "An account corresponding to the data provided already exists in the wallet."
                            )
                        )
                }

            assertFailsWith<ViewOnlyAccountImportException> {
                dataSource(synchronizer, relaxedBindingProvider())
                    .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
            }
        }

    @Test
    fun aCancelledImportIsRethrownNotWrapped() =
        runBlocking<Unit> {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } returns null
                    coEvery { importAccountByUfvk(any()) } throws CancellationException("cancelled")
                }

            assertFailsWith<CancellationException> {
                dataSource(synchronizer, relaxedBindingProvider())
                    .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
            }
            coVerify(exactly = 1) { synchronizer.importAccountByUfvk(any()) }
        }

    @Test
    fun anOverlapOfAnyKindRefusesTheImportWithoutImporting() =
        runBlocking {
            AccountKeyOverlap.Kind.entries.forEach { kind ->
                val synchronizer =
                    mockk<Synchronizer>(relaxed = true) {
                        every { accountsFlow } returns MutableStateFlow(listOf(account))
                        every { walletBalances } returns MutableStateFlow(null)
                        coEvery { getAccounts() } returns listOf(account)
                        coEvery { findAccountSharingKeyComponents(any()) } returns AccountKeyOverlap(account, kind)
                    }

                assertFailsWith<ViewOnlyAccountAlreadyExistsException>(kind.name) {
                    dataSource(synchronizer, relaxedBindingProvider())
                        .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
                }
                coVerify(exactly = 0) { synchronizer.importAccountByUfvk(any()) }
            }
        }

    @Test
    fun aCollisionFoundByTheLookupIsReportedAsAlreadyImportedWithoutImporting() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } throws
                        InitializeException.ImportAccountCollisionException(
                            RuntimeException("$UFVK matches several accounts")
                        )
                }

            val thrown =
                assertFailsWith<ViewOnlyAccountAlreadyExistsException> {
                    dataSource(synchronizer, relaxedBindingProvider())
                        .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
                }

            val causes = generateSequence<Throwable>(thrown) { it.cause }
            assertTrue(causes.all { it is ViewOnlyAccountAlreadyExistsException })
            assertFalse(thrown.stackTraceToString().contains("uview"))
            coVerify(exactly = 0) { synchronizer.importAccountByUfvk(any()) }
        }

    /**
     * Importing a key that was never checked could widen an existing account, so a failed lookup
     * fails the import instead of going on without it.
     */
    @Test
    fun aFailedLookupFailsTheImportClosedWithoutTheKeyInItsStackTrace() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } throws
                        InitializeException.FindAccountSharingKeyComponentsException(
                            RuntimeException("lookup_ufvk($UFVK)")
                        )
                }

            val thrown =
                assertFailsWith<ViewOnlyAccountImportException> {
                    dataSource(synchronizer, relaxedBindingProvider())
                        .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
                }

            assertTrue(generateSequence<Throwable>(thrown) { it.cause }.all { it is ViewOnlyAccountImportException })
            assertFalse(thrown.stackTraceToString().contains("uview"))
            coVerify(exactly = 0) { synchronizer.importAccountByUfvk(any()) }
        }

    @Test
    fun aCancelledLookupIsRethrownWithoutImporting() =
        runBlocking {
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } throws CancellationException("cancelled")
                }

            assertFailsWith<CancellationException> {
                dataSource(synchronizer, relaxedBindingProvider())
                    .importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)
            }
            coVerify(exactly = 0) { synchronizer.importAccountByUfvk(any()) }
        }

    @Test
    fun noWrappedImportExceptionLeaksAKeyInItsStackTrace() {
        listOf(
            ViewOnlyAccountImportException(),
            ViewOnlyAccountAlreadyExistsException(),
        ).forEach { exception ->
            assertFalse(exception.stackTraceToString().contains("uview"), exception.toString())
        }
    }

    private fun viewOnlySdkAccount(
        name: String,
        uuid: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 2).toByte() }),
    ) = mockk<Account> {
        every { accountUuid } returns uuid
        every { keySource } returns VIEW_ONLY_KEYSOURCE
        every { accountName } returns name
        every { ufvk } returns UFVK
        every { hdAccountIndex } returns null
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

    private fun ledgerAccount() =
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

    private fun zeroBalance() = WalletBalance(Zatoshi(0), Zatoshi(0), Zatoshi(0))

    private fun accountBalance(
        available: Long,
        unshielded: Long,
        sapling: WalletBalance = zeroBalance(),
    ): Map<AccountUuid, AccountBalance> =
        mapOf(
            accountUuid to
                AccountBalance(
                    sapling = sapling,
                    orchard = zeroBalance().copy(available = Zatoshi(available)),
                    ironwood = zeroBalance(),
                    unshielded = Zatoshi(unshielded),
                )
        )

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

        const val UFVK = "uview1testkeythatmustneverbeprinted"

        val DEFAULT_NAME = stringRes(co.electriccoin.zcash.ui.R.string.accounts_zashi)
    }
}
