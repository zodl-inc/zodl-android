package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessage
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A view-only account cannot migrate its funds, so the migration home banner stays empty for it and
 * the source never reads the engine's migration state on its behalf.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MigrationHomeMessageSourceViewOnlyTest {
    private val sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() }))

    private val observeMigrationLiveReadout = mockk<ObserveMigrationLiveReadoutUseCase>(relaxed = true)

    @Test
    fun aViewOnlyAccountGetsNoMigrationBanner() =
        runTest {
            val emissions = collectEmissions(source(viewOnly()))

            assertEquals(listOf(null), emissions)
            verify(exactly = 0) { observeMigrationLiveReadout(any()) }
        }

    @Test
    fun aSpendableAccountStillHasItsMigrationStateRead() =
        runTest {
            collectEmissions(source(zodl()))

            verify(exactly = 1) { observeMigrationLiveReadout(any()) }
        }

    private fun TestScope.collectEmissions(source: MigrationHomeMessageSourceImpl): List<MigrationHomeMessage?> {
        val emissions = mutableListOf<MigrationHomeMessage?>()
        backgroundScope.launch { source.observe().toList(emissions) }
        runCurrent()
        return emissions
    }

    private fun source(account: WalletAccount) =
        MigrationHomeMessageSourceImpl(
            context = mockk(relaxed = true),
            accountDataSource =
                mockk<AccountDataSource> {
                    every { selectedAccount } returns MutableStateFlow(account)
                },
            getOrchardMigrationSdk = mockk(relaxed = true),
            observeMigrationLiveReadout = observeMigrationLiveReadout,
            getOrchardBalance = mockk(relaxed = true),
            walletSnapshotDataSource = mockk(relaxed = true),
            hasSeenMigrationCompleteStorageProvider = mockk(relaxed = true),
            isBackgroundExecutionAvailableProvider = mockk(relaxed = true),
            navigationRouter = mockk(relaxed = true),
            migrationUpgradeNoticeStorageProvider = mockk(relaxed = true),
        )

    private fun zodl() =
        ZodlAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun viewOnly() =
        ViewOnlyAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = 0,
        )
}
