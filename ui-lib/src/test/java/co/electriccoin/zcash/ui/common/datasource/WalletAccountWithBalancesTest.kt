package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.SaplingInfo
import co.electriccoin.zcash.ui.common.model.TransparentInfo
import co.electriccoin.zcash.ui.common.model.UnifiedInfo
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * [withBalances] is the mapping [AccountDataSource.refreshSelectedAccount] applies to a freshly read SDK
 * balance. It must fold the pools exactly like the account observers do (unified = Orchard + Ironwood)
 * while leaving addresses, selection and identity untouched.
 */
class WalletAccountWithBalancesTest {
    private val fresh =
        AccountBalance(
            sapling = balance(available = 100L, changePending = 10L, valuePending = 1L),
            orchard = balance(available = 200L, changePending = 20L, valuePending = 2L),
            ironwood = balance(available = 300L, changePending = 30L, valuePending = 3L),
            unshielded = Zatoshi(400L),
        )

    @Test
    fun zashiAccountFoldsOrchardAndIronwoodIntoUnifiedAndKeepsIdentity() =
        runBlocking {
            val account = zashiAccount()

            val refreshed = account.withBalances(fresh) as ZashiAccount

            assertEquals(balance(500L, 50L, 5L), refreshed.unified.balance)
            assertEquals(fresh.sapling, refreshed.sapling.balance)
            assertEquals(fresh.ironwood, refreshed.ironwoodBalance)
            assertEquals(Zatoshi(400L), refreshed.transparent.balance)
            assertEquals(Zatoshi(600L), refreshed.spendableShieldedBalance)
            assertSame(account.sdkAccount, refreshed.sdkAccount)
            assertEquals(account.unified.address, refreshed.unified.address)
            assertEquals(account.sapling.address, refreshed.sapling.address)
            assertEquals(account.transparent.address, refreshed.transparent.address)
            assertEquals(account.isSelected, refreshed.isSelected)
        }

    @Test
    fun zashiAccountCanSpendReflectsTheFreshBalanceNotTheStaleOne() =
        runBlocking {
            val stale = zashiAccount()
            assertEquals(true, stale.canSpend(Zatoshi(1_000L)))

            val drained = stale.withBalances(fresh.copy(sapling = zero(), orchard = zero(), ironwood = zero()))

            assertEquals(false, drained.canSpend(Zatoshi(1L)))
            assertEquals(true, drained.canSpend(Zatoshi(0L)))
        }

    @Test
    fun keystoneAccountIgnoresSaplingAndFoldsUnified() =
        runBlocking {
            val account = keystoneAccount()

            val refreshed = account.withBalances(fresh) as KeystoneAccount

            assertEquals(balance(500L, 50L, 5L), refreshed.unified.balance)
            assertEquals(fresh.ironwood, refreshed.ironwoodBalance)
            assertEquals(Zatoshi(400L), refreshed.transparent.balance)
            assertEquals(Zatoshi(500L), refreshed.spendableShieldedBalance)
            assertNull(refreshed.sapling)
            assertSame(account.sdkAccount, refreshed.sdkAccount)
        }

    private suspend fun zashiAccount() =
        ZashiAccount(
            sdkAccount = mockk<Account>(),
            unified = UnifiedInfo(WalletAddress.Unified.new("ua"), balance(1_000L, 0L, 0L)),
            sapling = SaplingInfo(WalletAddress.Sapling.new("zs"), balance(1_000L, 0L, 0L)),
            ironwoodBalance = balance(500L, 0L, 0L),
            transparent = TransparentInfo(WalletAddress.Transparent.new("t1"), Zatoshi(7L)),
            isSelected = true,
        )

    private suspend fun keystoneAccount() =
        KeystoneAccount(
            sdkAccount = mockk<Account>(),
            unified = UnifiedInfo(WalletAddress.Unified.new("ua"), balance(1_000L, 0L, 0L)),
            ironwoodBalance = balance(500L, 0L, 0L),
            transparent = TransparentInfo(WalletAddress.Transparent.new("t1"), Zatoshi(7L)),
            isSelected = true,
        )

    private fun balance(available: Long, changePending: Long, valuePending: Long) =
        WalletBalance(
            available = Zatoshi(available),
            changePending = Zatoshi(changePending),
            valuePending = Zatoshi(valuePending)
        )

    private fun zero() = balance(0L, 0L, 0L)
}
