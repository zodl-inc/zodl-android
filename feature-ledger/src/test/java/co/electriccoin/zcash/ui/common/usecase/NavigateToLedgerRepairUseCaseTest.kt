package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepositoryImpl
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectArgs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pair Ledger on the sign sheet ends the session and opens the connect flow with the selected
 * account, and its stored index, as the target of pairing again.
 */
class NavigateToLedgerRepairUseCaseTest {
    @Test
    fun theSelectedAccountBecomesTheTargetBeforeTheConnectFlowOpens() =
        runTest {
            val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
            val account =
                mockk<LedgerAccount> {
                    every { sdkAccount.accountUuid } returns accountUuid
                    every { zip32AccountIndex } returns Zip32AccountIndex.new(4)
                }
            val repairTarget = LedgerRepairTargetRepositoryImpl()
            val cancelLedgerSigning = mockk<CancelLedgerSigningUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)

            NavigateToLedgerRepairUseCase(
                accountDataSource =
                    mockk<AccountDataSource> {
                        coEvery { getSelectedAccount() } returns account
                    },
                ledgerRepairTargetRepository = repairTarget,
                cancelLedgerSigning = cancelLedgerSigning,
                navigationRouter = navigationRouter,
            ).invoke()

            assertEquals(accountUuid, repairTarget.get())
            assertEquals(Zip32AccountIndex.new(4), repairTarget.getZip32AccountIndex())
            verifyOrder {
                cancelLedgerSigning.invoke()
                navigationRouter.forward(LedgerConnectArgs)
            }
        }

    /**
     * An account whose binding holds no index, or one that is not a Ledger account at all, goes
     * without one, so the scan screen starts from the first account.
     */
    @Test
    fun anAccountWithoutAStoredIndexBecomesTheTargetWithNone() =
        runTest {
            val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
            val unbound =
                mockk<LedgerAccount> {
                    every { sdkAccount.accountUuid } returns accountUuid
                    every { zip32AccountIndex } returns null
                }
            val keystone =
                mockk<KeystoneAccount> {
                    every { sdkAccount.accountUuid } returns accountUuid
                }
            listOf<WalletAccount>(unbound, keystone).forEach { account ->
                val repairTarget = LedgerRepairTargetRepositoryImpl()
                repairTarget.set(AccountUuid.new(ByteArray(16)), Zip32AccountIndex.new(9))

                NavigateToLedgerRepairUseCase(
                    accountDataSource =
                        mockk<AccountDataSource> {
                            coEvery { getSelectedAccount() } returns account
                        },
                    ledgerRepairTargetRepository = repairTarget,
                    cancelLedgerSigning = mockk(relaxed = true),
                    navigationRouter = mockk(relaxed = true),
                ).invoke()

                assertEquals(accountUuid, repairTarget.get())
                assertNull(repairTarget.getZip32AccountIndex(), account.javaClass.simpleName)
            }
        }
}
