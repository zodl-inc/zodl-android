package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class UpdateSwapActivityMetadataUseCase(
    accountDataSource: AccountDataSource,
    private val metadataRepository: MetadataRepository,
    private val getSwapStatus: GetSwapStatusUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val pipeline = Channel<Request>()

    private val pipelineCache = linkedSetOf<Request>()

    private val pipelineSemaphore = Mutex()

    private val pipelineCacheSemaphore = Mutex()

    private var pipelineJob: Job? = null

    init {
        accountDataSource.selectedAccount
            .map { it?.sdkAccount?.accountUuid }
            .distinctUntilChanged()
            .onEach {
                killPipeline()
                if (it != null) startPipeline()
            }.launchIn(scope)
    }

    operator fun invoke(activity: ActivityData) {
        scope.launch {
            val depositAddress =
                when (activity) {
                    is ActivityData.BySwap -> {
                        activity.swap.depositAddress
                            .takeIf { !activity.swap.status.isTerminal }
                    }

                    is ActivityData.ByTransaction -> {
                        activity.metadata.swapMetadata
                            ?.depositAddress
                            ?.takeIf { !activity.metadata.swapMetadata.status.isTerminal }
                    }
                }

            if (depositAddress != null) {
                val request =
                    pipelineCacheSemaphore.withLock {
                        val found = pipelineCache.find { it.depositAddress == depositAddress }?.also { it.close() }
                        Request(
                            depositAddress = depositAddress,
                            timestamp = found?.timestamp,
                        )
                    }
                Twig.debug { "Activities: queue $depositAddress" }
                pipeline.send(request)
            }
        }
    }

    private fun killPipeline() {
        pipelineJob?.cancel()
        pipelineJob = null
        pipelineCache.clear()
    }

    @Suppress("MagicNumber")
    private fun startPipeline() {
        pipelineJob =
            scope.launch {
                for (item in pipeline) {
                    pipelineSemaphore.withLock {
                        val depositAddress = item.depositAddress
                        if (item.timestamp != null) {
                            val timeSinceLastAttempt = Clock.System.now() - item.timestamp
                            if (timeSinceLastAttempt < 15.seconds) {
                                Twig.debug { "Activities: delaying for ${15.seconds - timeSinceLastAttempt}" }
                                delay(15.seconds - timeSinceLastAttempt)
                            }
                        }

                        val swapMetadata = metadataRepository.getSwapMetadata(depositAddress)
                        if (swapMetadata != null && !swapMetadata.status.isTerminal) {
                            Twig.debug { "Activities: consuming $depositAddress" }
                            val apiRequestTimestamp = Clock.System.now()
                            val quoteStatus = getSwapStatus(swapMetadata)
                            pipelineCacheSemaphore.withLock { item.close() }
                            if (quoteStatus.status?.status?.isTerminal != true) {
                                when (pipelineCache.lastOrNull()?.depositAddress) {
                                    null,
                                    depositAddress -> {
                                        requeue(depositAddress, apiRequestTimestamp)
                                        Twig.debug { "Activities: delaying 5..15 seconds" }
                                        delay((5..15).random().seconds)
                                    }

                                    else -> {
                                        requeue(depositAddress, apiRequestTimestamp)
                                        Twig.debug { "Activities: delaying 0.5..1.5 seconds" }
                                        delay((500..1500).random().milliseconds)
                                    }
                                }
                            }
                        }
                    }
                    yield()
                }
            }
    }

    private fun requeue(depositAddress: String, timestamp: Instant) {
        scope.launch {
            Twig.debug { "Activities: requeue $depositAddress" }
            pipeline.send(
                pipelineCacheSemaphore.withLock {
                    Request(
                        depositAddress = depositAddress,
                        timestamp = timestamp
                    )
                }
            )
        }
    }

    private inner class Request(
        val depositAddress: String,
        val timestamp: Instant?
    ) : AutoCloseable {
        init {
            val found = pipelineCache.find { it.depositAddress == depositAddress }
            if (found != null) pipelineCache.remove(found)
            pipelineCache.add(this)
        }

        override fun close() {
            pipelineCache.remove(this)
        }
    }
}
