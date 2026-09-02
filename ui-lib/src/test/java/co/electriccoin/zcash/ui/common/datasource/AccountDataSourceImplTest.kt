package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        walletBalances: MutableStateFlow<Map<AccountUuid, AccountBalance>?>
    ): AccountDataSourceImpl {
        val synchronizer =
            mockk<Synchronizer> {
                every { accountsFlow } returns MutableStateFlow(listOf(account))
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
        return AccountDataSourceImpl(
            synchronizerProvider = synchronizerProvider,
            selectedAccountUUIDProvider = selectedAccountUUIDProvider,
            persistableWalletProvider = persistableWalletProvider,
            context = mockk(relaxed = true),
        )
    }

    private suspend fun <T : Any> awaitValue(timeoutMs: Long = 5_000, poll: () -> T?): T =
        withTimeout(timeoutMs) {
            var result = poll()
            while (result == null) {
                delay(10)
                result = poll()
            }
            result
        }

    @Test
    fun nullBalancesMapEmitsAccountWithNullBalancesRatherThanNoEmissionOrZeros() =
        runBlocking {
            val walletBalances = MutableStateFlow<Map<AccountUuid, AccountBalance>?>(null)
            val dataSource = dataSource(walletBalances)

            val accounts = awaitValue { dataSource.allAccounts.value }

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

            val accounts = awaitValue { dataSource.allAccounts.value }

            assertEquals(1, accounts.size)
            val zashiAccount = accounts.single() as ZashiAccount
            assertNotNull(zashiAccount.unifiedBalance)
            assertEquals(Zatoshi(500_000L), zashiAccount.unifiedBalance.available)
            assertEquals(Zatoshi(250_000L), zashiAccount.transparentBalance)
            assertEquals(Zatoshi(0), zashiAccount.saplingBalance?.available)
            assertEquals(Zatoshi(0), zashiAccount.ironwoodBalance?.available)
            assertEquals(Zatoshi(750_000L), zashiAccount.totalBalance)
            assertEquals(Zatoshi(500_000L), zashiAccount.spendableShieldedBalance)
        }
}
