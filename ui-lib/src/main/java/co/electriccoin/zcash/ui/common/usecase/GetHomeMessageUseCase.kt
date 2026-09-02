package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MessageAvailabilityDataSource
import co.electriccoin.zcash.ui.common.datasource.WalletSnapshotDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationHomeMessageSource
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.model.WalletSnapshot
import co.electriccoin.zcash.ui.common.provider.CrashReportingStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.HomeMessageCacheRepository
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessage
import co.electriccoin.zcash.ui.common.repository.RuntimeMessage
import co.electriccoin.zcash.ui.common.voting.VotingHomeMessageSource
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GetHomeMessageUseCase(
    private val walletBackupMessageUseCase: WalletBackupMessageUseCase,
    private val crashReportingStorageProvider: CrashReportingStorageProvider,
    private val walletSnapshotDataSource: WalletSnapshotDataSource,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val accountDataSource: AccountDataSource,
    private val messageAvailabilityDataSource: MessageAvailabilityDataSource,
    private val cache: HomeMessageCacheRepository,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider,
    private val migrationHomeMessageSource: MigrationHomeMessageSource,
    private val votingHomeMessageSource: VotingHomeMessageSource,
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observe(): Flow<HomeMessageData?> =
        channelFlow {
            val messages =
                combine(
                    observeRuntimeMessage(),
                    walletBackupMessageUseCase.observe(),
                    observeIsTorMessageVisible(),
                    observeIsExchangeRateMessageVisible(),
                    combine(
                        votingHomeMessageSource.observeIsCoinholderPollingMessageVisible(),
                        crashReportingStorageProvider.observe().map { it == null },
                    ) { isCoinholderPollingVisible, isCrashReportingVisible ->
                        TrailingPrioritizedInputs(isCoinholderPollingVisible, isCrashReportingVisible)
                    },
                ) { runtimeMessage, backup, isTorAvailable, isCCAvailable, trailing ->
                    createMessage(
                        runtimeMessage = runtimeMessage,
                        backup = backup,
                        isTorVisible = isTorAvailable,
                        isCurrencyConversionEnabled = isCCAvailable,
                        isCoinholderPollingVisible = trailing.isCoinholderPollingVisible,
                        isCrashReportingVisible = trailing.isCrashReportingVisible,
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
            val transparentBalance = account?.transparentBalance
            when {
                account == null || transparentBalance == null -> {
                    flowOf(null)
                }

                account.isShieldingAvailable == true -> {
                    messageAvailabilityDataSource.canShowShieldMessage
                        .map { canShowShieldMessage ->
                            when {
                                !canShowShieldMessage -> null
                                else -> HomeMessageData.ShieldFunds(transparentBalance)
                            }
                        }
                }

                else -> {
                    flowOf(null)
                }
            }
        }

    private data class RuntimeMessageInputs(
        val shieldFunds: HomeMessageData.ShieldFunds?,
        val migration: MigrationHomeMessage?,
        val account: WalletAccount?,
        val walletSnapshot: WalletSnapshot
    )

    /**
     * [HomeMessageData.CoinholderPolling] and [HomeMessageData.CrashReport] bundled together into
     * one flow purely so the outer `observe()` combine (already at kotlinx's 5-flow overload
     * ceiling) doesn't need a 6th argument — same "bundle inputs into a data class" trick as
     * [RuntimeMessageInputs] above.
     */
    private data class TrailingPrioritizedInputs(
        val isCoinholderPollingVisible: Boolean,
        val isCrashReportingVisible: Boolean,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRuntimeMessage(): Flow<RuntimeMessage?> {
        return channelFlow {
            var firstSyncingMessage: HomeMessageData.Syncing? = null
            combine(
                observeShieldFundsMessage(),
                migrationHomeMessageSource.observe(),
                accountDataSource.selectedAccount,
                walletSnapshotDataSource.observe().filterNotNull()
            ) { sf, mig, acc, ws -> RuntimeMessageInputs(sf, mig, acc, ws) }
                .collect { inputs ->
                    val shieldFundsMessage = inputs.shieldFunds
                    val migrationMessage = inputs.migration
                    val account = inputs.account
                    val walletSnapshot = inputs.walletSnapshot

                    if (walletSnapshot.status in
                        listOf(
                            Synchronizer.Status.STOPPED,
                            Synchronizer.Status.INITIALIZING
                        )
                    ) {
                        return@collect
                    }

                    // Priority order: disconnected -> synchronizer error -> syncing -> migration
                    // -> shield funds. Connectivity/sync issues now lead the chain — a user can't
                    // act on the migration banner while the wallet is disconnected, erroring, or
                    // still syncing, so those states take priority over it.
                    val message =
                        createDisconnectedMessage(walletSnapshot)
                            ?: createSynchronizerErrorMessage(walletSnapshot)
                            ?: createSyncingMessage(
                                walletSnapshot,
                                syncMessageShownBefore = firstSyncingMessage != null,
                                someBalance = (account?.spendableShieldedBalance?.value ?: 0) > 0
                            )
                            ?: migrationMessage
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

    private fun observeIsTorMessageVisible() =
        isTorEnabledStorageProvider.observe().map { it == null }.distinctUntilChanged()

    private fun createMessage(
        runtimeMessage: RuntimeMessage?,
        backup: WalletBackupData,
        isTorVisible: Boolean,
        isCurrencyConversionEnabled: Boolean,
        isCoinholderPollingVisible: Boolean,
        isCrashReportingVisible: Boolean,
    ) = when {
        runtimeMessage != null -> runtimeMessage
        backup is WalletBackupData.Available -> HomeMessageData.Backup
        isCoinholderPollingVisible -> HomeMessageData.CoinholderPolling
        isTorVisible -> HomeMessageData.EnableTor
        isCurrencyConversionEnabled -> HomeMessageData.EnableCurrencyConversion
        isCrashReportingVisible -> HomeMessageData.CrashReport
        else -> null
    }

    private fun prioritizeMessage(message: HomeMessageData?): HomeMessageData? {
        val isSameMessageUpdate =
            message?.priority == cache.lastMessage?.priority // same but updated
        val someMessageBeenShown =
            cache.lastShownMessage != null // has any message been shown while app in fg
        val hasNoMessageBeenShownLately = cache.lastMessage == null // has no message been shown
        val isHigherPriorityMessage =
            (message?.priority ?: 0) > (cache.lastShownMessage?.priority ?: 0)
        val result =
            when {
                message == null -> {
                    null
                }

                message is RuntimeMessage -> {
                    message
                }

                isSameMessageUpdate -> {
                    message
                }

                isHigherPriorityMessage -> {
                    if (hasNoMessageBeenShownLately) {
                        if (someMessageBeenShown) null else message
                    } else {
                        message
                    }
                }

                else -> {
                    null
                }
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

    private fun createSyncingMessage(
        walletSnapshot: WalletSnapshot,
        syncMessageShownBefore: Boolean,
        someBalance: Boolean,
    ): RuntimeMessage? = syncingMessageFor(walletSnapshot, syncMessageShownBefore, someBalance)
}

internal const val SYNCING_BANNER_HIDE_BELOW_BLOCKS = 3456L

@Suppress("MagicNumber")
internal fun syncingMessageFor(
    walletSnapshot: WalletSnapshot,
    syncMessageShownBefore: Boolean,
    someBalance: Boolean,
): RuntimeMessage? {
    if (walletSnapshot.status != Synchronizer.Status.SYNCING) return null

    val progress = walletSnapshot.progress.decimal * 100f
    return if (walletSnapshot.restoringState == WalletRestoringState.RESYNCING) {
        HomeMessageData.Resyncing(progress)
    } else if (walletSnapshot.restoringState == WalletRestoringState.RESTORING) {
        HomeMessageData.Restoring(walletSnapshot.isSpendable && someBalance, progress)
    } else {
        if (!syncMessageShownBefore) {
            if (walletSnapshot.blocksRemaining < SYNCING_BANNER_HIDE_BELOW_BLOCKS) {
                null
            } else {
                HomeMessageData.Syncing(progress = progress)
            }
        } else {
            HomeMessageData.Syncing(progress = progress)
        }
    }
}
