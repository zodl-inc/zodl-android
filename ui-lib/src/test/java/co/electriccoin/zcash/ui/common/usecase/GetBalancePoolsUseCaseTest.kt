package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * MOB-1723 regression: [co.electriccoin.zcash.ui.common.provider.retainWhileWalletExists] must be
 * scoped per selected account (via `flatMapLatest`), not span the whole account x balances combine —
 * otherwise switching to an account whose entry is missing from the balances map resurrects the
 * PREVIOUS account's retained balances instead of correctly reporting "not loaded yet".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetBalancePoolsUseCaseTest {
    private val accountAUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val accountBUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })

    private fun zeroBalance() = WalletBalance(Zatoshi(0), Zatoshi(0), Zatoshi(0))

    private fun account(uuid: AccountUuid): WalletAccount =
        ZashiAccount(
            sdkAccount = Account.new(uuid),
            unifiedAddress = "unified-$uuid",
            transparentAddress = "transparent-$uuid",
            saplingAddress = "sapling-$uuid",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun balancesFor(
        uuid: AccountUuid,
        available: Long,
    ): Map<AccountUuid, AccountBalance> =
        mapOf(
            uuid to
                AccountBalance(
                    sapling = zeroBalance(),
                    orchard = zeroBalance().copy(available = Zatoshi(available)),
                    ironwood = zeroBalance(),
                    unshielded = Zatoshi(0),
                )
        )

    @Test
    fun switchingToAccountMissingFromBalancesMapEmitsNullNotThePreviousAccountsBalances() =
        runTest {
            val selectedAccount = MutableStateFlow<WalletAccount?>(account(accountAUuid))
            val walletBalances =
                MutableStateFlow<Map<AccountUuid, AccountBalance>?>(balancesFor(accountAUuid, available = 500_000L))
            val synchronizer = mockk<Synchronizer> { every { this@mockk.walletBalances } returns walletBalances }
            val synchronizerProvider =
                mockk<SynchronizerProvider> { every { this@mockk.synchronizer } returns MutableStateFlow(synchronizer) }
            val accountDataSource =
                mockk<AccountDataSource> { every { this@mockk.selectedAccount } returns selectedAccount }
            val persistableWalletProvider =
                mockk<PersistableWalletProvider> {
                    every { persistableWallet } returns MutableStateFlow(mockk(relaxed = true))
                }

            val useCase =
                GetBalancePoolsUseCase(
                    synchronizerProvider = synchronizerProvider,
                    accountDataSource = accountDataSource,
                    persistableWalletProvider = persistableWalletProvider,
                )

            val emissions = mutableListOf<BalancePools?>()
            val job = launch { useCase.observe().collect { emissions += it } }
            advanceUntilIdle()

            assertEquals(Zatoshi(500_000L), emissions.last()?.orchard, "account A's balance should be visible")

            selectedAccount.value = account(accountBUuid)
            advanceUntilIdle()

            assertNull(
                emissions.last(),
                "switching to account B, missing from the balances map, must emit null, not account A's balance",
            )

            job.cancelAndJoin()
        }
}
