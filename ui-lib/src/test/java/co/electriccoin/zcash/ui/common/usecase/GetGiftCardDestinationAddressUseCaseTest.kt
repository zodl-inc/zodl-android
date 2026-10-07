package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.UnifiedAddressRequest
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [GetGiftCardDestinationAddressUseCase] sweeps a gift card to the selected account's Orchard-only address, never to
 * a transparent or Sapling receiver.
 */
class GetGiftCardDestinationAddressUseCaseTest {
    private val account = mockk<Account>()

    private val walletAccount = mockk<WalletAccount> { every { sdkAccount } returns account }

    private val accountDataSource = mockk<AccountDataSource> { coEvery { getSelectedAccount() } returns walletAccount }

    private val mainSynchronizer = mockk<Synchronizer>()

    private val synchronizerProvider =
        mockk<SynchronizerProvider> { coEvery { getSynchronizer() } returns mainSynchronizer }

    @Test
    fun theDestinationIsTheSelectedAccountsOrchardOnlyAddress() =
        runTest {
            coEvery { mainSynchronizer.getCustomUnifiedAddress(account, UnifiedAddressRequest.Orchard) } returns
                ORCHARD_ADDRESS

            val address = GetGiftCardDestinationAddressUseCase(accountDataSource, synchronizerProvider)()

            assertEquals(ORCHARD_ADDRESS, address)
            coVerify(exactly = 1) { mainSynchronizer.getCustomUnifiedAddress(any(), any()) }
        }

    private companion object {
        const val ORCHARD_ADDRESS = "u1orchardonly"
    }
}
