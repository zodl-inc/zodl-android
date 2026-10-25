package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The view-only import is retried while the engine refuses a wallet mutation because it is still
 * finishing a previous pass, for example the initial sync of an account imported just before; the
 * engine writes nothing when it refuses, so a later attempt can succeed. A key collision and a
 * cancellation are never retried.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDataSourceImplViewOnlyImportRetryTest {
    private val account = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() }))

    @Test
    fun anImportRefusedOnceIsRetriedAndReturnsTheAccount() =
        runBlocking {
            val imported = Account.new(AccountUuid.new(ByteArray(16) { (it + 1).toByte() }))
            val synchronizer =
                mockk<Synchronizer>(relaxed = true) {
                    every { accountsFlow } returns MutableStateFlow(listOf(account))
                    every { walletBalances } returns MutableStateFlow(null)
                    coEvery { getAccounts() } returns listOf(account)
                    coEvery { findAccountSharingKeyComponents(any()) } returns null
                    coEvery { importAccountByUfvk(any()) } throws refusal() andThen imported
                }

            val created =
                dataSource(synchronizer).importViewOnlyAccount(ufvk = UFVK, name = "Savings", birthday = null)

            assertEquals(imported, created)
            coVerify(exactly = 2) { synchronizer.importAccountByUfvk(any()) }
            coVerify(exactly = 1) { synchronizer.findAccountSharingKeyComponents(any()) }
            coVerify(exactly = 1) { synchronizer.getAccounts() }
        }

    @Test
    fun aRefusedAttemptIsRetriedAfterTheFirstBackoff() =
        runTest {
            var calls = 0

            val result =
                retryViewOnlyImport {
                    calls++
                    if (calls == 1) throw refusal()
                    "imported"
                }

            assertEquals("imported", result)
            assertEquals(2, calls)
            assertEquals(2_000L, testScheduler.currentTime)
        }

    @Test
    fun aRefusedImportGivesUpAfterThreeAttemptsWithADoublingBackoff() =
        runTest {
            var calls = 0

            assertFailsWith<InitializeException.ImportAccountException> {
                retryViewOnlyImport<Unit> {
                    calls++
                    throw refusal()
                }
            }

            assertEquals(3, calls)
            assertEquals(6_000L, testScheduler.currentTime)
        }

    @Test
    fun aKeyCollisionIsNotRetried() =
        runTest {
            var calls = 0

            assertFailsWith<InitializeException.ImportAccountCollisionException> {
                retryViewOnlyImport<Unit> {
                    calls++
                    throw InitializeException.ImportAccountCollisionException(null)
                }
            }

            assertEquals(1, calls)
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun aCancellationIsNotRetried() =
        runTest {
            var calls = 0

            assertFailsWith<CancellationException> {
                retryViewOnlyImport<Unit> {
                    calls++
                    throw CancellationException("cancelled")
                }
            }

            assertEquals(1, calls)
            assertEquals(0L, testScheduler.currentTime)
        }

    private fun refusal() = InitializeException.ImportAccountException(RuntimeException("mutation refused"))

    private fun dataSource(synchronizer: Synchronizer): AccountDataSourceImpl {
        val synchronizerProvider =
            mockk<SynchronizerProvider> {
                every { this@mockk.synchronizer } returns MutableStateFlow(synchronizer)
                coEvery { getSynchronizer() } returns synchronizer
            }
        return AccountDataSourceImpl(
            synchronizerProvider = synchronizerProvider,
            selectedAccountUUIDProvider =
                mockk(relaxed = true) {
                    every { uuid } returns MutableStateFlow(null)
                    coEvery { getUUID() } returns null
                },
            persistableWalletProvider =
                mockk {
                    every { persistableWallet } returns MutableStateFlow(mockk(relaxed = true))
                },
            ledgerAccountBindingProvider =
                mockk<LedgerAccountBindingProvider>(relaxed = true) {
                    every { observe(any()) } returns MutableStateFlow(null)
                },
            viewOnlyContent = mockk(relaxed = true),
            context = mockk(relaxed = true),
        )
    }

    private companion object {
        const val UFVK = "uview1testkeythatmustneverbeprinted"
    }
}
