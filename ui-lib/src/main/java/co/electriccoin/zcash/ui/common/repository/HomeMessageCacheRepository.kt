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
        override val priority: Int = 4
    }

    data object EnableCurrencyConversion : Prioritized {
        override val priority: Int = 2
    }

    data object CrashReport : Prioritized {
        override val priority: Int = 1
    }
}

/**
 * Message which always is shown.
 */
sealed class RuntimeMessage : HomeMessageData {
    override val priority: Int = Int.MAX_VALUE
}

/**
 * Message which always is displayed only if previous message was lower priority.
 */
sealed interface Prioritized : HomeMessageData
