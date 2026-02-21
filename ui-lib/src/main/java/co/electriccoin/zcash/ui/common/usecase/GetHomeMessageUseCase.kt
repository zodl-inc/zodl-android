package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MessageAvailabilityDataSource
import co.electriccoin.zcash.ui.common.datasource.WalletSnapshotDataSource
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.model.WalletSnapshot
import co.electriccoin.zcash.ui.common.provider.CrashReportingStorageProvider
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.HomeMessageCacheRepository
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import co.electriccoin.zcash.ui.common.repository.RuntimeMessage
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class GetHomeMessageUseCase(
    private val walletBackupMessageUseCase: WalletBackupMessageUseCase,
    private val crashReportingStorageProvider: CrashReportingStorageProvider,
    private val walletSnapshotDataSource: WalletSnapshotDataSource,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val accountDataSource: AccountDataSource,
    private val messageAvailabilityDataSource: MessageAvailabilityDataSource,
    private val cache: HomeMessageCacheRepository,
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observe(): Flow<HomeMessageData?> =
        channelFlow {
            val messages =
                combine(
                    observeRuntimeMessage(),
                    walletBackupMessageUseCase.observe(),
                    observeIsExchangeRateMessageVisible(),
                    crashReportingStorageProvider.observe().map { it == null },
                ) { runtimeMessage, backup, isCCAvailable, isCrashReportingEnabled ->
                    createMessage(
                        runtimeMessage = runtimeMessage,
                        backup = backup,
                        isCurrencyConversionEnabled = isCCAvailable,
                        isCrashReportingVisible = isCrashReportingEnabled,
                    )
                }

            launch {
                walletSnapshotDataSource
                    .observe()
                    .filterNotNull()
                    .map { it.status }
                    .flatMapLatest {
                        when (it) {
                            Synchronizer.Status.STOPPED,
                            Synchronizer.Status.INITIALIZING -> emptyFlow()
                            else -> messages
                        }
                    }.distinctUntilChanged()
                    .collect { send(it) }
            }

            awaitClose()
        }.debounce(1.seconds)
            .distinctUntilChanged()
            .map { message -> prioritizeMessage(message) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeShieldFundsMessage() =
        accountDataSource.selectedAccount.flatMapLatest { account ->
            when {
                account == null -> flowOf(null)
                account.isShieldingAvailable ->
                    messageAvailabilityDataSource.canShowShieldMessage
                        .map { canShowShieldMessage ->
                            when {
                                !canShowShieldMessage -> null
                                else -> HomeMessageData.ShieldFunds(account.transparent.balance)
                            }
                        }

                else -> flowOf(null)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRuntimeMessage(): Flow<RuntimeMessage?> {
        return channelFlow {
            var firstSyncingMessage: HomeMessageData.Syncing? = null
            combine(
                observeShieldFundsMessage(),
                accountDataSource.selectedAccount,
                walletSnapshotDataSource.observe().filterNotNull()
            ) { availability, account, walletSnapshot ->
                Triple(availability, account, walletSnapshot)
            }.collect { (shieldFundsMessage, account, walletSnapshot) ->
                if (walletSnapshot.status in listOf(Synchronizer.Status.STOPPED, Synchronizer.Status.INITIALIZING)) {
                    return@collect
                }

                val message =
                    createSynchronizerErrorMessage(walletSnapshot)
                        ?: createDisconnectedMessage(walletSnapshot)
                        ?: createSyncingMessage(
                            walletSnapshot,
                            syncMessageShownBefore = firstSyncingMessage != null,
                            someBalance = (account?.spendableShieldedBalance?.value ?: 0) > 0
                        )
                        ?: shieldFundsMessage

                if (message is HomeMessageData.Syncing && firstSyncingMessage == null) {
                    firstSyncingMessage = message
                } else if (message !is HomeMessageData.Syncing) {
                    firstSyncingMessage = null
                }

                send(message)
            }
        }
    }

    private fun observeIsExchangeRateMessageVisible() =
        exchangeRateRepository.state
            .map { it == ExchangeRateState.OptIn }
            .distinctUntilChanged()

    private fun createMessage(
        runtimeMessage: RuntimeMessage?,
        backup: WalletBackupData,
        isCurrencyConversionEnabled: Boolean,
        isCrashReportingVisible: Boolean,
    ) = when {
        runtimeMessage != null -> runtimeMessage
        backup is WalletBackupData.Available -> HomeMessageData.Backup
        isCurrencyConversionEnabled -> HomeMessageData.EnableCurrencyConversion
        isCrashReportingVisible -> HomeMessageData.CrashReport
        else -> null
    }

    private fun prioritizeMessage(message: HomeMessageData?): HomeMessageData? {
        val isSameMessageUpdate = message?.priority == cache.lastMessage?.priority // same but updated
        val someMessageBeenShown = cache.lastShownMessage != null // has any message been shown while app in fg
        val hasNoMessageBeenShownLately = cache.lastMessage == null // has no message been shown
        val isHigherPriorityMessage = (message?.priority ?: 0) > (cache.lastShownMessage?.priority ?: 0)
        val result =
            when {
                message == null -> null
                message is RuntimeMessage -> message
                isSameMessageUpdate -> message
                isHigherPriorityMessage ->
                    if (hasNoMessageBeenShownLately) {
                        if (someMessageBeenShown) null else message
                    } else {
                        message
                    }

                else -> null
            }

        if (result != null) {
            messageAvailabilityDataSource.onMessageShown()
            cache.lastShownMessage = result
        }
        cache.lastMessage = result

        Twig.debug {
            when {
                message == null -> "Home message: no message to show"
                result == null -> "Home message: ${message::class.simpleName} was filtered out"
                else -> "Home message: ${result::class.simpleName} shown"
            }
        }

        return result
    }

    private fun createSynchronizerErrorMessage(walletSnapshot: WalletSnapshot): HomeMessageData.Error? {
        if (walletSnapshot.synchronizerError == null ||
            (
                walletSnapshot.synchronizerError is SynchronizerError.Processor &&
                    walletSnapshot.synchronizerError.cause is CancellationException
            )
        ) {
            return null
        }

        return HomeMessageData.Error(walletSnapshot.synchronizerError)
    }

    private fun createDisconnectedMessage(walletSnapshot: WalletSnapshot): HomeMessageData.Disconnected? =
        if (walletSnapshot.status == Synchronizer.Status.DISCONNECTED) {
            HomeMessageData.Disconnected
        } else {
            null
        }

    @Suppress("MagicNumber")
    private fun createSyncingMessage(
        walletSnapshot: WalletSnapshot,
        syncMessageShownBefore: Boolean,
        someBalance: Boolean,
    ): RuntimeMessage? {
        if (walletSnapshot.status != Synchronizer.Status.SYNCING) return null

        val progress = walletSnapshot.progress.decimal * 100f
        return if (walletSnapshot.restoringState == WalletRestoringState.RESTORING) {
            HomeMessageData.Restoring(walletSnapshot.isSpendable && someBalance, progress)
        } else {
            if (!syncMessageShownBefore) {
                if (progress >= .98f || progress == 0f) null else HomeMessageData.Syncing(progress = progress)
            } else {
                HomeMessageData.Syncing(progress = progress)
            }
        }
    }
}
