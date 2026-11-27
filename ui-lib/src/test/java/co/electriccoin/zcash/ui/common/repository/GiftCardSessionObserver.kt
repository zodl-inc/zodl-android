package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.GiftCardSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlin.test.assertNotNull

/**
 * Every session a collector of [GiftCardRepository.observeSession] has seen, until [job] is cancelled.
 */
internal class GiftCardSessionObserver {
    val values = mutableListOf<GiftCardSession>()
    lateinit var job: Job

    fun latest(): GiftCardSession = assertNotNull(values.lastOrNull())
}

/**
 * Collects [flow] in this test's scope, which counts as an observer of the session.
 */
internal fun TestScope.observe(flow: Flow<GiftCardSession>): GiftCardSessionObserver {
    val observer = GiftCardSessionObserver()
    observer.job = launch { flow.collect { observer.values += it } }
    return observer
}
