package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.WalletBackupFlagStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupRemindMeCountStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupRemindMeTimestampStorageProvider
import co.electriccoin.zcash.ui.common.repository.ReceiveTransaction
import co.electriccoin.zcash.ui.common.repository.SendTransaction
import co.electriccoin.zcash.ui.common.repository.Transaction
import co.electriccoin.zcash.ui.common.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MOB-1787/MOB-909: the gate behind the home screen's backup prompt. [WalletBackupMessageUseCaseImpl]
 * reports [WalletBackupData.Available] only for a Zashi account whose seed is not backed up, which has
 * already received funds, and whose last "remind me later" lockout has elapsed. Every other combination
 * collapses to [WalletBackupData.Unavailable] - the state [createHomeMessage] reads as "no urgent
 * backup", see [GetHomeMessageUseCaseBackupPriorityTest].
 *
 * The lockout is measured against the real clock via `Instant.now()`, so a still-running lockout is
 * asserted on what the flow has emitted after [runCurrent]; its pending `delay` to the end of the
 * lockout is deliberately left unadvanced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletBackupMessageUseCaseImplTest {
    private val receiveTransaction = mockk<ReceiveTransaction>()
    private val sendTransaction = mockk<SendTransaction>()

    @Test
    fun unbackedSeedWithReceivedFundsAndNoLockoutIsAvailable() =
        runTest {
            val emissions = collectEmissions(useCase(isBackedUp = false, transactions = listOf(receiveTransaction)))

            assertEquals(listOf(WalletBackupData.Available(WalletBackupLockoutDuration.TWO_DAYS)), emissions)
        }

    @Test
    fun noReceiveTransactionKeepsBackupUnavailable() =
        runTest {
            val emissions = collectEmissions(useCase(isBackedUp = false, transactions = listOf(sendTransaction)))

            assertEquals(listOf(WalletBackupData.Unavailable), emissions)
        }

    @Test
    fun alreadyBackedUpSeedKeepsBackupUnavailable() =
        runTest {
            val emissions = collectEmissions(useCase(isBackedUp = true, transactions = listOf(receiveTransaction)))

            assertEquals(listOf(WalletBackupData.Unavailable), emissions)
        }

    @Test
    fun activeRemindMeLaterLockoutKeepsBackupUnavailable() =
        runTest {
            val emissions =
                collectEmissions(
                    useCase(
                        isBackedUp = false,
                        remindMeCount = 1,
                        remindMeTimestamp = Instant.now(),
                        transactions = listOf(receiveTransaction)
                    )
                )

            assertEquals(listOf(WalletBackupData.Unavailable), emissions)
        }

    @Test
    fun elapsedRemindMeLaterLockoutMakesBackupAvailableAgain() =
        runTest {
            val emissions =
                collectEmissions(
                    useCase(
                        isBackedUp = false,
                        remindMeCount = 1,
                        remindMeTimestamp = longAgo(),
                        transactions = listOf(receiveTransaction)
                    )
                )

            assertEquals(listOf(WalletBackupData.Available(WalletBackupLockoutDuration.TWO_WEEKS)), emissions)
        }

    /** Far enough in the past that every [WalletBackupLockoutDuration] lockout has already elapsed. */
    private fun longAgo(): Instant =
        Instant.now().minusMillis(WalletBackupLockoutDuration.ONE_MONTH.duration.inWholeMilliseconds)

    private fun TestScope.collectEmissions(useCase: WalletBackupMessageUseCase): List<WalletBackupData> {
        val emissions = mutableListOf<WalletBackupData>()
        backgroundScope.launch { useCase.observe().toList(emissions) }
        runCurrent()
        return emissions
    }

    private fun useCase(
        isBackedUp: Boolean,
        transactions: List<Transaction>?,
        remindMeCount: Int = 0,
        remindMeTimestamp: Instant? = null,
        account: WalletAccount? = mockk<ZashiAccount>()
    ) = WalletBackupMessageUseCaseImpl(
        walletBackupFlagStorageProvider =
            mockk<WalletBackupFlagStorageProvider> { every { observe() } returns MutableStateFlow(isBackedUp) },
        walletBackupRemindMeCountStorageProvider =
            mockk<WalletBackupRemindMeCountStorageProvider> {
                every { observe() } returns MutableStateFlow(remindMeCount)
            },
        walletBackupRemindMeTimestampStorageProvider =
            mockk<WalletBackupRemindMeTimestampStorageProvider> {
                every { observe() } returns MutableStateFlow(remindMeTimestamp)
            },
        accountDataSource =
            mockk<AccountDataSource> { every { selectedAccount } returns MutableStateFlow(account) },
        transactionRepository =
            mockk<TransactionRepository> { every { this@mockk.transactions } returns MutableStateFlow(transactions) },
    )
}
