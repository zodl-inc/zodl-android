package co.electriccoin.zcash.ui.common.usecase

import androidx.annotation.StringRes
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.provider.WalletBackupFlagStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupRemindMeCountStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupRemindMeTimestampStorageProvider
import co.electriccoin.zcash.ui.common.repository.ReceiveTransaction
import co.electriccoin.zcash.ui.common.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

interface WalletBackupMessageUseCase {
    fun observe(): Flow<WalletBackupData>
}

class WalletBackupMessageUseCaseImpl(
    private val walletBackupFlagStorageProvider: WalletBackupFlagStorageProvider,
    private val walletBackupRemindMeCountStorageProvider: WalletBackupRemindMeCountStorageProvider,
    private val walletBackupRemindMeTimestampStorageProvider: WalletBackupRemindMeTimestampStorageProvider,
    private val accountDataSource: AccountDataSource,
    private val transactionRepository: TransactionRepository,
) : WalletBackupMessageUseCase {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<WalletBackupData> =
        combine(
            walletBackupFlagStorageProvider.observe(),
            walletBackupRemindMeCountStorageProvider.observe(),
            walletBackupRemindMeTimestampStorageProvider.observe(),
        ) { isBackedUp, count, timestamp ->
            Triple(isBackedUp, count, timestamp)
        }.flatMapLatest { (isBackedUp, count, timestamp) ->
            when {
                isBackedUp -> {
                    flowOf(WalletBackupData.Unavailable)
                }

                timestamp == null -> {
                    flowOf(WalletBackupData.Available(WalletBackupLockoutDuration.TWO_DAYS))
                }

                count == 1 -> {
                    calculateNext(
                        lastTimestamp = timestamp,
                        lastLockoutDuration = WalletBackupLockoutDuration.TWO_DAYS,
                        nextLockoutDuration = WalletBackupLockoutDuration.TWO_WEEKS
                    )
                }

                else -> {
                    calculateNext(
                        lastTimestamp = timestamp,
                        lastLockoutDuration =
                            if (count == 2) {
                                WalletBackupLockoutDuration.TWO_WEEKS
                            } else {
                                WalletBackupLockoutDuration.ONE_MONTH
                            },
                        nextLockoutDuration = WalletBackupLockoutDuration.ONE_MONTH
                    )
                }
            }
        }.flatMapLatest { backup ->
            combine(
                accountDataSource.selectedAccount,
                transactionRepository.transactions,
            ) { account, transactions ->
                if (backup is WalletBackupData.Available &&
                    account is ZodlAccount &&
                    transactions.orEmpty().any { it is ReceiveTransaction }
                ) {
                    backup
                } else {
                    WalletBackupData.Unavailable
                }
            }
        }.distinctUntilChanged()

    private fun calculateNext(
        lastTimestamp: Instant,
        lastLockoutDuration: WalletBackupLockoutDuration,
        nextLockoutDuration: WalletBackupLockoutDuration
    ): Flow<WalletBackupData> {
        val nextAvailableTimestamp = lastTimestamp.plusMillis(lastLockoutDuration.duration.inWholeMilliseconds)
        val now = Instant.now()
        return if (nextAvailableTimestamp > now) {
            flow {
                val remaining = nextAvailableTimestamp.toEpochMilli() - now.toEpochMilli()
                emit(WalletBackupData.Unavailable)
                delay(remaining)
                emit(WalletBackupData.Available(nextLockoutDuration))
            }
        } else {
            flowOf(WalletBackupData.Available(nextLockoutDuration))
        }
    }
}

sealed interface WalletBackupData {
    data class Available(
        val lockoutDuration: WalletBackupLockoutDuration
    ) : WalletBackupData

    data object Unavailable : WalletBackupData
}

enum class WalletBackupLockoutDuration(
    val duration: Duration,
    @field:StringRes val res: Int
) {
    TWO_DAYS(2.days, R.string.general_remind_me_in_two_days),
    TWO_WEEKS(14.days, R.string.general_remind_me_in_two_weeks),
    ONE_MONTH(30.days, R.string.general_remind_me_in_two_months),
}
