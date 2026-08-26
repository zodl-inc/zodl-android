package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.MessageAvailabilityDataSource
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface HomeMessageCacheRepository {
    /**
     * Last message that was shown. Null if no message has been shown yet.
     */
    var lastShownMessage: HomeMessageData?

    /**
     * Last message that was shown. Null if no message has been shown yet or if last message was null.
     */
    var lastMessage: HomeMessageData?

    fun init()

    fun reset()
}

class HomeMessageCacheRepositoryImpl(
    private val messageAvailabilityDataSource: MessageAvailabilityDataSource
) : HomeMessageCacheRepository {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override var lastShownMessage: HomeMessageData? = null
    override var lastMessage: HomeMessageData? = null

    override fun init() {
        messageAvailabilityDataSource
            .canShowMessage
            .onEach { canShowMessage ->
                if (canShowMessage) {
                    lastShownMessage = null
                    lastMessage = null
                }
            }.launchIn(scope)
    }

    override fun reset() {
        lastShownMessage = null
        lastMessage = null
    }
}

@Suppress("MagicNumber")
sealed interface HomeMessageData {
    val priority: Int

    data class Error(
        val synchronizerError: SynchronizerError
    ) : RuntimeMessage()

    data object Disconnected : RuntimeMessage()

    data class Restoring(
        val isSpendable: Boolean,
        val progress: Float
    ) : RuntimeMessage()

    data class Resyncing(
        val progress: Float
    ) : RuntimeMessage()

    data class Syncing(
        val progress: Float
    ) : RuntimeMessage()

    data object Updating : RuntimeMessage()

    data class ShieldFunds(
        val zatoshi: Zatoshi
    ) : RuntimeMessage()

    data object EnableTor : Prioritized {
        override val priority: Int = 3
    }

    data object Backup : Prioritized {
        override val priority: Int = 5
    }

    data object EnableCurrencyConversion : Prioritized {
        override val priority: Int = 2
    }

    data object CrashReport : Prioritized {
        override val priority: Int = 1
    }

    /**
     * Nudge to participate in the active Coinholder Polling (CHP) round (MOB-1805). Ranked below
     * [Backup] but above the other opt-in flows ([EnableTor], [EnableCurrencyConversion],
     * [CrashReport]) — see [co.electriccoin.zcash.ui.common.usecase.GetHomeMessageUseCase]'s
     * `createMessage`. No dismiss affordance in the design, so unlike the other opt-in messages
     * this one has no persisted "seen"/dismissed flag — its producer
     * (`VotingHomeMessageSource.observeIsCoinholderPollingMessageVisible`) is purely data-driven:
     * it disappears on its own once the account has voted, the round ends, or voting is disabled.
     */
    data object CoinholderPolling : Prioritized {
        override val priority: Int = 4
    }
}

/**
 * Message which always is shown.
 */
sealed class RuntimeMessage : HomeMessageData {
    override val priority: Int = Int.MAX_VALUE
}

/**
 * Home-banner payload produced by the feature-migration module. A [RuntimeMessage] subclass so it
 * keeps the always-shown priority the banner had when it lived in ui-lib; abstract (and declared
 * here, in [RuntimeMessage]'s own package, per the sealed-subclassing rule) because the concrete
 * data class lives in the feature module — see MigrationContracts.kt.
 */
abstract class MigrationHomeMessage : RuntimeMessage()

/**
 * Message which always is displayed only if previous message was lower priority.
 */
sealed interface Prioritized : HomeMessageData
